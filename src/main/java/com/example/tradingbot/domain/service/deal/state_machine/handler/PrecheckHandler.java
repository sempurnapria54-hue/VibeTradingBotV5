package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.deal.DealEvent;
import com.example.tradingbot.domain.model.trade.market.MarketPhase;
import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStepType;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandFactory;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import com.example.tradingbot.domain.service.strategy.interpreter.StrategyActionInterpreter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrecheckHandler implements StateHandler {

    private final ServiceCommandFactory commandFactory;

    private final StrategyActionInterpreter strategyActionInterpreter;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PRECHECK;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        if (BooleanUtils.isFalse(Objects.equals(deal.getStatus(), Deal.Status.PRECHECK))) {
            throw new IllegalStateException("deal.status must be PRECHECK");
        }

        if (Objects.isNull(deal.getInstrumentId())) {
            throw new IllegalStateException("deal.instrumentId is null");
        }
    }

    @Override
    public TransitionResult handle(DealContext context, DealEvent event) {
        return switch (event) {
            case CHECK_ENTRY_INVARIANTS -> TransitionResult.stay();

            case PROCESS, RETRY -> process(context);

            case CHECK_EXIT_INVARIANTS -> checkExitInvariantsInternal(context);

            case FAIL -> {
                context.getDeal()
                        .setCloseReason(Deal.CloseReason.EMERGENCY_STOP);
                yield TransitionResult.moveTo(Deal.Status.ERROR);
            }

            default -> TransitionResult.stay();
        };
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        if (Objects.equals(result.getNextStatus(), Deal.Status.ENTRY_SUBMITTED) && isPrecheckBlocked(context)) {
            throw new IllegalStateException("Precheck transition to ENTRY_SUBMITTED is not allowed");
        }
    }

    private TransitionResult process(DealContext context) {
        List<ServiceCommand> commands = new ArrayList<>();
        commands.add(commandFactory.system(context, ServiceCommandType.REFRESH_POSITION));
        commands.add(commandFactory.system(context, ServiceCommandType.REFRESH_BALANCE));
        commands.add(commandFactory.system(context, ServiceCommandType.REFRESH_PENDING_ORDERS));
        commands.addAll(strategyActionInterpreter.interpret(context, Set.of(StrategyStepType.ENTRY,
                                                                            StrategyStepType.GRID_ENTRY)));
        return TransitionResult.stay(commands);
    }

    private TransitionResult checkExitInvariantsInternal(DealContext context) {
        if (context.hasEntryOrder() && isPrecheckPassed(context)) {
            return TransitionResult.moveTo(Deal.Status.ENTRY_SUBMITTED);
        }

        if (isPrecheckBlocked(context)) {
            return TransitionResult.moveTo(Deal.Status.CLOSED);
        }

        return TransitionResult.stay();
    }

    private boolean isPrecheckPassed(DealContext context) {
        Strategy strategy = context.getStrategy();
        StrategyDetails strategyDetails = context.getStrategyDetails();
        MarketPhase marketPhase = context.getMarketPhase();

        if (Objects.isNull(strategy) || strategy.isNotActive()) {
            return false;
        }

        if (Objects.isNull(strategyDetails)) {
            return false;
        }

        if (strategyDetails.isTradingDisabled()) {
            return false;
        }

        if (Objects.isNull(marketPhase) || Objects.isNull(marketPhase.getType()) || marketPhase.isUnknown()) {
            return false;
        }

        return BooleanUtils.isFalse(context.hasActivePosition());
    }

    private boolean isPrecheckBlocked(DealContext context) {
        return BooleanUtils.isFalse(isPrecheckPassed(context));
    }
}
