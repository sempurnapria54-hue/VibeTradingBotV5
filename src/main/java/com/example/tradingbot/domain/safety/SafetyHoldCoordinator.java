package com.example.tradingbot.domain.safety;

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
        AnomalyReport report = anomalyReportService.open(dealContext, signal);
        try {
            report = anomalyReportService.advance(report, AnomalyReport.Status.IN_PROGRESS);
            killSwitch.run();
            report = anomalyReportService.advance(report, AnomalyReport.Status.KILL_SWITCH_EXECUTED);
            anomalyReportService.complete(report, dealContext);
        } catch (RuntimeException e) {
            log.error("Safety hold reaction failed anomalyReportId={} scope={}", report.getId(), signal.getScope(), e);
            anomalyReportService.fail(report, e.getMessage());
        }
    }
}
