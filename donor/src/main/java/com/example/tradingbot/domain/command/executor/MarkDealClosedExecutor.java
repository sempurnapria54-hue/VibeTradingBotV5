package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingbot.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingbot.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет MARK_DEAL_CLOSED_COMMAND — терминальное ребро штатного
 * закрытия (EXIT_PENDING → CLOSED). Ставит терминал только после
 * подтверждённого отсутствия живого риска.
 *
 * <p><b>Числа этот исполнитель не пишет — он его АССЕРТИТ</b>
 * (docs/models/domain/aggregate/Deal.md §«Расчёт и запись — писателей
 * три»): на штатной тропе число уже записала финализация выхода, и
 * подстановка нуля объявляла бы посчитанным то, что не посчитано.
 * Исключение — тропа закрытия БЕЗ входа: там ноль есть результат
 * расчёта, и пишет его сам терминал.
 *
 * <p><b>Расхождение сверки поднимает ступень 1 после коммита терминала</b>
 * и только в боевом режиме допуска: до калибровки расхождение неотличимо
 * от «допуск не тот» (docs/rules/pnl-reconciliation.md §«Реакция на
 * расхождение»). Ступень ЗАТРЕБОВАНА результатом, а поднимает её проход:
 * иначе отказ реакции откатил бы применение терминала.
 *
 * <p>См. docs/components/MarkDealClosedExecutor.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkDealClosedExecutor implements CommandExecutor {

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealReconciliationCalculator reconciliationCalculator;
    private final DealTerminalFeaturesWriter featuresWriter;
    private final DealTerminalGate terminalGate;
    private final AnomalyReportService anomalyReportService;

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
            return complete(actionState, dealContext);
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
            // Тропа закрытия БЕЗ входа: считать не по чему, и ноль здесь —
            // результат тропы, а не подставленное умолчание.
            deal.setResultProfit(BigDecimal.ZERO);
            deal.setResultProfitCurrency(dealContext.getInstrument().getExternalSettlementCurrency());
            journalCurrencyUnresolved(dealContext, deal);
            featuresWriter.apply(dealContext, false);
        }
        if (isNull(deal.getCloseReason())) {
            // Причина берётся СТАРШИНСТВОМ причин траншей, а не умолчанием:
            // подставленный STRATEGY_EXIT объявлял бы штатным выходом и то
            // закрытие, которого стратегия не запрашивала.
            deal.setCloseReason(deal.closeReasonBySeniority());
        }
        deal.setStatus(Deal.Status.CLOSED);
        dealDataService.save(deal);
        return complete(actionState, dealContext);
    }

    /**
     * Валюта результата не разрешилась — ноль записан, но в чём он
     * выражен, неизвестно. Природа факта — ПРОИСШЕСТВИЕ: свой момент у
     * каждой такой сделки, и счётность обязательна, иначе популяция
     * сделок с невыраженным нулём не всплывает ни в одном сигнале
     * (docs/components/MarkDealClosedExecutor.md). Терминал этим не
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
     * Завершение звена плюс затребование ступени по уже записанному
     * признаку сверки. Признак читается со сделки, а не пересчитывается:
     * писатель у него один — финализация выхода.
     */
    private ServiceCommandExecutionResult complete(DealActionState actionState, DealContext dealContext) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
        if (isTrue(reconciliationCalculator.rungRequested(dealContext,
                dealContext.getDeal().getReconciliationStatus()))) {
            return ServiceCommandExecutionResult.okWithHold(
                    HoldSignal.exchangeSoft(Constants.Hold.PNL_RECONCILIATION_MISMATCH));
        }
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Право на терминал — <b>тот же гейт, что у машины сделки</b>: все
     * транши терминальны и живой риск доказанно отсутствует
     * (docs/spec/deal-lifecycle.json §transitionAllowed). Ребро ставит
     * это звено, а не машина, поэтому и гейт читается здесь: свой,
     * упрощённый предикат живого риска был бы вторым носителем инварианта
     * и разошёлся бы с первым — в разрешающую сторону, потому что не
     * видит ни сверки экспозиции, ни полноты графа.
     */
    private Boolean terminalAllowed(Deal deal, Boolean graphComplete) {
        return isTrue(deal.allTranchesTerminal())
                && isTrue(terminalGate.riskProvenAbsent(deal, deal.getTranches(), graphComplete));
    }
}
