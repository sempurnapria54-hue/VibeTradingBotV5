package com.example.tradingbot.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Координатор реактивной реакции CRITICAL-холда над сделкой: держатель
 * решения о последовательности, исполнители — DataService'ы (TRADE_BLOCKED),
 * {@link AnomalyReportService} (журнал + слепки), {@link KillSwitchService}
 * (kill-switch). Вызывается в проходе {@code DealOrchestratorJob} по
 * {@code DealTransition.holdSignal} (под advisory-локом прохода D-M1).
 *
 * <p>Последовательность (L3 и L4 одной формы, дизайн холдов шага 6 §2-3):
 * TRADE_BLOCKED scope <b>первым</b> (gate + анкер идемпотентности) →
 * AnomalyReport CREATED с before-слепком → IN_PROGRESS → kill-switch (scope) →
 * KILL_SWITCH_EXECUTED → after-слепок → COMPLETED. Идемпотентно по статусу
 * scope: TRADE_BLOCKED ставится только из ACTIVE — повторный сигнал того же
 * scope (или scope не-ACTIVE) пропускается. Реакция не пробрасывает исключение
 * наружу: сбой обработки фиксируется в AnomalyReport (ERROR), проход живёт.
 * Журналирование инцидента — <b>best-effort и не гейтит kill-switch</b>: сбой
 * любой записи отчёта (включая создание) логируется, но не подавляет teardown
 * риска и не выходит наружу — снятие риска приоритетнее журнала.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyHoldCoordinator {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final AnomalyReportService anomalyReportService;
    private final KillSwitchService killSwitchService;

    /** Поднять реактивный холд по сигналу. Идемпотентно по статусу scope. */
    public void react(HoldSignal signal, DealContext dealContext) {
        if (HoldScope.EXCHANGE.equals(signal.getScope())) {
            reactExchange(signal, dealContext);
            return;
        }
        reactInstrument(signal, dealContext);
    }

    private void reactInstrument(HoldSignal signal, DealContext dealContext) {
        if (isFalse(instrumentDataService.blockTrade(dealContext.getInstrument().getId()))) {
            return;
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireInstrument(dealContext));
    }

    private void reactExchange(HoldSignal signal, DealContext dealContext) {
        Long exchangeId = dealContext.getExchange().getId();
        if (isFalse(exchangeDataService.blockTrade(exchangeId))) {
            return;
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireExchange(exchangeId));
    }

    private void runReaction(HoldSignal signal, DealContext dealContext, Runnable killSwitch) {
        AnomalyReport report = openSafely(signal, dealContext);
        advanceSafely(report, AnomalyReport.Status.IN_PROGRESS);
        try {
            killSwitch.run();
            advanceSafely(report, AnomalyReport.Status.KILL_SWITCH_EXECUTED);
            completeSafely(report, dealContext);
        } catch (RuntimeException e) {
            log.error("Safety hold kill-switch failed scope={}", signal.getScope(), e);
            failSafely(report, e.getMessage());
        }
    }

    /**
     * Создать отчёт инцидента best-effort. Сбой создания журнала <b>не</b>
     * подавляет kill-switch: возвращаем {@code null}, дальнейшие записи журнала
     * становятся no-op, teardown риска продолжается.
     */
    private AnomalyReport openSafely(HoldSignal signal, DealContext dealContext) {
        try {
            return anomalyReportService.open(dealContext, signal);
        } catch (RuntimeException e) {
            log.error("Anomaly report open failed scope={} code={}", signal.getScope(), signal.getCode(), e);
            return null;
        }
    }

    private void advanceSafely(AnomalyReport report, AnomalyReport.Status status) {
        if (isNull(report)) {
            return;
        }
        try {
            anomalyReportService.advance(report, status);
        } catch (RuntimeException e) {
            log.error("Anomaly report advance failed anomalyReportId={} status={}", report.getId(), status, e);
        }
    }

    private void completeSafely(AnomalyReport report, DealContext dealContext) {
        if (isNull(report)) {
            return;
        }
        try {
            anomalyReportService.complete(report, dealContext);
        } catch (RuntimeException e) {
            log.error("Anomaly report complete failed anomalyReportId={}", report.getId(), e);
        }
    }

    private void failSafely(AnomalyReport report, String message) {
        if (isNull(report)) {
            return;
        }
        try {
            anomalyReportService.fail(report, message);
        } catch (RuntimeException e) {
            log.error("Anomaly report fail-write failed anomalyReportId={}", report.getId(), e);
        }
    }
}
