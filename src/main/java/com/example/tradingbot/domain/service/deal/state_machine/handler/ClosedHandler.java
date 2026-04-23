package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

@Service
public class ClosedHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.CLOSED;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.CLOSED) {
            throw new IllegalStateException("deal.status must be CLOSED");
        }
    }

    @Override
    public TransitionResult handle(DealContext context, DealEvent event) {
        return TransitionResult.stay();
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        // CLOSED — терминальный успешный статус.
    }
}