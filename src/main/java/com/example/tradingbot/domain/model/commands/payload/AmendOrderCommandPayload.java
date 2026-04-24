package com.example.tradingbot.domain.model.commands.payload;

import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
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
public class AmendOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Локальный id order, который нужно изменить.
     */
    private Long orderId;

    /**
     * StrategyAction id для fallback-поиска order после рестарта.
     */
    private Long strategyActionId;

    /**
     * Новая цена order, если стратегия её изменила.
     */
    private BigDecimal price;

    /**
     * Новый размер order в контрактах, если стратегия его изменила.
     */
    private BigDecimal size;
}
