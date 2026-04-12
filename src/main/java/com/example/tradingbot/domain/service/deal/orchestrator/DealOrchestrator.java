package com.example.tradingbot.domain.service.deal.orchestrator;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.DealService;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandExecutor;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.DealStateMachine;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.domain.model.deal.Deal.CloseReason.EMERGENCY_STOP;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealOrchestrator {

    private final DealService dealService;

    private final DealContextService dealContextService;

    private final DealStateMachine dealStateMachine;

    private final ServiceCommandExecutor serviceCommandExecutor;

    public void runOneCycle(Long dealId) {
        Objects.requireNonNull(dealId);

        DealContext context = this.dealContextService.load(dealId);
        Deal deal = getRequiredDeal(context, dealId);

        log.debug("DealOrchestrator started cycle. dealId={}, status={}", deal.getId(), deal.getStatus());

        try {
            executeEvent(context, DealEvent.CHECK_ENTRY_INVARIANTS);

            context = refreshContext(dealId);

            executeEvent(context, DealEvent.PROCESS);

            context = refreshContext(dealId);

            executeEvent(context, DealEvent.CHECK_EXIT_INVARIANTS);

            context = refreshContext(dealId);

            persistDeal(context.getDeal());

            log.debug("DealOrchestrator finished cycle. dealId={}, status={}", context.getDeal()
                                                                                      .getId(), context.getDeal()
                                                                                                       .getStatus());
        } catch (Exception exception) {
            handleUnexpectedError(context, exception);
        }
    }

    public void runUntilStable(Long dealId, int maxCycles) {
        if (maxCycles <= 0) {
            throw new IllegalArgumentException("maxCycles must be > 0");
        }

        DealContext context = this.dealContextService.load(dealId);
        Deal deal = getRequiredDeal(context, dealId);

        Deal.Status previousStatus = null;

        for (int i = 0; i < maxCycles; i++) {
            Deal.Status currentStatus = deal.getStatus();

            runOneCycle(dealId);

            context = refreshContext(dealId);
            deal = getRequiredDeal(context, dealId);

            if (deal.getStatus() == Deal.Status.CLOSED) {
                return;
            }

            if (previousStatus != null
                    && previousStatus == deal.getStatus()
                    && currentStatus == deal.getStatus()) {
                return;
            }

            previousStatus = currentStatus;
        }
    }

    private void executeEvent(DealContext context, DealEvent event) {
        TransitionResult transitionResult = this.dealStateMachine.step(context, event);
        Deal deal = getRequiredDeal(context, null);

        if (transitionResult == null) {
            throw new IllegalStateException(
                    "TransitionResult is null. status=" + deal.getStatus() + ", event=" + event
            );
        }

        List<ServiceCommandType> commands = transitionResult.getCommands();
        if (commands == null || commands.isEmpty()) {
            persistDeal(deal);
            return;
        }

        log.debug(
                "DealOrchestrator executing commands. dealId={}, status={}, event={}, commandsCount={}",
                deal.getId(),
                deal.getStatus(),
                event,
                commands.size()
        );

        this.serviceCommandExecutor.execute(context, commands);

        persistDeal(deal);
    }

    private DealContext refreshContext(Long dealId) {
        return this.dealContextService.load(dealId);
    }

    private void persistDeal(Deal deal) {
        this.dealService.save(deal);
    }

    private Deal getRequiredDeal(DealContext context, Long dealId) {
        if (context == null) {
            throw new IllegalStateException("DealContext is null. dealId=" + dealId);
        }

        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("Deal is null. dealId=" + dealId);
        }

        return deal;
    }

    private void handleUnexpectedError(DealContext context, Exception exception) {
        Deal deal = getRequiredDeal(context, null);

        log.error(
                "Unexpected orchestrator error. dealId={}, status={}, message={}",
                deal.getId(),
                deal.getStatus(),
                exception.getMessage(),
                exception
        );

        if (deal.getCloseReason() == null) {
            deal.setCloseReason(EMERGENCY_STOP);
        }

        deal.setStatus(Deal.Status.ERROR);

        persistDeal(deal);
    }
}