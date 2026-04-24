package com.example.tradingbot.domain.service.deal.state_machine;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.handler.StateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealStateMachine {

    private final Set<StateHandler> handlers;

    public TransitionResult step(DealContext context, DealEvent event) {
        checkEntryFields(context, event);

        Deal deal = context.getDeal();
        Deal.Status currentStatus = deal.getStatus();

        StateHandler handler = getHandlerRequired(currentStatus);

        handler.checkEntryInvariants(context);

        TransitionResult result = handler.handle(context, event);

        handler.checkExitInvariants(context, result);

        if (Objects.nonNull(result.getNextStatus())) {
            deal.setStatus(result.getNextStatus());
        }

        return result;
    }

    private StateHandler getHandlerRequired(Deal.Status status) {
        return handlers.stream()
                       .filter(e -> Objects.equals(e.supportedStatus(), status))
                       .findFirst()
                       .orElseThrow(() -> new IllegalStateException("No handler for status: " + status));
    }

    private void checkEntryFields(DealContext context, DealEvent event) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(event);

        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        Deal.Status currentStatus = deal.getStatus();
        if (Objects.isNull(currentStatus)) {
            throw new IllegalStateException("deal.status is null");
        }
    }


}
