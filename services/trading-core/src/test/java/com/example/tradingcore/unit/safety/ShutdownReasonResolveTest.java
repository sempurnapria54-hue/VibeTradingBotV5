package com.example.tradingcore.unit.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.safety.HardRungShutdownReasonResolver;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.persistence.service.DealDataService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Причина выхода из штатного ведения по стоящей ступени — группа `U4`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/spec/hard-rung-shutdown-reason.json, величины
 * {@code hardRungShutdownReason} и {@code firstMoveShutdownReason};
 * прозаический дом — docs/lifecycles/Deal.md §«Причина выхода из
 * штатного ведения»).
 *
 * <p><b>Базовая сборка:</b> читатель причины; служба сделок подменена и
 * отвечает двумя множествами — сделки под счётной ступенью и сделки под
 * ступенью пары. Перечень идентичностей подаётся прямо: иначе краснота
 * не различала бы «читатель решил не так» и «ребро поставило не ту
 * ступень».
 */
class ShutdownReasonResolveTest {

    private static final Long DEAL = 11L;
    private static final Long OTHER_DEAL = 12L;

    private final DealDataService deals = mock(DealDataService.class);

    private HardRungShutdownReasonResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new HardRungShutdownReasonResolver(deals);
        standing(List.of(), List.of());
    }

    private void standing(List<Long> underAccountRung, List<Long> underInstrumentRung) {
        when(deals.findIdsUnderAccountRung(any())).thenReturn(underAccountRung);
        when(deals.findIdsUnderInstrumentRung(any())).thenReturn(underInstrumentRung);
    }

    /** Пустота значащая: что делать со сделкой без ступеней, решает затребователь. */
    @Test
    @DisplayName("U4.1 — сделка не стои́т ни под одной ступенью: в раскладке её нет вовсе")
    void u4_1_aDealUnderNoRungIsAbsentFromTheMap() {
        Map<Long, Deal.ShutdownReason> resolved = resolver.resolveByStandingRung(List.of(DEAL));

        assertThat(resolved).as("отсутствие — значащая пустота").doesNotContainKey(DEAL);
    }

    /** Стои́т только ступень пары — причина риск-политики. */
    @Test
    @DisplayName("U4.2 — стои́т только ступень пары: причина риск-политики")
    void u4_2_theStandingPairRungGivesTheRiskPolicyReason() {
        standing(List.of(), List.of(DEAL));

        assertThat(resolver.resolveByStandingRung(List.of(DEAL)))
                .containsEntry(DEAL, Deal.ShutdownReason.RISK_POLICY);
    }

    /** Стои́т только счётная ступень — причина биржевого сворачивания. */
    @Test
    @DisplayName("U4.3 — стои́т только счётная ступень: причина биржевого сворачивания")
    void u4_3_theStandingAccountRungGivesTheExchangeHoldReason() {
        standing(List.of(DEAL), List.of());

        assertThat(resolver.resolveByStandingRung(List.of(DEAL)))
                .containsEntry(DEAL, Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** Счёт читается первым: старшинство согласовано с доминированием биржевых ступеней. */
    @Test
    @DisplayName("U4.4 — стоя́т обе ступени: причина биржевая — счёт читается первым")
    void u4_4_theAccountRungWinsOverThePairRung() {
        standing(List.of(DEAL), List.of(DEAL));

        assertThat(resolver.resolveByStandingRung(List.of(DEAL)))
                .containsEntry(DEAL, Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** Обе выборки идут один раз каждая и на пустом перечне. */
    @Test
    @DisplayName("U4.5 — перечень сделок пуст: раскладка пуста, обе выборки позваны по разу")
    void u4_5_anEmptyDealListStillAsksBothQueriesOnce() {
        Map<Long, Deal.ShutdownReason> resolved = resolver.resolveByStandingRung(List.of());

        assertThat(resolved).isEmpty();
        verify(deals, times(1)).findIdsUnderAccountRung(List.of());
        verify(deals, times(1)).findIdsUnderInstrumentRung(List.of());
    }

    /** Обращений ровно два — по одному на радиус, а не по одному на сделку. */
    @Test
    @DisplayName("U4.6 — перечень из многих сделок: обращений к службе ровно два, запроса на сделку нет")
    void u4_6_theQueriesArePerScopeNotPerDeal() {
        List<Long> dealIds = List.of(DEAL, OTHER_DEAL, 13L, 14L);
        standing(List.of(DEAL), List.of(OTHER_DEAL));

        resolver.resolveByStandingRung(dealIds);

        verify(deals, times(1)).findIdsUnderAccountRung(dealIds);
        verify(deals, times(1)).findIdsUnderInstrumentRung(dealIds);
    }

    /** Резерв первого хода: счётная реакция даёт биржевую причину. */
    @Test
    @DisplayName("U4.7 — первый ход, ступеней нет, реакция счётная: резерв даёт биржевую причину")
    void u4_7_theFirstMoveFallsBackToTheAccountScopeReason() {
        assertThat(resolver.resolveForFirstMove(List.of(DEAL), HoldScope.EXCHANGE_ACCOUNT))
                .containsEntry(DEAL, Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** Тот же резерв инструментной реакции. */
    @Test
    @DisplayName("U4.8 — первый ход, ступеней нет, реакция инструментная: резерв даёт причину риск-политики")
    void u4_8_theFirstMoveFallsBackToThePairScopeReason() {
        assertThat(resolver.resolveForFirstMove(List.of(DEAL), HoldScope.INSTRUMENT))
                .containsEntry(DEAL, Deal.ShutdownReason.RISK_POLICY);
    }

    /** Стоящая ступень старше резерва. */
    @Test
    @DisplayName("U4.9 — первый ход, стои́т счётная ступень, реакция инструментная: причина биржевая")
    void u4_9_theStandingRungOutranksTheFallback() {
        standing(List.of(DEAL), List.of());

        assertThat(resolver.resolveForFirstMove(List.of(DEAL), HoldScope.INSTRUMENT))
                .containsEntry(DEAL, Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** То же в обратную сторону: резерв доходит только до сделки без единой ступени. */
    @Test
    @DisplayName("U4.10 — первый ход, стои́т ступень пары, реакция счётная: причина риск-политики")
    void u4_10_theStandingPairRungOutranksTheAccountFallback() {
        standing(List.of(), List.of(DEAL));

        assertThat(resolver.resolveForFirstMove(List.of(DEAL), HoldScope.EXCHANGE_ACCOUNT))
                .containsEntry(DEAL, Deal.ShutdownReason.RISK_POLICY);
    }

    /** У каждой сделки своя причина: у одних по стоящей, у других по радиусу реакции. */
    @Test
    @DisplayName("U4.11 — первый ход, часть сделок под ступенью, часть без: у каждой своя причина")
    void u4_11_eachDealGetsItsOwnReason() {
        standing(List.of(DEAL), List.of());

        Map<Long, Deal.ShutdownReason> resolved =
                resolver.resolveForFirstMove(List.of(DEAL, OTHER_DEAL), HoldScope.INSTRUMENT);

        assertThat(resolved).containsEntry(DEAL, Deal.ShutdownReason.EXCHANGE_HOLD);
        assertThat(resolved).containsEntry(OTHER_DEAL, Deal.ShutdownReason.RISK_POLICY);
    }

    /** У шага прохода резерва нет по построению: сделка без ступеней им пропускается. */
    @Test
    @DisplayName("U4.12 — шаг прохода на сделке без ступеней: резерва нет, сделка в раскладке отсутствует")
    void u4_12_thePassStepHasNoFallback() {
        assertThat(resolver.resolveByStandingRung(List.of(DEAL, OTHER_DEAL))).isEmpty();
    }
}
