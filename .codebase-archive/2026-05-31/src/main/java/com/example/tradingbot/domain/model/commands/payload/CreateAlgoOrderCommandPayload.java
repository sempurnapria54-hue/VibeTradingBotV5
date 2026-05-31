package com.example.tradingbot.domain.model.commands.payload;

import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.ConditionType;
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
public class CreateAlgoOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Стабильный id StrategyAction, по которому создаётся runtime algo-order.
     */
    private Long strategyActionId;

    /**
     * Доменный тип условия algo-order.
     */
    private ConditionType conditionType;

    /**
     * Размер algo-order в контрактах для SWAP/FUTURES.
     */
    private BigDecimal size;

    /**
     * Доменная сторона algo-order.
     */
    private AlgoOrder.Direction direction;

    /**
     * Биржевой тип algo-order для OKX ordType.
     */
    private String externalType;

    /**
     * Биржевая сторона algo-order: buy или sell.
     */
    private String externalDirection;

    /**
     * Условие с trigger/take-profit/stop-loss/trailing параметрами.
     */
    private Condition condition;
}
