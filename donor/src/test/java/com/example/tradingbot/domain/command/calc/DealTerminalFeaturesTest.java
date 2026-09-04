package com.example.tradingbot.domain.command.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tradingbot.config.ExchangeContourProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает четыре признака отбора сделки с их исполнимыми формами
 * (docs/spec/position-close-outcome.json,
 * docs/spec/deal-lifecycle.json §benchmarkAvailabilityOnTerminal).
 *
 * <p>Несущее — <b>старшинство исходов эпизодов</b>: неизвестное выше
 * штатного, иначе ликвидация соседнего эпизода пряталась бы за
 * NORMAL_EXIT; и <b>охрана от перезаписи</b>: на сделке с финализированным
 * числом терминал признаков не пишет вовсе.
 */
class DealTerminalFeaturesTest {

    private final AnomalyReportService anomalyReportService = mock(AnomalyReportService.class);
    private final DealReconciliationCalculator reconciliationCalculator =
            mock(DealReconciliationCalculator.class);
    private final DealTerminalFeaturesWriter writer = new DealTerminalFeaturesWriter(
            reconciliationCalculator, new ExchangeContourProperties(), anomalyReportService);

    @Test
    @DisplayName("Ликвидация соседнего эпизода не прячется за штатным выходом")
    void liquidationWinsOverNormalExit() {
        when(reconciliationCalculator.reconcile(any())).thenReturn(Deal.ReconciliationStatus.NOT_RUN);
        Deal deal = deal(episode(10, "2"), episode(-5, "3"));

        DealTerminalFeatures features = writer.apply(context(deal), false);

        assertEquals(Deal.CloseOutcome.LIQUIDATION, features.getCloseOutcome());
        assertEquals(Deal.CloseOutcome.LIQUIDATION, deal.getCloseOutcome());
    }

    @Test
    @DisplayName("Неизвестный тип закрытия даёт UNDETERMINED и поднимает журнальный отчёт")
    void unusableCloseTypeIsReported() {
        when(reconciliationCalculator.reconcile(any())).thenReturn(Deal.ReconciliationStatus.NOT_RUN);
        Deal deal = deal(episode(10, "99"));

        DealTerminalFeatures features = writer.apply(context(deal), false);

        assertEquals(Deal.CloseOutcome.UNDETERMINED, features.getCloseOutcome());
        assertTrue(features.getUnrecognizedCloseTypeReported());
    }

    @Test
    @DisplayName("Вошедшая сделка без единого эпизода: утверждать штатный выход не из чего")
    void enteredDealWithoutEpisodesIsUndetermined() {
        when(reconciliationCalculator.reconcile(any())).thenReturn(Deal.ReconciliationStatus.NOT_RUN);
        Deal deal = deal();

        DealTerminalFeatures features = writer.apply(context(deal), false);

        assertEquals(Deal.CloseOutcome.UNDETERMINED, features.getCloseOutcome());
    }

    @Test
    @DisplayName("Финализированное число охраняет признаки от перезаписи: терминал их не пишет")
    void finalizedResultKeepsFeatures() {
        Deal deal = deal(episode(10, "2"));
        deal.setCloseOutcome(Deal.CloseOutcome.NORMAL_EXIT);

        DealTerminalFeatures features = writer.apply(context(deal), true);

        assertNull(features.getCloseOutcome());
        // Прежнее значение осталось нетронутым, и сверка даже не считалась.
        assertEquals(Deal.CloseOutcome.NORMAL_EXIT, deal.getCloseOutcome());
        verifyNoInteractions(anomalyReportService);
    }

    @Test
    @DisplayName("Не вошедшая сделка: NOT_APPLICABLE — нормальная популяция, а не аномалия")
    void noEntryYieldsNotApplicable() {
        Deal deal = new Deal();
        deal.setEntryReason(Deal.EntryReason.STRATEGY);
        deal.setTranches(List.of());
        deal.setPositions(List.of());

        DealTerminalFeatures features = writer.apply(context(deal), false);

        assertEquals(Deal.RiskBenchmarkAvailability.NOT_APPLICABLE, features.getRiskBenchmarkAvailability());
        assertNull(features.getCloseOutcome());
        assertNull(features.getReconciliationStatus());
    }

    @Test
    @DisplayName("Вход был, знаменателя нет — MISSING с отчётом; есть — AVAILABLE")
    void benchmarkAvailabilityFollowsDenominator() {
        when(reconciliationCalculator.reconcile(any())).thenReturn(Deal.ReconciliationStatus.NOT_RUN);
        Deal missing = deal(episode(10, "2"));
        Deal available = deal(episode(10, "2"));
        available.setPlannedRiskAmount(BigDecimal.TEN);

        assertEquals(Deal.RiskBenchmarkAvailability.MISSING,
                writer.apply(context(missing), false).getRiskBenchmarkAvailability());
        assertEquals(Deal.RiskBenchmarkAvailability.AVAILABLE,
                writer.apply(context(available), false).getRiskBenchmarkAvailability());
    }

    @Test
    @DisplayName("Полнота разбивки: добыча не выполнялась — NOT_ASSESSED, окно накрыло — COMPLETE")
    void breakdownCompletenessFollowsWindow() {
        when(reconciliationCalculator.reconcile(any())).thenReturn(Deal.ReconciliationStatus.NOT_RUN);
        Deal notAssessed = deal(episode(10, "2"));

        assertEquals(Deal.BreakdownCompleteness.NOT_ASSESSED,
                writer.apply(context(notAssessed), false).getBreakdownIncomplete());

        Deal complete = deal(episode(10, "2"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        complete.setBillsWindowBegin(now.minusDays(3));
        complete.setBillsFetchedThrough(now);
        assertEquals(Deal.BreakdownCompleteness.COMPLETE,
                writer.apply(context(complete), false).getBreakdownIncomplete());

        Deal incomplete = deal(episode(10, "2"));
        incomplete.setBillsWindowBegin(now.minusDays(200));
        incomplete.setBillsFetchedThrough(now);
        assertEquals(Deal.BreakdownCompleteness.INCOMPLETE_BY_WINDOW,
                writer.apply(context(incomplete), false).getBreakdownIncomplete());
    }

    // ------------------------------------------------------------------

    private DealContext context(Deal deal) {
        Instrument instrument = new Instrument();
        instrument.setExternalSettlementCurrency("USDT");
        Exchange exchange = new Exchange();
        exchange.setName("OKX");
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .exchange(exchange)
                .graphComplete(true)
                .flowsComplete(true)
                .build();
    }

    private Deal deal(Position... episodes) {
        Deal deal = new Deal();
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        deal.setTranches(List.of());
        deal.setPositions(List.of(episodes));
        return deal;
    }

    private Position episode(Number realizedProfit, String closeType) {
        Position position = new Position();
        position.setExternalRealizedProfit(BigDecimal.valueOf(realizedProfit.doubleValue()));
        position.setExternalCloseType(closeType);
        return position;
    }
}
