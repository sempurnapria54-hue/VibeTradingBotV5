package com.example.tradingbot.domain.command.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Рассчитанная цена (или набор цен) для действия — результат
 * PriceCalculator. RVO, не persisted. Формулы и таблица «источник цены →
 * тип цены» — у PriceCalculator; здесь только структура данных. Защитные
 * под-объекты (SL/TP/trailing) заполняются, только если действие
 * создаёт/замещает соответствующую защиту. См.
 * docs/components/models/CalculatedPrice.md.
 */
@Value
@Builder
public class CalculatedPrice {

    /** Назначение рассчитанной цены. */
    StrategyPricePurpose purpose;

    /** Режим цены (нужно ли слать на биржу). */
    PriceMode priceMode;

    /** Базовая цена, от которой считали. */
    BigDecimal basePrice;

    /** Сырая цена до округления. */
    BigDecimal rawPrice;

    /** Цена после округления по tick size. */
    BigDecimal roundedPrice;

    /** Нужно ли отправлять цену на биржу. */
    Boolean sendPriceToExchange;

    /** SL-компонент, если action создаёт/замещает stop-loss. */
    ResolvedStopLossPrice stopLossPrice;

    /** TP-компонент, если action создаёт/замещает take-profit. */
    ResolvedTakeProfitPrice takeProfitPrice;

    /** Trailing-компонент, если action создаёт/замещает trailing stop. */
    ResolvedTrailingPrice trailingPrice;

    /** Пояснение расчёта для логов/аудита. */
    String description;
}
