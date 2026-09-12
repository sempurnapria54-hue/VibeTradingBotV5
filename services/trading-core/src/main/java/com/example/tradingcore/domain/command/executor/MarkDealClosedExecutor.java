package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.LossStreakCounter;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применяет терминал штатного закрытия сделки — рёбра
 * {@code ACTIVE → CLOSED} и {@code EXIT_PENDING → CLOSED}
 * (docs/components/MarkDealClosedExecutor.md).
 *
 * <p><b>Числа этот исполнитель обычно не пишет — он его АССЕРТИТ.</b> На
 * штатной тропе число уже записала финализация выхода, и подстановка нуля
 * объявляла бы посчитанным то, что не посчитано. Исключение — тропы
 * закрытия БЕЗ входа: там ноль есть результат расчёта, и пишет его сам
 * терминал, вместе с валютой и признаком доступности знаменателя.
 *
 * <p><b>Причину закрытия звено не подставляет</b>: она берётся
 * СТАРШИНСТВОМ причин траншей (docs/lifecycles/Deal.md). Подставленный
 * «выход по стратегии» объявлял бы штатным выходом и то закрытие, которого
 * стратегия не запрашивала.
 *
 * <p><b>Ступени затребуются, а поднимает их проход</b> — после коммита
 * терминала: реакция пишет по общей строке торгового состояния счёта, и её
 * отказ в общей транзакции откатил бы применение терминала. Оснований два
 * и коды у них разные — расхождение сверки и достигнутый предел серии
 * убытков; оба могут сработать одним ходом.
 *
 * <p><b>Ребро пишется ТОЧЕЧНЫМ гардированным запросом, а не строкой
 * целиком</b> (docs/components/MarkDealClosedExecutor.md §«Побочные
 * эффекты терминала»). Гард — исходный статус: проактивная детекция
 * уводит активные сделки радиуса в {@code ERROR} своим потоком, и
 * терминал, выведенный из снимка начала прохода, снял бы этот каскад
 * молча. Ребро не применилось — звено не завершается, и следующий проход
 * ведёт сделку уже ошибочной тропой.
 *
 * <p><b>Названное ограничение: базу риска терминал не двигает.</b> Ход
 * «следовать за свободным остатком в обе стороны» не построен ни одним
 * писателем, поэтому нет и омиссии, о которой отчитываться: код
 * {@code RISK_BASE_OPERAND_MISSING} заводится вместе со своим ходом, не
 * раньше (docs/models/domain/core/Exchange.md, дом задачи —
 * .claude/work/backlog.md §«Хвост шага 8 (safety / AnomalyJob) — возврат
 * по появлению носителя»).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkDealClosedExecutor implements CommandExecutor {

    /** Статусы, из которых штатный терминал законен. */
    private static final List<Deal.Status> ACTIVE_STATUSES =
            List.of(Deal.Status.ACTIVE, Deal.Status.EXIT_PENDING);

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealReconciliationCalculator reconciliationCalculator;
    private final DealTerminalFeaturesWriter featuresWriter;
    private final DealTerminalGate terminalGate;
    private final LossStreakCounter lossStreakCounter;
    private final AnomalyReportService anomalyReportService;
    private final CoreEventWriter coreEventWriter;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.MARK_DEAL_CLOSED_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (Deal.Status.CLOSED.equals(deal.getStatus())) {
            return complete(actionState, dealContext, false);
        }
        if (isFalse(terminalAllowed(deal, dealContext.getGraphComplete()))) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                    "терминал не ставится: не все транши терминальны либо живой риск не доказанно отсутствует"
                            + " (docs/spec/deal-lifecycle.json §riskProvenAbsent)");
        }
        if (isNull(deal.getResultProfit())) {
            if (isTrue(deal.positionObserved())) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                        "штатный терминал требует посчитанного числа: финализация выхода его не записала"
                                + " (docs/spec/deal-lifecycle.json §cleanTerminalContract)");
            }
            writeResultOfPathWithoutEntry(dealContext, deal);
        }
        if (isNull(deal.getCloseReason())) {
            deal.setCloseReason(deal.closeReasonBySeniority());
        }
        Deal.Status fromStatus = deal.getStatus();
        deal.setStatus(Deal.Status.CLOSED);
        if (isFalse(dealDataService.applyTerminalEdge(deal, ACTIVE_STATUSES))) {
            deal.setStatus(fromStatus);
            return ServiceCommandExecutionResult.notCompleted(
                    "терминал не применён: сделка ушла из-под прохода — статус в базе больше не активен"
                            + " (docs/components/DealOrchestratorJob.md §«Цикл прохода»)");
        }
        publishClosed(dealContext, deal);
        dealActionStateDataService.skipLiveSystemExecutions(dealContext.getActionStates(), anchorId(actionState));
        Boolean haltTriggered = lossStreakCounter.applyTerminal(dealContext);
        return complete(actionState, dealContext, haltTriggered);
    }

    /**
     * Тропа закрытия БЕЗ входа: считать не по чему, и ноль здесь —
     * результат тропы, а не подставленное умолчание. Валюта пишется, только
     * если резолвится; не резолвится — ноль без валюты плюс журнальный
     * отчёт, и проверка ребра смягчается до одного числа.
     *
     * <p>Признаки отбора тем же ходом: доступность знаменателя получает
     * значение «неприменимо» — знаменателя нет потому, что входа не было.
     */
    private void writeResultOfPathWithoutEntry(DealContext dealContext, Deal deal) {
        deal.setResultProfit(BigDecimal.ZERO);
        deal.setResultProfitCurrency(dealContext.getInstrument().getExternalSettlementCurrency());
        journalCurrencyUnresolved(dealContext, deal);
        featuresWriter.apply(dealContext, false);
        dealDataService.applyResultAndFeatures(deal);
    }

    /**
     * Валюта результата не разрешилась — ноль записан, но в чём он выражен,
     * неизвестно. Природа факта — ПРОИСШЕСТВИЕ: свой момент у каждой такой
     * сделки, и счётность обязательна, иначе популяция сделок с
     * невыраженным нулём не всплывает ни в одном сигнале. Терминал этим не
     * блокируется.
     */
    private void journalCurrencyUnresolved(DealContext dealContext, Deal deal) {
        if (nonNull(deal.getResultProfitCurrency())) {
            return;
        }
        try {
            anomalyReportService.journal(dealContext,
                    HoldSignal.instrumentJournal(Constants.Hold.RESULT_CURRENCY_UNRESOLVED));
        } catch (RuntimeException e) {
            log.error("Journal RESULT_CURRENCY_UNRESOLVED failed dealId={}", deal.getId(), e);
        }
    }

    /**
     * Завершение звена плюс затребование ступеней по двум основаниям.
     * Признак сверки читается со сделки, а не пересчитывается: писатель у
     * него один — тот, кто признаки писал.
     */
    private ServiceCommandExecutionResult complete(DealActionState actionState, DealContext dealContext,
                                                   Boolean haltTriggered) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
        List<HoldSignal> requested = new ArrayList<>();
        if (isTrue(reconciliationCalculator.rungRequested(dealContext,
                dealContext.getDeal().getReconciliationStatus()))) {
            requested.add(HoldSignal.exchangeAccountSoft(Constants.Hold.PNL_RECONCILIATION_MISMATCH));
        }
        if (isTrue(haltTriggered)) {
            requested.add(HoldSignal.exchangeAccountSoft(Constants.Hold.LOSS_STREAK_LIMIT_REACHED));
        }
        return requested.isEmpty()
                ? ServiceCommandExecutionResult.ok()
                : ServiceCommandExecutionResult.okWithHolds(requested);
    }

    /**
     * Право на терминал — <b>тот же гейт, что у машины сделки</b>: все
     * транши терминальны и живой риск доказанно отсутствует
     * (docs/spec/deal-lifecycle.json §transitionAllowed). Ребро ставит это
     * звено, а не машина, поэтому и гейт читается здесь.
     */
    private Boolean terminalAllowed(Deal deal, Boolean graphComplete) {
        return isTrue(deal.allTranchesTerminal())
                && isTrue(terminalGate.riskProvenAbsent(deal, deal.getTranches(), graphComplete));
    }

    private Long anchorId(DealActionState actionState) {
        return isNull(actionState) ? null : actionState.getId();
    }

    /**
     * Событие закрытия сделки — <b>той же транзакцией</b>, что применяет
     * терминальное ребро (docs/architecture/contracts.md §«У каждого
     * класса события назван писатель, и он же писатель решения»).
     * Писателей у класса два, потому что терминалов два.
     */
    private void publishClosed(DealContext dealContext, Deal deal) {
        coreEventWriter.dealClosed(dealContext.getExchangeAccount().getTenantId(), deal,
                dealContext.getExchangeAccount().getInternalId(),
                dealContext.getInstrument().getInternalId(),
                dealContext.strategyInternalId(),
                dealContext.getGraphComplete());
    }
}
