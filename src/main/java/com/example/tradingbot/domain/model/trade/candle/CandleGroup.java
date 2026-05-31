package com.example.tradingbot.domain.model.trade.candle;

import com.example.tradingbot.domain.model.Auditable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Группа свечей одного инструмента и одного таймфрейма — единица
 * загрузки/докачки/проверки целостности свечной истории. Несёт
 * фактические границы загруженной истории и поддерживаемый count;
 * плановый горизонт «до куда грузить» — на инструменте
 * (Instrument.plannedCandleStartDate). См.
 * docs/models/domain/other/CandleGroup.md,
 * docs/lifecycles/CandleGroup.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroup extends Auditable {

    /** Внутренний идентификатор группы. */
    private Long id;

    /** Инструмент-владелец (Instrument.id). */
    private Long instrumentId;

    /** Канонический таймфрейм группы. */
    private TimeFrame timeframe;

    /** Таймфрейм в формате биржи (сырой, например 1H). */
    private String externalTimeframe;

    /** Статус жизненного цикла загрузки свечей. */
    private Status status;

    /** Время открытия первой фактически загруженной свечи (UTC мс). */
    private Long actualFirstUtcMillis;

    /** Время открытия последней фактически загруженной свечи (UTC мс). */
    private Long actualLastUtcMillis;

    /** Поддерживаемое число свечей в группе (основа проверки целостности). */
    private Long count;

    /**
     * Ожидаемое по density-инварианту число свечей на фактических
     * границах [actualFirst, actualLast]:
     * {@code (actualLast - actualFirst) / step + 1}. Для пустой
     * группы (границы не заданы) — 0.
     */
    public long expectedCount() {
        if (Objects.isNull(actualFirstUtcMillis) || Objects.isNull(actualLastUtcMillis)) {
            return 0L;
        }
        long step = timeframe.getDurationMillis();
        return (actualLastUtcMillis - actualFirstUtcMillis) / step + 1L;
    }

    /**
     * Ряд плотен на [actualFirst, actualLast]: поддерживаемый count
     * совпадает с ожидаемым по density-инварианту. Дотягивание нижней
     * границы до планового горизонта — забота BACKFILL, не плотности.
     */
    public boolean isDense() {
        long expected = expectedCount();
        long actual = Objects.isNull(count) ? 0L : count;
        return actual == expected;
    }

    /** Группа готова (покрытие подтверждено, дыр нет). */
    public boolean isActive() {
        return Objects.equals(status, Status.ACTIVE);
    }

    /** Статус жизненного цикла загрузки свечей группы. */
    public enum Status {
        CREATED, BACKFILL, SYNC, CHECK, REPAIR, ACTIVE, ERROR, DELETED
    }
}
