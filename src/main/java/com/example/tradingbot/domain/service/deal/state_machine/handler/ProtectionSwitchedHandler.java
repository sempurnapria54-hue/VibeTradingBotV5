package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProtectionSwitchedHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PROTECTION_SWITCHED;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.PROTECTION_SWITCHED) {
            throw new IllegalStateException("deal.status must be PROTECTION_SWITCHED");
        }

        if (!context.hasActivePosition()) {
            throw new IllegalStateException("active position is required");
        }

        if (!context.hasEntryOrder()) {
            throw new IllegalStateException("entryOrder is required");
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
                        ServiceCommandType.REFRESH_POSITIONS,
                        ServiceCommandType.CREATE_MAIN_PROTECTION,
                        ServiceCommandType.REFRESH_ALGO_ORDERS,
                        ServiceCommandType.CANCEL_ATTACHED_PROTECTION,
                        ServiceCommandType.REFRESH_ALGO_ORDERS
                ));
            }
            case CHECK_EXIT_INVARIANTS -> {
                if (context.isPositionClosed()) {
                    return TransitionResult.moveTo(Deal.Status.EXIT_PENDING);
                }

                if (context.isReadyForManaging()) {
                    return TransitionResult.moveTo(Deal.Status.MANAGING);
                }

                return TransitionResult.stay();
            }
            case FAIL -> {
                context.getDeal()
                       .setCloseReason(Deal.CloseReason.PROTECTION_FAILED);
                return TransitionResult.moveTo(Deal.Status.ERROR);
            }
            default -> {
                return TransitionResult.stay();
            }
        }
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        if (result.getNextStatus() == Deal.Status.MANAGING) {
            if (!context.isReadyForManaging()) {
                throw new IllegalStateException("transition to MANAGING requires ready-for-managing context");
            }
        }
    }
}