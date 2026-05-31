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
public class SubmitOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Локальный id order, который уже создан в БД.
     */
    private Long orderId;

    /**
     * StrategyAction id для fallback-поиска order после рестарта.
     */
    private Long strategyActionId;
}
