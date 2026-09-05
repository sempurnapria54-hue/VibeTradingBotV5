package com.example.connector.okx.resolve;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Опознаёт отказ источника в наших кредах по коду ответа
 * (docs/integrations/okx/rules/auth-rejection-codes.md — дом перечня;
 * перечень добыт прогоном контура, кейс {@code SEC1.1}, а не выведен).
 *
 * <p><b>Опознание по коду, а не по HTTP-статусу.</b> У всего семейства статус
 * один — {@code 401}, и по нему наш собственный дефект сборки запроса
 * неотличим от отвергнутых кредов. Реакции у них при этом противоположные:
 * первое лечится нашей стороной, второе повтором не лечится вовсе и поднимает
 * биржевую ступень 2.
 *
 * <p><b>Перечень закрыт над наблюдёнными формами</b>, а не над всем семейством
 * {@code 501xx}: код вне перечня отказом кредов не считается и идёт общей
 * тропой отказа границы — направление консервативное.
 */
@Component
public class OkxCredentialsRejectionResolver {

    /**
     * Коды отказа в кредах. Каждый наблюдён прогоном:
     * {@code 50101} — ключ не того контура; {@code 50105} — passphrase не та;
     * {@code 50111} — ключ неверной формы; {@code 50113} — подпись не принята;
     * {@code 50119} — ключа у источника нет (отозван либо чужой).
     */
    private static final Set<String> CREDENTIALS_REJECTED_CODES =
            Set.of("50101", "50105", "50111", "50113", "50119");

    /**
     * Коды, которые выглядят так же (тот же {@code 401}, то же семейство), но
     * означают <b>наш</b> дефект сборки запроса: {@code 50102} — часы разошлись
     * с источником, {@code 50103} — заголовок ключа не поставлен. Держатся
     * отдельным перечнем, а не «всё остальное»: молчание о них читалось бы как
     * «таких ответов не бывает», и первый же из них уехал бы в отказ кредов.
     */
    private static final Set<String> OWN_REQUEST_DEFECT_CODES = Set.of("50102", "50103");

    /** Отверг ли источник наши креды этим кодом. */
    public Boolean isCredentialsRejected(String code) {
        return contains(CREDENTIALS_REJECTED_CODES, code);
    }

    /**
     * Не наш ли это собственный дефект сборки запроса. Отдельный вопрос, а не
     * отрицание первого: третий класс — «прочий отказ источника» — существует
     * и ведёт своей тропой.
     */
    public Boolean isOwnRequestDefect(String code) {
        return contains(OWN_REQUEST_DEFECT_CODES, code);
    }

    /**
     * Пустой код — «не этот класс», а не отказ вычисления. Ответ источника без
     * поля {@code code} достижим (искажённое тело, ответ прокси), и падать на
     * нём резолвер не вправе: тогда сломанный ответ уводил бы тропу в
     * непредвиденную ошибку вместо штатного отказа границы.
     */
    private Boolean contains(Set<String> codes, String code) {
        return isNotBlank(code) && codes.contains(code);
    }
}
