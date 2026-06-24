package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.DealOrchestratorProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.deal.DealStateMachine;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.safety.SafetyHoldCoordinator;
import com.example.tradingbot.persistence.service.DealDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Сопровождает уже созданные сделки, прогоняя их через FSM. Один проход:
 * выборка активных сделок (ограниченным окном) → для каждой собрать
 * DealContext → DealStateMachine → исполнить команды → применить переход.
 * Это execution boundary: unexpected exceptions ловятся здесь, сделка
 * уводится в ERROR (docs/rules/runtime-error-classification.md).
 * Concurrency-guard (D-M1) — БД-блок на весь проход через
 * {@link OrchestratorPassLock}: и таймерный, и ручной заход под одной
 * блокировкой. CRON/enabled — конвенция джоб
 * (.claude/rules/codestyle.md §Джобы). См.
 * docs/components/DealOrchestratorJob.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealOrchestratorJob {

    private final DealOrchestratorProperties properties;
    private final OrchestratorPassLock passLock;
    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final DealStateMachine dealStateMachine;
    private final ServiceCommandExecutor serviceCommandExecutor;
    private final SafetyHoldCoordinator safetyHoldCoordinator;

    @Scheduled(cron = "${deal-orchestrator.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        Boolean ran = passLock.runExclusively(this::run);
        if (isFalse(ran)) {
            log.debug("Deal orchestrator pass skipped: overlapping run");
        }
    }

    private void run() {
        List<Deal> activeDeals = dealDataService.findActive(properties.getBatchSize());
        for (Deal deal : activeDeals) {
            processDeal(deal);
        }
    }

    private void processDeal(Deal deal) {
        try {
            DealContext dealContext = dealContextService.build(deal);
            if (enforceHold(dealContext)) {
                return;
            }
            DealTransition transition = dealStateMachine.advance(dealContext);
            for (ServiceCommand command : transition.getCommands()) {
                serviceCommandExecutor.execute(command, dealContext);
            }
            applyTransition(deal, transition);
            reactToHoldSignal(transition, dealContext);
        } catch (RuntimeException e) {
            log.error("Deal orchestration failed dealId={}", deal.getId(), e);
            moveToErrorSafely(deal);
        }
    }

    /**
     * Enforcement активных сделок на held-scope (дизайн холдов шага 6 §5):
     * сделка под TRADE_BLOCKED-холдом (инструмент L3 или биржа L4 каскадом)
     * уводится в ERROR со shutdownReason — normal-flow по ней не запускается,
     * teardown live risk доводит ErrorHandler. Уже-ERROR сделки пропускаются
     * (их и так ведёт ErrorHandler). Возвращает true, если сделка перехвачена.
     */
    private boolean enforceHold(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (Deal.Status.ERROR.equals(deal.getStatus())) {
            return false;
        }
        Deal.ShutdownReason reason = heldShutdownReason(dealContext);
        if (isNull(reason)) {
            return false;
        }
        deal.setShutdownReason(reason);
        deal.setStatus(Deal.Status.ERROR);
        dealDataService.save(deal);
        return true;
    }

    /** Held-scope сделки: биржа TRADE_BLOCKED → EXCHANGE_HOLD; инструмент TRADE_BLOCKED → RISK_POLICY; иначе null. */
    private Deal.ShutdownReason heldShutdownReason(DealContext dealContext) {
        if (isTrue(dealContext.getExchange().isTradeBlocked())) {
            return Deal.ShutdownReason.EXCHANGE_HOLD;
        }
        if (isTrue(dealContext.getInstrument().isTradeBlocked())) {
            return Deal.ShutdownReason.RISK_POLICY;
        }
        return null;
    }

    /** Поднятый handler'ом safety-холд координируется над сделкой (после применения перехода). */
    private void reactToHoldSignal(DealTransition transition, DealContext dealContext) {
        if (nonNull(transition.getHoldSignal())) {
            safetyHoldCoordinator.react(transition.getHoldSignal(), dealContext);
        }
    }

    private void applyTransition(Deal deal, DealTransition transition) {
        if (isNull(transition.getNextStatus())) {
            return;
        }
        deal.setStatus(transition.getNextStatus());
        if (nonNull(transition.getCloseReason())) {
            deal.setCloseReason(transition.getCloseReason());
        }
        if (nonNull(transition.getShutdownReason())) {
            deal.setShutdownReason(transition.getShutdownReason());
        }
        dealDataService.save(deal);
    }

    /** Execution boundary: непредвиденная ошибка прохода → сделка в ERROR (ErrorHandler разберёт). */
    private void moveToErrorSafely(Deal deal) {
        try {
            deal.setStatus(Deal.Status.ERROR);
            dealDataService.save(deal);
        } catch (RuntimeException e) {
            log.error("Failed to move deal to ERROR dealId={}", deal.getId(), e);
        }
    }
}
