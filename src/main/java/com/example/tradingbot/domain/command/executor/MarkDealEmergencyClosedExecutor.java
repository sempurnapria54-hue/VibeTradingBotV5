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
import com.example.tradingbot.domain.command.calc.DealResult;
import com.example.tradingbot.domain.command.calc.DealResultCalculator;
import com.example.tradingbot.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingbot.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет MARK_DEAL_EMERGENCY_CLOSED_COMMAND — аварийный терминал
 * сделки (ERROR → EMERGENCY_CLOSED). Ребро ставится только по
 * подтверждённому отсутствию живого риска: снятие риска ведёт обработчик
 * ошибочного состояния, а это звено лишь закрывает сделку.
 *
 * <p><b>Число — best-effort ПО ДОСТУПНОСТИ, а не по составу</b>
 * (docs/spec/deal-lifecycle.json §emergencyTerminalContract): формула та
 * же, что у штатной тропы, и записывается только посчитанное. Пусто
 * означает «неисчислимо» — подстановка недопустима, в первую очередь ноль:
 * на тропе закрытия без входа он законен и на глаз неотличим.
 *
 * <p><b>Уже стоящее число не трогается</b>: его записала финализация
 * выхода до ухода в ошибочное состояние, и вместе с ним записаны признаки
 * отбора — на ПОЛНОМ графе. Аварийный терминал приходит на усечённом и
 * пересчитывать их не вправе.
 *
 * <p>См. docs/components/MarkDealEmergencyClosedExecutor.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkDealEmergencyClosedExecutor implements CommandExecutor {

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealResultCalculator resultCalculator;
    private final DealTerminalFeaturesWriter featuresWriter;
    private final DealReconciliationCalculator reconciliationCalculator;
    private final DealTerminalGate terminalGate;
    private final AnomalyReportService anomalyReportService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (Deal.Status.EMERGENCY_CLOSED.equals(deal.getStatus())) {
            return complete(actionState, dealContext);
        }
        if (isFalse(terminalGate.riskProvenAbsent(deal, deal.getTranches(), dealContext.getGraphComplete()))) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                    "аварийный терминал не ставится: живой риск не доказанно отсутствует"
                            + " (docs/spec/deal-lifecycle.json §riskProvenAbsent)");
        }
        Boolean resultFinalized = nonNull(deal.getResultProfit());
        if (isFalse(resultFinalized)) {
            // Best-effort: пишем только посчитанное. Недоступный итог оставляет
            // число пустым — «неисчислимо», а не подставленный ноль.
            DealResult result = resultCalculator.calculate(dealContext);
            if (isTrue(result.getAvailable())) {
                deal.setResultProfit(result.getResultProfit());
                deal.setResultProfitCurrency(result.getResultProfitCurrency());
            } else {
                journalResultNotComputable(dealContext, deal);
            }
        }
        featuresWriter.apply(dealContext, resultFinalized);
        if (isNull(deal.getCloseReason())) {
            deal.setCloseReason(Deal.CloseReason.EMERGENCY_CLOSE);
        }
        deal.setStatus(Deal.Status.EMERGENCY_CLOSED);
        dealDataService.save(deal);
        return complete(actionState, dealContext);
    }

    /**
     * Итог аварийно закрытой сделки неисчислим — пустота отличима от нуля
     * и СЧЁТНА. Природа факта — происшествие: по каждой такой сделке своя
     * строка. Клейм несущий: невключение неизвестного исхода в расчёт
     * ожидаемости корректно только при известном ЧИСЛЕ таких случаев, а
     * без строки оно тождественно нулю, а не неизвестно
     * (docs/components/MarkDealEmergencyClosedExecutor.md). Терминал этим
     * не блокируется.
     */
    private void journalResultNotComputable(DealContext dealContext, Deal deal) {
        try {
            anomalyReportService.journal(dealContext,
                    HoldSignal.instrumentJournal(Constants.Hold.RESULT_NOT_COMPUTABLE));
        } catch (RuntimeException e) {
            log.error("Journal RESULT_NOT_COMPUTABLE failed dealId={}", deal.getId(), e);
        }
    }

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
}
