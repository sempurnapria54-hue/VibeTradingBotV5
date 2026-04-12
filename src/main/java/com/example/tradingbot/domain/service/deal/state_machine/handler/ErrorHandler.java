package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ErrorHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ERROR;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.ERROR) {
            throw new IllegalStateException("deal.status must be ERROR");
        }
    }

    @Override
    public TransitionResult handle(DealContext context, DealEvent event) {
        switch (event) {
            case CHECK_ENTRY_INVARIANTS -> {
                return TransitionResult.stay();
            }
            case PROCESS, RETRY, CHECK_EXIT_INVARIANTS -> {
                return TransitionResult.stay(List.of(
                        ServiceCommandType.EXECUTE_KILL_SWITCH
                ));
            }
            case FAIL -> {
                return TransitionResult.stay();
            }
            default -> {
                return TransitionResult.stay();
            }
        }
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        // ERROR терминален.
        // При желании позже можно проверить, что kill-switch действительно снял риск по инструменту.
    }
}