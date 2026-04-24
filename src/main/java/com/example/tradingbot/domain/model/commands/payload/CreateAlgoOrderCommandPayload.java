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

    private Long strategyActionId;

    private ConditionType conditionType;

    private BigDecimal size;

    private AlgoOrder.Direction direction;

    private String externalType;

    private String externalDirection;

    private Condition condition;
}
