package com.example.tradingbot.domain.model.commands.payload;

import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelAlgoOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Локальный id algo-order, который нужно отменить.
     */
    private Long algoOrderId;

    /**
     * StrategyAction id для fallback-поиска algo-order после рестарта.
     */
    private Long strategyActionId;
}
