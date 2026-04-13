package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EntryFinalizedHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ENTRY_FINALIZED;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.ENTRY_FINALIZED) {
            throw new IllegalStateException("deal.status must be ENTRY_FINALIZED");
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
                        ServiceCommandType.REFRESH_ENTRY_ORDER,
                        ServiceCommandType.REFRESH_POSITIONS,
                        ServiceCommandType.REFRESH_ALGO_ORDERS
                ));
            }
            case CHECK_EXIT_INVARIANTS -> {
                if (context.isEntryOrderFinal() && context.hasActivePosition() && context.hasAttachedStopLoss()) {
                    return TransitionResult.moveTo(Deal.Status.PROTECTION_SWITCHED);
                }

                if (context.isEntryOrderFinal() && context.hasActivePosition() && !context.hasAttachedStopLoss()) {
                    context.getDeal()
                           .setCloseReason(Deal.CloseReason.PROTECTION_FAILED);
                    return TransitionResult.moveTo(Deal.Status.ERROR);
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
        if (result.getNextStatus() == Deal.Status.PROTECTION_SWITCHED) {
            if (!context.isEntryOrderFinal()) {
                throw new IllegalStateException("entryOrder must be final before PROTECTION_SWITCHED");
            }

            if (!context.hasActivePosition()) {
                throw new IllegalStateException("active position is required before PROTECTION_SWITCHED");
            }

            if (!context.hasAttachedStopLoss()) {
                throw new IllegalStateException("attached stop loss is required before PROTECTION_SWITCHED");
            }
        }
    }
}