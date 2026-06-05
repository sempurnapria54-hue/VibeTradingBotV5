package com.example.tradingbot.domain.model.aggregate.strategy.action;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Настройки трейлинг-стопа. Хранятся JSONB-полем на строке
 * strategy_algo_order_action. Ранняя активация трейлинга (без порога
 * прибыли) выбивается шумом — порог активации рекомендуем (СТ-1,
 * чек-лист авторинга). См. docs/models/domain/aggregate/Strategy.md
 * (§TrailingSettings).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrailingSettings {

    /** Порог прибыли активации трейлинга, % (null — активен сразу). */
    private BigDecimal activationProfitPercents;

    /** Callback трейлинга, % (биржевой callback ratio/percent). */
    private BigDecimal callbackPercents;

    /** Буфер активации, %. */
    private BigDecimal activationBufferPercents;
}
