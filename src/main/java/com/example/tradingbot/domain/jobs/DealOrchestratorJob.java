package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.DealOrchestratorProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.action.SystemActionExecutor;
import com.example.tradingbot.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingbot.domain.command.RetryBudgetExhaustedException;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.deal.DealTrancheStateMachine;
import com.example.tradingbot.domain.deal.TrancheTransition;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.persistence.service.DealTrancheDataService;
import com.example.tradingbot.domain.deal.DealStateMachine;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.safety.HoldService;
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
 * Concurrency-guard (D-M1) — in-process не-реентрантность через
 * {@link JobExecutionGuard} (как остальные джобы): таймерный и ручной заход
 * сериализуются по имени джобы. БД advisory-замок (мультиинстанс) отложен на
 * фазу 3 — в фазе 1 один экземпляр, межэкземплярной конкуренции нет. CRON/enabled
 * — конвенция джоб
 * (.claude/rules/codestyle.md §Джобы). См.
 * docs/components/DealOrchestratorJob.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealOrchestratorJob {

    private static final String JOB_NAME = "dealOrchestratorJob";

    private final DealOrchestratorProperties properties;
    private final JobExecutionGuard executionGuard;
    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final DealStateMachine dealStateMachine;
    private final DealTrancheStateMachine dealTrancheStateMachine;
    private final DealTrancheDataService dealTrancheDataService;
    private final ServiceCommandExecutor serviceCommandExecutor;
    private final SystemActionExecutor systemActionExecutor;
    private final HoldService holdService;

    @Scheduled(cron = "${deal-orchestrator.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
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
            // Ревизия живых системных исполнений идёт ДО прохода: строка,
            // чья надобность снята фактами, иначе держала бы частичный ключ
            // и тратила бюджет на надобность, которой больше нет
            // (docs/components/SystemActionExecutor.md).
            systemActionExecutor.reviseLiveExecutions(dealContext);
            advanceTranches(dealContext);
            advanceDeal(dealContext);
        } catch (ControlledExchangeException e) {
            // Выделенный обработчик, класс броска 1: биржа отвергла поимённо
            // опознанным отказом. Биржевая ступень безусловна, и сделка
            // уходит ошибочной тропой независимо от уровня строки
            // (docs/rules/controlled-exchange-exceptions.md).
            log.warn("Controlled exchange violation dealId={}", deal.getId(), e);
            moveToErrorSafely(deal);
        } catch (RetryBudgetExhaustedException e) {
            // Выделенный обработчик, класс броска 2: бюджет попыток
            // исчерпан. Ошибочной тропой уводится только СИСТЕМНАЯ
            // (финализационная) строка; у стратегийной сделка остаётся в
            // своём статусе — реакцию даёт лестница, а повтор надобности
            // гейтится стоящей ступенью
            // (docs/components/DealOrchestratorJob.md).
            if (isTrue(e.getStrategyLevel())) {
                log.warn("Retry budget exhausted on strategy row dealId={} — deal stays in status {}",
                        deal.getId(), deal.getStatus(), e);
                return;
            }
            log.warn("Retry budget exhausted on system row dealId={}", deal.getId(), e);
            moveToErrorSafely(deal);
        } catch (RuntimeException e) {
            // Общий обработчик: всё остальное — сборка контекста, прогон
            // FSM, применение перехода — без радиусной реакции.
            log.error("Deal orchestration failed dealId={}", deal.getId(), e);
            moveToErrorSafely(deal);
        }
    }

    /**
     * Проход по живым траншам сделки. Каждый транш продвигается своей
     * машиной; намерение перехода применяется, только если контракт
     * переходов транша его разрешил.
     */
    private void advanceTranches(DealContext dealContext) {
        for (DealTranche tranche : dealContext.getDeal().liveTranches()) {
            TrancheTransition transition = dealTrancheStateMachine.advance(dealContext, tranche);
            executeCommands(transition.getCommands(), dealContext);
            if (isTrue(transition.getEscalateDealToError())) {
                moveToErrorSafely(dealContext.getDeal());
                return;
            }
            applyTrancheTransition(dealContext, tranche, transition);
        }
    }

    /** Проход по самой сделке: намерение перехода проверяется гейтом сделки. */
    private void advanceDeal(DealContext dealContext) {
        DealTransition transition = dealStateMachine.advance(dealContext);
        executeCommands(transition.getCommands(), dealContext);
        applyTransition(dealContext, transition);
        reactToHoldSignal(transition, dealContext);
    }

    /**
     * Исполнение команд перехода. Ступень safety, ЗАТРЕБОВАННАЯ звеном,
     * поднимается здесь — как и переход: исход звена есть намерение, а не
     * право (docs/processes/fsm-execution-layering.md).
     */
    private void executeCommands(List<ServiceCommand> commands, DealContext dealContext) {
        for (ServiceCommand command : commands) {
            ServiceCommandExecutionResult result = serviceCommandExecutor.execute(command, dealContext);
            holdService.raise(result.getHoldSignal(), dealContext);
            if (isFalse(result.getSuccess())) {
                // Команда провалилась (учёт retry/terminal сделал диспетчер) — остальные команды
                // перехода в этом проходе не гоним; handler разберёт FAILED-якорь на следующем тике.
                return;
            }
        }
    }

    /**
     * Применить намерение перехода транша — только через контракт
     * переходов: исход прохода есть намерение, а не право.
     */
    private void applyTrancheTransition(DealContext dealContext, DealTranche tranche,
                                        TrancheTransition transition) {
        if (isNull(transition.getNextStatus())) {
            return;
        }
        // Признак переоткрытия живёт на ОБЪЯВЛЕНИИ транша, а не на детали:
        // сетка и одиночный вход одной фазы вправе решать это по-разному.
        Boolean reopenAllowed = dealContext.reopenAllowed(tranche);
        if (isFalse(dealTrancheStateMachine.transitionAllowed(tranche, transition.getNextStatus(),
                dealContext.getDeal(), reopenAllowed, graphComplete(dealContext)))) {
            log.debug("Tranche transition is not allowed trancheId={} to={}",
                    tranche.getId(), transition.getNextStatus());
            return;
        }
        tranche.setStatus(transition.getNextStatus());
        // Причину закрытия пишет обработчик терминального ребра — той же
        // транзакцией, которой ребро применяется; write-once.
        if (nonNull(transition.getCloseReason()) && isNull(tranche.getCloseReason())) {
            tranche.setCloseReason(transition.getCloseReason());
        }
        dealTrancheDataService.save(tranche);
    }

    /**
     * Полнота графа исполнения прохода — признак читается ГОТОВЫМ с
     * контекста: его кладёт фабрика контекста, и пересобирать его здесь
     * значило бы обязать проход знать объём загрузки
     * (docs/spec/deal-context-load.json §graphComplete).
     */
    private Boolean graphComplete(DealContext dealContext) {
        return isTrue(dealContext.getGraphComplete());
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

    /**
     * Поднятая handler'ом ступень исполняется над сделкой (после применения
     * перехода). Ступень приходит СО СИГНАЛОМ, и кто ведёт реакцию —
     * решает общий исполнитель блокировки, а не этот проход.
     */
    private void reactToHoldSignal(DealTransition transition, DealContext dealContext) {
        if (nonNull(transition.getHoldSignal())) {
            holdService.raise(transition.getHoldSignal(), dealContext);
        }
    }

    private void applyTransition(DealContext dealContext, DealTransition transition) {
        Deal deal = dealContext.getDeal();
        if (isNull(transition.getNextStatus())) {
            return;
        }
        if (isFalse(dealStateMachine.transitionAllowed(dealContext, transition.getNextStatus(),
                graphComplete(dealContext)))) {
            log.debug("Deal transition is not allowed dealId={} to={}",
                    deal.getId(), transition.getNextStatus());
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
