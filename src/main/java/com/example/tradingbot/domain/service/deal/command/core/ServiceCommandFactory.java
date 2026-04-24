package com.example.tradingbot.domain.service.deal.command.core;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyActionType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPositionAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStepType;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ServiceCommandFactory {

    public ServiceCommand system(DealContext context, ServiceCommandType type) {
        return baseBuilder(context, type)
                .build();
    }

    public ServiceCommand strategy(DealContext context,
                                   ServiceCommandType type,
                                   StrategyStepType sourceStepType,
                                   StrategyAction sourceAction,
                                   Long strategyActionId,
                                   ServiceCommandPayload payload) {
        return baseBuilder(context, type)
                .sourceStepType(sourceStepType)
                .sourceActionType(resolveActionType(sourceAction))
                .strategyActionId(strategyActionId)
                .payload(payload)
                .build();
    }

    private ServiceCommand.ServiceCommandBuilder baseBuilder(DealContext context, ServiceCommandType type) {
        Deal deal = Objects.nonNull(context) ? context.getDeal() : null;
        Strategy strategy = Objects.nonNull(context) ? context.getStrategy() : null;
        StrategyDetails strategyDetails = Objects.nonNull(context) ? context.getStrategyDetails() : null;

        return ServiceCommand.builder()
                .type(type)
                .dealId(Objects.nonNull(deal) ? deal.getId() : null)
                .instrumentId(Objects.nonNull(context) ? context.getInstrumentId() : null)
                .strategyId(Objects.nonNull(strategy) ? strategy.getId() : null)
                .strategyDetailsId(Objects.nonNull(strategyDetails) ? strategyDetails.getId() : null)
                .sourceDealStatus(Objects.nonNull(deal) ? deal.getStatus() : null);
    }

    private StrategyActionType resolveActionType(StrategyAction action) {
        if (action instanceof StrategyOrderAction orderAction) {
            return orderAction.getActionType();
        }
        if (action instanceof StrategyAlgoOrderAction algoOrderAction) {
            return algoOrderAction.getActionType();
        }
        if (action instanceof StrategyPositionAction positionAction) {
            return positionAction.getActionType();
        }
        return null;
    }
}
