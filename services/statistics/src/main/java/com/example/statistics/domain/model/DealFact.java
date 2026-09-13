package com.example.statistics.domain.model;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Сделочный факт — строка-предшественник строки сделочного агрегата
 * (docs/models/domain/other/StatisticsFact.md §«Сделочный факт»).
 *
 * <p><b>Зерно у факта — одно принятое событие, а не сутки.</b> Агрегат
 * получается из фактов свёрткой по суткам; фактов из агрегата не
 * получается, и потому факт — единственный вход пересчёта.
 *
 * <p><b>Хранятся только ОПЕРАНДЫ, а не выводы из них.</b> Ценовой
 * результат, класс результата и R выводятся при свёртке и вторым
 * носителем не заводятся (docs/concept.md, П4).
 *
 * <p><b>Неизменяем по построению:</b> событие произошло однажды.
 */
@Value
@Builder
public class DealFact {

    /** Идентичность принятого события; она же отметка обработанного. */
    String eventId;

    /** Тенант-владелец: ключ зерна и радиус отбора. */
    String tenantId;

    /** Биржевой счёт: ключ зерна. */
    String exchangeAccountInternalId;

    /** Определение стратегии: ключ зерна; пусто — «сделка стратегии не имеет». */
    String strategyInternalId;

    /** Расчётная валюта результата: ключ зерна; пусто — «валюта не резолвилась». */
    String resultCurrency;

    /**
     * Момент терминала сделки. <b>Ось времени таблицы</b>: по ней же
     * считаются сутки зерна при свёртке.
     */
    OffsetDateTime closedAt;

    /** Сделка приняла риск: популяция долей — она, а не все закрытые. */
    Boolean tookRisk;

    /** Граф сделки полон: конъюнкт доступности ценового результата. */
    Boolean graphComplete;

    /** Чистый результат сделки. */
    BigDecimal netResult;

    /** Комиссия. */
    BigDecimal fee;

    /** Финансирование. */
    BigDecimal funding;

    /** Штраф ликвидации. */
    BigDecimal liquidationPenalty;

    /** Плановый риск: знаменатель R. */
    BigDecimal plannedRisk;

    /** Исход закрытия. */
    String closeOutcome;

    /** Состояние сверки P&amp;L. */
    String reconciliationStatus;

    /** Полнота разбивки движений средств. */
    String breakdownIncomplete;

    /** Доступность базы риска. */
    String riskBenchmarkAvailability;

    /**
     * Вход полон: присутствуют ключ дедупа, оба обязательных ключа зерна и
     * ось времени.
     *
     * <p><b>Перечень равен множеству колонок {@code not null} схемы за
     * вычетом тех, которые производит сама сторона приёма</b> — ключ
     * присваивает база. Операнды мер сюда не входят: их пустота есть
     * <b>значение</b>, разбираемое свёрткой (недоступный результат, не
     * резолвившаяся валюта), а не пробел входа
     * (docs/rules/absent-value-semantics.md).
     */
    public Boolean hasCompleteInput() {
        return nonNull(eventId)
                && nonNull(tenantId)
                && nonNull(exchangeAccountInternalId)
                && nonNull(closedAt);
    }
}
