package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandFactory;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProtectionSwitchedHandler implements StateHandler {

    private final ServiceCommandFactory commandFactory;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PROTECTION_SWITCHED;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        if (BooleanUtils.isFalse(Objects.equals(deal.getStatus(), Deal.Status.PROTECTION_SWITCHED))) {
            throw new IllegalStateException("deal.status must be PROTECTION_SWITCHED");
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
                        .setCloseReason(Deal.CloseReason.PROTECTION_FAILED);
                yield TransitionResult.moveTo(Deal.Status.ERROR);
            }
            default -> TransitionResult.stay();
        };
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        if (Objects.equals(result.getNextStatus(), Deal.Status.MANAGING)
                && BooleanUtils.isFalse(context.isReadyForManaging())) {
            throw new IllegalStateException("transition to MANAGING requires ready-for-managing context");
        }
    }

    private TransitionResult process(DealContext context) {
        List<ServiceCommand> commands = List.of(
                commandFactory.system(context, ServiceCommandType.REFRESH_POSITION),
                commandFactory.system(context, ServiceCommandType.REFRESH_ORDER),
                commandFactory.system(context, ServiceCommandType.REFRESH_ALGO_ORDERS)
        );
        return TransitionResult.stay(commands);
    }

    private TransitionResult checkExitInvariantsInternal(DealContext context) {
        if (context.isPositionClosed()) {
            return TransitionResult.moveTo(Deal.Status.EXIT_PENDING);
        }

        if (context.isReadyForManaging()) {
            return TransitionResult.moveTo(Deal.Status.MANAGING);
        }

        return TransitionResult.stay();
    }
}
