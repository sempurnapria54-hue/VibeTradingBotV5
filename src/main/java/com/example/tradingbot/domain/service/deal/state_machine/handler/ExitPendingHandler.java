package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExitPendingHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.EXIT_PENDING;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.EXIT_PENDING) {
            throw new IllegalStateException("deal.status must be EXIT_PENDING");
        }
    }

    @Override
    public TransitionResult handle(DealContext context, DealEvent event) {
        switch (event) {
            case CHECK_ENTRY_INVARIANTS -> {
                return TransitionResult.stay();
            }
            case PROCESS, RETRY -> {
                return TransitionResult.stay(List.of(
                        ServiceCommandType.FINALIZE_EXIT
                ));
            }
            case CHECK_EXIT_INVARIANTS -> {
                boolean closeReasonDefined = context.getDeal()
                                                    .getCloseReason() != null;
                boolean noPosition = context.isReadyForExitPending();
                boolean noActiveAlgos = context.getActiveAlgoOrders() == null || context.getActiveAlgoOrders()
                                                                                        .isEmpty();

                if (noPosition && noActiveAlgos && closeReasonDefined) {
                    return TransitionResult.moveTo(Deal.Status.CLOSED);
                }

                return TransitionResult.stay();
            }
            case FAIL -> {
                context.getDeal()
                       .setCloseReason(Deal.CloseReason.EMERGENCY_STOP);
                return TransitionResult.moveTo(Deal.Status.ERROR);
            }
            default -> {
                return TransitionResult.stay();
            }
        }
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        if (result.getNextStatus() == Deal.Status.CLOSED) {
            if (!context.isReadyForExitPending()) {
                throw new IllegalStateException("transition to CLOSED requires finalized exit context");
            }
        }
    }
}