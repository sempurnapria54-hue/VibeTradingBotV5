package com.example.tradingbot.domain.model.commands;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.trade.strategy.StrategyActionType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStepType;
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
public class ServiceCommand {

    private ServiceCommandType type;

    private Long dealId;

    private Long instrumentId;

    private Long strategyId;

    private Long strategyDetailsId;

    private Deal.Status sourceDealStatus;

    private StrategyStepType sourceStepType;

    private StrategyActionType sourceActionType;

    private Long strategyActionId;

    private ServiceCommandPayload payload;
}
