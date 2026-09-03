package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.risk.RiskCheckResult;
import com.example.tradingbot.domain.command.risk.RiskValidator;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает детекторы инвариантов живой сделки с их домами
 * (docs/rules/live-risk-protection.md §«Реакция на непокрытый риск»,
 * docs/models/domain/aggregate/Deal.md, docs/rules/instrument-hold.md
 * §«Форма реакции на нарушение риск-политики при живой защите»).
 *
 * <p>Несущее для этого теста — <b>гейт полноты графа</b>: на неполном
 * графе операнды занижены, и детектор обязан МОЛЧАТЬ. Ложный триггер
 * `A4`/`A11` сносит всю биржу.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DealInvariantDetectorsTest {

    @Mock
    private DealDataService dealDataService;

    @Mock
    private DealContextService dealContextService;

    @Mock
    private DealTerminalGate dealTerminalGate;

    @Mock
    private RiskValidator riskValidator;

    @Mock
    private AnomalyReaction reaction;

    @InjectMocks
    private DealInvariantDetectors detectors;

    @Test
    @DisplayName("A4: живой транш без покрытия — биржевая ступень 2")
    void uncoveredTrancheRaisesExchangeTeardown() {
        given(context(tranche(true, false), true));
        when(dealTerminalGate.exposureReconciled(any(), any())).thenReturn(Boolean.TRUE);

        detectors.detect(exchange());

        assertEquals(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED, captured().getCode());
        assertEquals(HoldRung.HARD, captured().getRung());
    }

    @Test
    @DisplayName("A4 МОЛЧИТ на покрытом транше: штатное состояние аномалией не считается")
    void coveredTrancheIsNotAnomaly() {
        given(context(tranche(true, true), true));
        when(dealTerminalGate.exposureReconciled(any(), any())).thenReturn(Boolean.TRUE);

        detectors.detect(exchange());

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("Неполный граф глушит ВСЕ детекторы сделки: операнды на нём занижены")
    void incompleteGraphSilencesEveryDetector() {
        given(context(tranche(true, false), false));

        detectors.detect(exchange());

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("A11: сумма экспозиций разошлась с нетто-размером — биржевая ступень 2")
    void exposureMismatchRaisesExchangeTeardown() {
        given(context(tranche(false, true), true));
        when(dealTerminalGate.exposureReconciled(any(), any())).thenReturn(Boolean.FALSE);

        detectors.detect(exchange());

        assertEquals(Constants.Hold.EXCHANGE_EXPOSURE_MISMATCH, captured().getCode());
    }

    @Test
    @DisplayName("A12: потолки нарушены при нулевом акте — МЯГКАЯ ступень инструмента")
    void riskPolicyBreachRaisesInstrumentSoftRung() {
        given(context(tranche(false, true), true));
        when(dealTerminalGate.exposureReconciled(any(), any())).thenReturn(Boolean.TRUE);
        when(riskValidator.ceilingsBreachedWithoutAct(any()))
                .thenReturn(List.of(RiskCheckResult.blocked(
                        RiskCheckResult.RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED, "x", null)));

        detectors.detect(exchange());

        AnomalyFinding finding = captured();
        assertEquals(Constants.Hold.RISK_POLICY_BREACH_UNDER_PROTECTION, finding.getCode());
        assertEquals(HoldScope.INSTRUMENT, finding.getScope());
        assertEquals(HoldRung.SOFT, finding.getRung());
    }

    private AnomalyFinding captured() {
        ArgumentCaptor<AnomalyFinding> captor = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction).apply(captor.capture(), any());
        return captor.getValue();
    }

    private void given(DealContext context) {
        when(dealDataService.findActiveByExchangeId(anyLong())).thenReturn(List.of(context.getDeal()));
        when(dealContextService.build(any())).thenReturn(context);
        when(riskValidator.ceilingsBreachedWithoutAct(any())).thenReturn(List.of());
    }

    private DealContext context(DealTranche tranche, Boolean graphComplete) {
        Deal deal = new Deal();
        deal.setId(11L);
        deal.setTranches(List.of(tranche));
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .exchange(exchange())
                .graphComplete(graphComplete)
                .build();
    }

    private DealTranche tranche(Boolean riskBearing, Boolean covered) {
        DealTranche tranche = new DealTranche() {
            @Override
            public Boolean isRiskBearing() {
                return riskBearing;
            }

            @Override
            public Boolean isCovered() {
                return covered;
            }
        };
        tranche.setId(3L);
        return tranche;
    }

    private Exchange exchange() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        return exchange;
    }
}
