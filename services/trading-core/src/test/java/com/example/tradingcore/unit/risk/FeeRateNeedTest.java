package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.actionWithUnresolvedStopTrigger;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.weakeningAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ставка комиссии: два условия нужности — группа {@code U3} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §«Ставка комиссии: два разных «нет»»).
 *
 * <p><b>Базовая сборка</b> — U1.1, но ставка комиссии у правил
 * инструмента ПУСТА: живого эпизода нет, действие уровня не касается,
 * если клетка не говорит иного.
 */
class FeeRateNeedTest {

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenAnUnresolvedFeeRate() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", null));
    }

    @Test
    @DisplayName("U3.1 — действие ставит уровень остановки убытка: ставка не резолвится")
    void u3_1_anActThatPlacesAStopLevelNeedsTheRate() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext())))
                .containsExactly(RiskCheckCode.FEE_RATE_UNAVAILABLE);
    }

    @Test
    @DisplayName("U3.2 — уровня действие не касается, эпизод жив: ставка — операнд живого слагаемого")
    void u3_2_aLiveEpisodeNeedsTheRateEvenWithoutALevel() {
        assertThat(codes(harness.validate(weakeningAction(), context(dealWithLiveEpisode()))))
                .as("живое слагаемое без ставки не считается вовсе, и его отказ идёт следом")
                .containsExactly(RiskCheckCode.FEE_RATE_UNAVAILABLE,
                        RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U3.3 — уровня нет, эпизода нет: уровневые потребители отпали, живое слагаемое ноль")
    void u3_3_withoutALevelAndWithoutAnEpisodeTheRateIsNotNeeded() {
        assertThat(codes(harness.validate(weakeningAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U3.4 — уровень объявлен, цена срабатывания пуста: уровня действие не ставит")
    void u3_4_aDeclaredStopWithoutATriggerPriceDoesNotPlaceALevel() {
        assertThat(codes(harness.validate(actionWithUnresolvedStopTrigger(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U3.5 — ставка резолвится, действие ставит уровень: отказа нет")
    void u3_5_aResolvedRateSatisfiesTheLevelConsumer() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", "0.0005"));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U3.6 — ставка резолвится нулём: ноль — значение ставки, а не её отсутствие")
    void u3_6_aZeroRateIsAValueNotAnAbsence() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", "0"));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U3.7 — ставка отрицательна (ребейт): величина едет как есть")
    void u3_7_aNegativeRebateRateTravelsAsItIs() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", "-0.0002"));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext()))).isEmpty();
    }

    /** Сделка с живым эпизодом и стоящей защитой транша: уровень после акта резолвится. */
    private static Deal dealWithLiveEpisode() {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));
        deal.setPositions(List.of(episode("1", ANCHOR)));
        return deal;
    }
}
