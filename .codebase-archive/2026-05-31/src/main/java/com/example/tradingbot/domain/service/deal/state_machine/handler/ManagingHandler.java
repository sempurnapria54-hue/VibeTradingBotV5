package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.deal.DealEvent;
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
public class ManagingHandler implements StateHandler {

    private final ServiceCommandFactory commandFactory;

    private final StrategyActionInterpreter strategyActionInterpreter;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.MANAGING;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        if (BooleanUtils.isFalse(Objects.equals(deal.getStatus(), Deal.Status.MANAGING))) {
            throw new IllegalStateException("deal.status must be MANAGING");
        }

        if (BooleanUtils.isFalse(context.hasActivePosition())) {
            throw new IllegalStateException("active position is required");
        }

        if (BooleanUtils.isFalse(context.hasMainProtection())) {
            throw new IllegalStateException("main protection is required");
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
        if (Objects.equals(result.getNextStatus(), Deal.Status.EXIT_PENDING)
                && BooleanUtils.isFalse(context.isPositionClosed())) {
            throw new IllegalStateException("transition to EXIT_PENDING requires closed position");
        }
    }

    private TransitionResult process(DealContext context) {
        List<ServiceCommand> commands = new ArrayList<>();
        commands.add(commandFactory.system(context, ServiceCommandType.REFRESH_POSITION));
        commands.add(commandFactory.system(context, ServiceCommandType.REFRESH_ALGO_ORDERS));
        commands.addAll(strategyActionInterpreter.interpret(context, Set.of(StrategyStepType.PROTECTION_ADJUSTMENT,
                                                                            StrategyStepType.PARTIAL_EXIT,
                                                                            StrategyStepType.GRID_MANAGEMENT,
                                                                            StrategyStepType.EXIT,
                                                                            StrategyStepType.FAIL_SAFE)));
        return TransitionResult.stay(commands);
    }

    private TransitionResult checkExitInvariantsInternal(DealContext context) {
        if (context.isPositionClosed()) {
            return TransitionResult.moveTo(Deal.Status.EXIT_PENDING);
        }

        if (context.hasActivePosition() && BooleanUtils.isFalse(context.hasMainProtection())) {
            context.getDeal()
                    .setCloseReason(Deal.CloseReason.PROTECTION_FAILED);
            return TransitionResult.moveTo(Deal.Status.ERROR);
        }

        return TransitionResult.stay();
    }
}
