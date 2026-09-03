package com.example.tradingbot.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.util.Constants;
import java.util.function.Supplier;
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
 * KILL_SWITCH_EXECUTED → after-слепок → COMPLETED. <b>Терминал COMPLETED
 * гейтится подтверждением закрытия сверкой реального состояния биржи</b> (отчёт
 * самого kill-switch, bounded ретраем teardown внутри него): не подтверждено на
 * L3 (инструмент) → эскалация на биржевой холд + общебиржевой kill-switch
 * (HOLD-Q1); на L4 (биржа) отчёт остаётся открытым (KILL_SWITCH_EXECUTED),
 * досверка орфанов вне модели сделки — AnomalyJob (ANOM-Q2, шаг 8).
 * Идемпотентно по статусу
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
            absorbed(signal, dealContext);
            return;
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireInstrument(dealContext));
    }

    private void reactExchange(HoldSignal signal, DealContext dealContext) {
        Long exchangeId = dealContext.getExchange().getId();
        if (isFalse(exchangeDataService.blockTrade(exchangeId))) {
            absorbed(signal, dealContext);
            return;
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireExchange(exchangeId));
    }

    /**
     * Поглощённый сигнал: ступень уже стои́т, смена статуса и снятие риска
     * не гоняются — <b>но строка остаётся</b>
     * (docs/rules/error-handling-policy.md §«Поглощение наблюдаемо»).
     * Второе основание несёт свой машинный код, и без строки различимость
     * оснований пропадала бы ровно там, где нужна: контур стои́т, а
     * почему — в данных нет. Дедуп держит ключ состояния, не гард
     * перехода, поэтому повтор того же основания строки не множит.
     *
     * <p>Kill-switch этой тропой не гоняется: права на доведение
     * недоделанного у автоматического сигнала нет, оно есть только у
     * явного вызова держателя.
     */
    private void absorbed(HoldSignal signal, DealContext dealContext) {
        try {
            anomalyReportService.journalState(dealContext, signal, null);
        } catch (RuntimeException e) {
            log.error("Journal absorbed safety signal failed scope={} code={}",
                    signal.getScope(), signal.getCode(), e);
        }
    }

    private void runReaction(HoldSignal signal, DealContext dealContext, Supplier<Boolean> killSwitch) {
        AnomalyReport report = openSafely(signal, dealContext);
        advanceSafely(report, AnomalyReport.Status.IN_PROGRESS);
        try {
            Boolean closeConfirmed = killSwitch.get();
            advanceSafely(report, AnomalyReport.Status.KILL_SWITCH_EXECUTED);
            completeOrEscalate(report, signal, dealContext, closeConfirmed);
        } catch (RuntimeException e) {
            log.error("Safety hold kill-switch failed scope={}", signal.getScope(), e);
            failSafely(report, e.getMessage());
        }
    }

    /**
     * Терминал COMPLETED — только при подтверждённом (сверкой реального
     * состояния биржи) закрытии. Не подтверждено на L3 (инструмент) → эскалация
     * на биржевой холд + общебиржевой kill-switch (HOLD-Q1: неустранимый
     * остаток = интеграции нельзя доверять, радиус неизвестен → консервативно
     * тормозим биржу). На L4 (биржа) эскалировать дальше некуда — отчёт остаётся
     * открытым (KILL_SWITCH_EXECUTED); досверка реального состояния — AnomalyJob
     * (ANOM-Q2, шаг 8).
     */
    private void completeOrEscalate(AnomalyReport report, HoldSignal signal, DealContext dealContext,
                                    Boolean closeConfirmed) {
        if (isTrue(closeConfirmed)) {
            completeSafely(report, dealContext);
            return;
        }
        if (HoldScope.INSTRUMENT.equals(signal.getScope())) {
            log.error("L3 kill-switch not confirmed flat; escalating to exchange-wide hold exchangeId={}",
                    dealContext.getExchange().getId());
            reactExchange(HoldSignal.exchange(Constants.Hold.EXCHANGE_KILL_SWITCH_RESIDUAL), dealContext);
            return;
        }
        log.warn("Exchange kill-switch not confirmed flat; anomaly report kept open anomalyReportId={}",
                isNull(report) ? null : report.getId());
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
