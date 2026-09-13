package com.example.audit.domain.service;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import com.example.audit.domain.model.AccessDenial;
import com.example.audit.persistence.service.AccessDenialDataService;
import com.example.platform.security.AccessDenialRecorder;
import com.example.tradingbot.domain.util.InternalIdFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Писатель журнальной строки отвергнутого по правам вызова
 * (docs/models/domain/other/AccessDenial.md). Зовут его обе точки входа
 * отказа фильтр-цепочки — <b>до</b> сборки ответа вызывающему.
 *
 * <p><b>Отказ записи не превращается в доступ.</b> Не удалось завести
 * строку — вызов всё равно отвергается: сбой записи уходит в лог (первый
 * уровень внутренней градации, docs/rules/error-handling-policy.md), а
 * ответ остаётся отказом. Обратный порядок — «не смогли записать, значит
 * пропускаем» — был бы ошибкой в разрешающую сторону (docs/concept.md П1,
 * следствие 3).
 *
 * <p><b>Почему сбой уходит именно в лог, при том что лог носителем
 * наблюдаемости не является.</b> Носитель здесь и есть та запись, которая
 * не удалась; второго персистентного носителя под сбой первого не
 * заводится — он упирался бы в ту же недоступную базу. Отказ при этом
 * громкий, а не тихий.
 *
 * <p><b>Порт периметра реализует он сам</b> ({@link AccessDenialRecorder}):
 * точки входа отказа лежат в общем артефакте и перечня исходов не
 * видят: доменные перечни объявляются только в доменном слое
 * (.claude/rules/codestyle.md §«Слои моделей и enum'ы»), а метод на класс
 * отказа эту границу сохраняет.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessDenialService implements AccessDenialRecorder {

    /**
     * Потолок ширины колонки поверхности.
     *
     * <p>Значение <b>контролируется вызывающим, который себя не
     * предъявил</b>: длинный путь — это его выбор, а не наш факт. Без
     * усечения такой вызов ронял бы вставку на ограничении колонки, то
     * есть отказ доступа стирал бы собственный след — и тем надёжнее, чем
     * длиннее путь.
     */
    private static final int SURFACE_MAX_LENGTH = 256;

    private final AccessDenialDataService dataService;

    /**
     * Принципал не предъявлен либо предъявленный не принят.
     *
     * <p>Имя не передаётся намеренно: заявленное, но не
     * удостоверенное в строку не пишется — это была бы запись
     * непроверенного как факта.
     */
    @Override
    public void recordPrincipalAbsent(String surface) {
        record(surface, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);
    }

    /** Принципал принят, но операция ему не разрешена. */
    @Override
    public void recordOperationForbidden(String surface, String principal) {
        record(surface, AccessDenial.Outcome.OPERATION_FORBIDDEN, principal);
    }

    /**
     * Завести строку отказа.
     *
     * @param surface   куда стучались: метод и путь
     * @param outcome   класс отказа
     * @param principal <b>принятый</b> принципал либо пусто; заявленное,
     *                  но не удостоверенное имя сюда не передаётся
     */
    public void record(String surface, AccessDenial.Outcome outcome, String principal) {
        AccessDenial denial = new AccessDenial();
        denial.setInternalId(InternalIdFactory.forInternalEntity());
        denial.setSurface(abbreviate(surface, SURFACE_MAX_LENGTH));
        denial.setOutcome(outcome);
        denial.setPrincipal(principal);

        if (isFalse(denial.isConsistent())) {
            // Дефект писателя, а не вызывающего: класс отказа и принципал
            // выражают одно состояние дважды и разойтись не вправе.
            log.error("Access denial row is inconsistent: outcome={}, principal present={}",
                    outcome, nonNull(principal));
        }
        try {
            dataService.save(denial);
        } catch (RuntimeException failure) {
            log.error("Access denial row not persisted: surface={}, outcome={}, cause={}",
                    denial.getSurface(), outcome, failure.getMessage(), failure);
        }
    }
}
