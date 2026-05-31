package com.example.tradingbot.domain.model.commands.payload;

import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmendAlgoOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Локальный id algo-order, который нужно изменить.
     */
    private Long algoOrderId;

    /**
     * StrategyAction id для fallback-поиска algo-order после рестарта.
     */
    private Long strategyActionId;

    /**
     * Новый размер algo-order в контрактах, если стратегия его изменила.
     */
    private BigDecimal size;

    /**
     * Новое условие algo-order, если стратегия изменила trigger/SL/TP/trailing параметры.
     */
    private Condition condition;
}
