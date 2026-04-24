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

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Service
@RequiredArgsConstructor
public class ExitPendingHandler implements StateHandler {

    private final ServiceCommandFactory commandFactory;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.EXIT_PENDING;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        if (BooleanUtils.isFalse(Objects.equals(deal.getStatus(), Deal.Status.EXIT_PENDING))) {
            throw new IllegalStateException("deal.status must be EXIT_PENDING");
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
        if (Objects.equals(result.getNextStatus(), Deal.Status.CLOSED)
                && BooleanUtils.isFalse(context.isReadyForExitPending())) {
            throw new IllegalStateException("transition to CLOSED requires finalized exit context");
        }
    }

    private TransitionResult process(DealContext context) {
        List<ServiceCommand> commands = List.of(
                commandFactory.system(context, ServiceCommandType.REFRESH_POSITION),
                commandFactory.system(context, ServiceCommandType.REFRESH_ORDER_HISTORY),
                commandFactory.system(context, ServiceCommandType.REFRESH_ALGO_ORDER_HISTORY),
                commandFactory.system(context, ServiceCommandType.REFRESH_FILLS),
                commandFactory.system(context, ServiceCommandType.FINALIZE_DEAL_EXIT)
        );
        return TransitionResult.stay(commands);
    }

    private TransitionResult checkExitInvariantsInternal(DealContext context) {
        boolean closeReasonDefined = Objects.nonNull(context.getDeal().getCloseReason());
        boolean noPosition = context.isReadyForExitPending();
        boolean noActiveAlgos = emptyIfNull(context.getActiveAlgoOrders()).isEmpty();

        if (noPosition && noActiveAlgos && closeReasonDefined) {
            return TransitionResult.moveTo(Deal.Status.CLOSED);
        }

        return TransitionResult.stay();
    }
}
