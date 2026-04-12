package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EntrySubmittedHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ENTRY_SUBMITTED;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.ENTRY_SUBMITTED) {
            throw new IllegalStateException("deal.status must be ENTRY_SUBMITTED");
        }

        if (deal.getInstrumentId() == null) {
            throw new IllegalStateException("deal.instrumentId is null");
        }

        if (context.getStrategy() == null) {
            throw new IllegalStateException("strategy is null");
        }

        if (context.getStrategyDetails() == null) {
            throw new IllegalStateException("strategyDetails is null");
        }

        if (context.hasActivePosition()) {
            throw new IllegalStateException("position already exists before entry submission");
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
                        ServiceCommandType.CREATE_ENTRY_ORDER
                ));
            }
            case CHECK_EXIT_INVARIANTS -> {
                if (context.hasEntryOrder()) {
                    return TransitionResult.moveTo(Deal.Status.ENTRY_FINALIZED);
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
        if (result.getNextStatus() == Deal.Status.ENTRY_FINALIZED) {
            if (!context.hasEntryOrder()) {
                throw new IllegalStateException("transition to ENTRY_FINALIZED requires entryOrder");
            }
        }
    }
}