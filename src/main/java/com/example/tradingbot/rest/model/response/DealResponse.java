package com.example.tradingbot.rest.model.response;

import com.example.tradingbot.rest.model.response.algo_order.AlgoOrder;
import com.example.tradingbot.rest.model.response.order.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DealResponse extends Auditable {

    private String internalId;
    private Long instrumentId;
    private String status;
    private String closeReason;
    private BigDecimal resultProfit;
    private List<Order> orderEntities;
    private List<AlgoOrder> algoOrderEntities;
    private List<PositionResponse> positionEntities;
}
