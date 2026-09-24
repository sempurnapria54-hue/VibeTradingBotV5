package com.example.tradingbot.domain.model.trade.market_structure;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Конкретный ценовой уровень внутри MarketStructure (без родителя смысла
 * не имеет → раздел структуры, не отдельная модель). Эти же значения
 * Type используются strategy-layer для StrategyPriceBaseType /
 * StrategyPricePlacement. См.
 * docs/models/domain/other/MarketStructure.md (§MarketPriceLevel).
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketPriceLevel extends Auditable {

    /** Технический ID уровня. */
    private Long id;

    /** Тип уровня. */
    private Type type;

    /** Цена уровня. */
    private BigDecimal price;

    /** Свеча, на которой уровень найден. */
    private OffsetDateTime detectedAt;

    /** Свеча, на которой уровень подтверждён. */
    private OffsetDateTime confirmedAt;

    /**
     * Стоящий в перечне позже уровень того же типа вытесняет этот как
     * «последний подтверждённый». Известный момент подтверждения бьёт
     * неизвестный в обе стороны; при равных моментах либо двух неизвестных
     * вытесняет стоящий позже. Так выбор не зависит от порядка перечня,
     * пока моменты известны.
     */
    public Boolean isSupersededBy(MarketPriceLevel later) {
        if (isNull(later)) {
            return false;
        }
        if (isNull(confirmedAt)) {
            return true;
        }
        return nonNull(later.getConfirmedAt()) && isFalse(later.getConfirmedAt().isBefore(confirmedAt));
    }

    /** Тип ценового уровня структуры. */
    public enum Type {

        /** Нижняя граница диапазона. */
        RANGE_LOW,

        /** Верхняя граница диапазона. */
        RANGE_HIGH,

        /** Свинг-минимум (локальный минимум). */
        SWING_LOW,

        /** Свинг-максимум (локальный максимум). */
        SWING_HIGH,

        /** Уровень поддержки (подтверждённый пол ниже цены). */
        SUPPORT,

        /** Уровень сопротивления (подтверждённый потолок выше цены). */
        RESISTANCE
    }
}
