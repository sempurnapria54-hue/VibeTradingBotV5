package com.example.strategies.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.strategies.util.Constants;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Отвечает на вопрос «кто инициировал ход» — один поставщик на обоих
 * читателей: колонки аудита строки и поле актора в содержимом события
 * (docs/models/domain/other/Auditable.md §«Носитель дискриминатора —
 * контекст хода, а не поле модели»).
 *
 * <p><b>Читателей двое, а значение одно</b>, поэтому и бин один: второй
 * копии правила «принципал либо контур» ВНУТРИ СЕРВИСА не заводится — она
 * разошлась бы с первой первой же правкой
 * (.claude/rules/policy-home.md).
 *
 * <p><b>Со-расположение с поставщиками соседей названо, а не умолчано.</b>
 * Тот же предикат «принципал либо контур» написан у {@code trading-core} и
 * {@code audit-statistics}:
 * дом у ПРАВИЛА один (docs/models/domain/other/Auditable.md §«Область значений
 * актора»), а общего носителя у РЕАЛИЗАЦИИ нет — она читает контекст
 * Spring Security, и вынос её в общий артефакт сделал бы эту зависимость
 * обязательной для всякого сервиса, который артефакт собирает
 * (.claude/rules/codestyle.md §«Новый модуль монорепозитория»). Цена
 * названа: расхождение копий, в отличие от расхождения литерала, не
 * наблюдаемо — значение остаётся правдоподобным. Оживитель — задача о
 * едином носителе предиката (.claude/work/backlog.md §«Предикат актора —
 * три реализации при одном доме правила»).
 *
 * <p><b>Разделяет ПРЕДЪЯВЛЕННЫЙ принципал, а не тред записи и не место
 * исполнения.</b> Классов значений два — имя принципала и класс
 * собственного прохода, — и различение по треду ломается на первом же
 * асинхронном фасаде: ручной триггер джобы порождён человеком, а работа
 * идёт в чужом треде. Перенос контекста в порождённый тред — часть тропы,
 * а не деталь реализации ({@code AsyncActorContextConfigurer}).
 *
 * <p><b>Пусто в контексте означает «внешнего инициатора нет»</b> — это
 * значение <b>по проверенному признаку</b>, а не умолчание.
 *
 * <p><b>Анонимная аутентификация значением не является.</b> Контур отдаёт
 * её на открытых точках (проба живости, съём метрик); писать её именем
 * актора значило бы утверждать, что ход начал субъект, которого контур не
 * удостоверил.
 *
 * <p><b>Контур доступа — источник значения, а не слой предмета.</b>
 * Величина доменная (актор перехода едет содержимым события), поэтому и
 * поставщик её живёт здесь, а не у границы: у границы он был бы вторым
 * носителем одного и того же ответа.
 */
@Component
public class ActorProvider {

    /**
     * Актор текущего хода: имя предъявленного принципала либо класс
     * собственного прохода контура.
     */
    public String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isNull(authentication)
                || isFalse(authentication.isAuthenticated())
                || authentication instanceof AnonymousAuthenticationToken) {
            return Constants.Audit.SYSTEM_PRINCIPAL;
        }
        return authentication.getName();
    }
}
