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

    private Long strategyActionId;

    private Order.Type orderType;

    private String side;

    private BigDecimal price;

    private BigDecimal size;

    private List<AttachedAlgoOrder> attachedAlgoOrders;
}
