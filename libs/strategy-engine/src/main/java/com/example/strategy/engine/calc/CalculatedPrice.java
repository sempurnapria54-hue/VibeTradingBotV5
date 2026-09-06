package com.example.strategy.engine.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Рассчитанные цены действия — результат {@link PriceCalculator}
 * (docs/components/models/CalculatedPrice.md). Неизменяемый
 * runtime-объект прохода, не хранится. Формулы живут у калькулятора;
 * здесь только структура.
 *
 * <p>Защитные разделы заполняются, только если действие создаёт или
 * замещает соответствующую защиту.
 */
@Value
@Builder
public class CalculatedPrice {

    /** Назначение рассчитанной цены. */
    StrategyPricePurpose purpose;

    /** Режим цены. */
    PriceMode priceMode;

    /** База, от которой считали. */
    BigDecimal basePrice;

    /** Цена до округления. */
    BigDecimal rawPrice;

    /** Цена после округления по шагу цены. */
    BigDecimal roundedPrice;

    /** Нужно ли отправлять цену на биржу. */
    Boolean sendPriceToExchange;

    /** Раздел уровня остановки убытка. */
    ResolvedStopLossPrice stopLossPrice;

    /** Раздел уровня фиксации прибыли. */
    ResolvedTakeProfitPrice takeProfitPrice;

    /** Раздел параметров трейлинга. */
    ResolvedTrailingPrice trailingPrice;

    /** Пояснение расчёта для логов и аудита. */
    String description;
}
