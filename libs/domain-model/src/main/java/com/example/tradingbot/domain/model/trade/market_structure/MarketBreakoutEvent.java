package com.example.tradingbot.domain.model.trade.market_structure;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Предвычисленное событие подтверждённого пробоя структурного уровня:
 * сломанный уровень + направление + цена закрытия пробоя + confirmedAt.
 * Резолвер детектит пробой (буфер breakoutBufferPercents + удержание
 * breakoutConfirmationBars из MarketStructureParams) и экспонирует это
 * событие, которое условие RANGE_BREAKOUT_CONFIRMED читает готовым.
 * Форма — деталь реализации (CODE). См.
 * docs/models/domain/other/MarketStructure.md (§Семантика классификации, п.5),
 * docs/components/MarketStructureResolver.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketBreakoutEvent {

    /** Тип сломанного уровня (RANGE_HIGH/RANGE_LOW/RESISTANCE/SUPPORT). */
    private MarketPriceLevel.Type brokenLevelType;

    /** Направление пробоя. */
    private Direction direction;

    /** Цена сломанного уровня. */
    private BigDecimal levelPrice;

    /** Свеча, на которой пробой подтверждён (удержание баров завершилось). */
    private OffsetDateTime confirmedAt;

    /** Направление подтверждённого пробоя. */
    public enum Direction {

        /** Пробой вверх (закрытие выше сопротивления / верхней границы). */
        UP,

        /** Пробой вниз (закрытие ниже поддержки / нижней границы). */
        DOWN
    }
}
