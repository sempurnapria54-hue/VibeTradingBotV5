package com.example.tradingbot.domain.model.trade.market_structure;

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
