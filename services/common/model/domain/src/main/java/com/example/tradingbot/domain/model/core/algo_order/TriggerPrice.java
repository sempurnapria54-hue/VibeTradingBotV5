package com.example.tradingbot.domain.model.core.algo_order;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Триггерная цена ноги algo-order: внутренний тип/значение и биржевые
 * type/value (могут отличаться округлением). Раздел модели AlgoOrder
 * (model-granularity). См. docs/models/domain/core/AlgoOrder.md
 * (§«Условие срабатывания»).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TriggerPrice {

    /** Внутренний тип trigger-цены. */
    private AlgoOrder.TriggerPriceType type;

    /** Внутреннее значение цены. */
    private BigDecimal value;

    /**
     * Эхо ценовой базы, которую площадка применила, — в словаре домена:
     * литерал площадки переводит коннектор на своей границе, и значение вне
     * перечня приезжает пустым. Несёт его только прочитанная копия: на строку
     * эхо не переносится, оно операнд сверки объявленной базы {@link #type}
     * (docs/models/mapping/AlgoOrder.md §«Сверка эха»).
     */
    private AlgoOrder.TriggerPriceType externalType;

    /** Биржевое значение (может отличаться округлением). */
    private BigDecimal externalValue;
}
