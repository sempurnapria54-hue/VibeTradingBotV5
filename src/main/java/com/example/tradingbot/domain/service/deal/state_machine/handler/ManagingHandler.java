package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ManagingHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.MANAGING;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.MANAGING) {
            throw new IllegalStateException("deal.status must be MANAGING");
        }

        if (!context.hasActivePosition()) {
            throw new IllegalStateException("active position is required");
        }

        if (!context.hasMainProtection()) {
            throw new IllegalStateException("main protection is required");
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
                        ServiceCommandType.REFRESH_ALGO_ORDERS
                ));
            }
            case CHECK_EXIT_INVARIANTS -> {
                if (context.isPositionClosed()) {
                    return TransitionResult.moveTo(Deal.Status.EXIT_PENDING);
                }

                if (context.hasActivePosition() && !context.hasMainProtection()) {
                    context.getDeal()
                           .setCloseReason(Deal.CloseReason.PROTECTION_FAILED);
                    return TransitionResult.moveTo(Deal.Status.ERROR);
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
        if (result.getNextStatus() == Deal.Status.EXIT_PENDING) {
            if (!context.isPositionClosed()) {
                throw new IllegalStateException("transition to EXIT_PENDING requires closed position");
            }
        }
    }
}