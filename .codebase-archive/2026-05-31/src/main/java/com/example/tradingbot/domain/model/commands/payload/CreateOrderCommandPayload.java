package com.example.tradingbot.domain.model.commands.payload;

import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Стабильный id StrategyAction, по которому создаётся runtime order.
     */
    private Long strategyActionId;

    /**
     * Доменный тип создаваемого order.
     */
    private Order.Type orderType;

    /**
     * Биржевая сторона order: buy или sell.
     */
    private String side;

    /**
     * Цена order. Для market-like сценариев может быть null.
     */
    private BigDecimal price;

    /**
     * Размер order в контрактах для SWAP/FUTURES.
     */
    private BigDecimal size;

    /**
     * Attached protection, создаваемая локально вместе с entry order.
     */
    private List<AttachedAlgoOrder> attachedAlgoOrders;
}
