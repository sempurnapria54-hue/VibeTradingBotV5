package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.DealClosedContent;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealResult;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.LossStreakCounter;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.util.Constants;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применяет аварийный терминал сделки — ребро
 * {@code ERROR → EMERGENCY_CLOSED}
 * (docs/components/MarkDealEmergencyClosedExecutor.md). Симметричен
 * штатному терминалу; на биржу не ходит — факты уже приземлены добычей.
 *
 * <p><b>Число — best-effort ПО ДОСТУПНОСТИ, а не по составу.</b> Формула
 * та же, что на чистой тропе: урезанная дала бы направленное смещение
 * вверх в и без того смещённой корзине. Итог недоступен — число остаётся
 * пустым со смыслом «неисчислимо», и сделка терминализуется всё равно;
 * подстановка запрещена, в первую очередь ноль — на тропе закрытия без
 * входа он законен и на глаз неотличим.
 *
 * <p><b>Первая ветка — «число уже стои́т»</b>, и она достижима: финализация
 * выхода пишет число durable своей транзакцией, а между ней и терминалом
 * сделка может уйти в ошибочное состояние. Пересчёт и затирание там
 * запрещены — доступность итога пересчитывается каждым проходом и на
 * усечённой загрузке ложна при законном числе. Тот же durable-факт
 * свидетельствует и о признаках отбора: они записаны на полном графе.
 *
 * <p><b>Причину закрытия звено не пишет:</b> её пишет затребователь ребра
 * — аварийный обработчик, той же транзакцией, которой ребро гейтит
 * (docs/lifecycles/Deal.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkDealEmergencyClosedExecutor implements CommandExecutor {

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealResultCalculator resultCalculator;
    private final DealReconciliationCalculator reconciliationCalculator;
    private final DealTerminalFeaturesWriter featuresWriter;
    private final DealTerminalGate terminalGate;
    private final LossStreakCounter lossStreakCounter;
    private final AnomalyReportService anomalyReportService;
    private final OutboxWriter outboxWriter;

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
            return complete(actionState, dealContext, false);
        }
        if (isFalse(terminalGate.riskProvenAbsent(deal, deal.getTranches(), dealContext.getGraphComplete()))) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                    "аварийный терминал не ставится: живой риск не доказанно отсутствует"
                            + " (docs/spec/deal-lifecycle.json §riskProvenAbsent)");
        }
        // Терминальность траншей аварийный терминал НЕ гейтит: требование
        // одно — доказанное отсутствие живого риска. Строка транша стала бы
        // второй точкой отказа аварийного контура.
        if (isNull(deal.getResultProfit())) {
            writeBestEffortResult(dealContext, deal);
            featuresWriter.apply(dealContext, false);
        }
        deal.setStatus(Deal.Status.EMERGENCY_CLOSED);
        dealDataService.save(deal);
        publishClosed(dealContext, deal);
        dealActionStateDataService.skipLiveSystemExecutions(dealContext.getActionStates(), anchorId(actionState));
        Boolean haltTriggered = lossStreakCounter.applyTerminal(dealContext);
        return complete(actionState, dealContext, haltTriggered);
    }

    /**
     * Число по доступности: посчитано — пишется вместе с валютой, не
     * посчитано — остаётся пустым и заводит счётный отчёт. Пустота
     * отличима от нуля и обязана быть счётной: невключение неизвестного
     * исхода в расчёт ожидаемости корректно только при известном числе
     * таких случаев.
     */
    private void writeBestEffortResult(DealContext dealContext, Deal deal) {
        DealResult result = resultCalculator.calculate(dealContext);
        if (isFalse(result.getAvailable())) {
            journal(dealContext, Constants.Hold.RESULT_NOT_COMPUTABLE);
            return;
        }
        deal.setResultProfit(result.getResultProfit());
        deal.setResultProfitCurrency(result.getResultProfitCurrency());
    }

    private void journal(DealContext dealContext, String code) {
        try {
            anomalyReportService.journal(dealContext, HoldSignal.instrumentJournal(code));
        } catch (RuntimeException e) {
            log.error("Journal {} failed dealId={}", code, dealContext.getDeal().getId(), e);
        }
    }

    /**
     * Завершение звена плюс затребование ступеней — теми же двумя
     * основаниями, что у штатного терминала, и после его коммита их
     * поднимает проход.
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
        outboxWriter.write(dealContext.getExchangeAccount().getTenantId(), CoreEventType.DEAL_CLOSED,
                new DealClosedContent(deal.getInternalId(),
                        dealContext.getExchangeAccount().getInternalId(),
                        dealContext.getInstrument().getInternalId(),
                        String.valueOf(deal.getStatus()), String.valueOf(deal.getCloseReason()),
                        String.valueOf(deal.getResultProfit()),
                        deal.getResultProfitCurrency()));
    }
}
