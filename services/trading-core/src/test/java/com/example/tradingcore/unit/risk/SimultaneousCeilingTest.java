package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.weakeningAction;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Одновременный потолок в двух вложенных редакциях — группа {@code U14}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величины
 * {@code withinStrategySimultaneous} и {@code withinGlobalSimultaneous}).
 *
 * <p><b>Базовая сборка</b> — U13.1: эпизод в десять контрактов по цене
 * 3000, действующий уровень 2910, вход на десять контрактов с тем же
 * уровнем. Живое слагаемое равно 92.955, риск акта — столько же, сумма —
 * 185.91; процент стратегии и процент риск-аппетита задаются клеткой.
 */
class SimultaneousCeilingTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U14.1 — сумма ниже обоих потолков: отказа нет")
    void u14_1_aSumWithinBothCeilingsPasses() {
        harness.givenAppetite(appetite("10", 3));

        assertThat(codes(harness.validate(entryAction(), liveContext("10")))).isEmpty();
    }

    @Test
    @DisplayName("U14.2 — сумма РАВНА потолку стратегии: граница включена")
    void u14_2_aSumExactlyAtTheStrategyCeilingPasses() {
        harness.givenAppetite(appetite("10", 3));

        assertThat(codes(harness.validate(entryAction(), liveContext("1.8591"))))
                .as("92.955 + 92.955 = 185.91 ровно в потолок")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.3 — сумма выше потолка стратегии, но ниже глобального: ровно один отказ")
    void u14_3_onlyTheStrategyEditionBreaches() {
        harness.givenAppetite(appetite("10", 3));

        assertThat(codes(harness.validate(entryAction(), liveContext("1.859"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("U14.4 — сумма выше обоих: два отказа, стратегия первой")
    void u14_4_bothEditionsBreachInTheOrderOfChecks() {
        harness.givenAppetite(appetite("1.8", 3));

        assertThat(codes(harness.validate(entryAction(), liveContext("1.859"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                        RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED);
    }

    @Test
    @DisplayName("U14.5 — процент стратегии не объявлен: глобальная редакция всё равно считается")
    void u14_5_anUndeclaredStrategyPercentDoesNotStopTheGlobalEdition() {
        harness.givenAppetite(appetite("1.8", 3));

        assertThat(codes(harness.validate(entryAction(), liveContext(null))))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                        RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED);
    }

    @Test
    @DisplayName("U14.6 — живого риска нет вовсе: операнд — только слагаемое акта")
    void u14_6_withoutLiveRiskOnlyTheActSummandCounts() {
        harness.givenAppetite(appetite("10", 3));
        DealContext withoutLiveRisk = contextBuilder(emptyDeal())
                .strategyDetail(detail("10", "3", "1", "300"))
                .build();

        assertThat(codes(harness.validate(entryAction(), withoutLiveRisk)))
                .as("риск акта 92.955 против потолка 100")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.7 — живой риск сам выше потолка при нулевом акте: контроль срабатывает")
    void u14_7_anAlreadyTakenExposureBreachesOnItsOwn() {
        harness.givenAppetite(appetite("10", 3));

        assertThat(codes(harness.validate(weakeningAction(), liveContext("0.9"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    /** Контекст базовой сборки группы с названным процентом одновременного риска стратегии. */
    private static DealContext liveContext(String strategySimultaneousPercent) {
        Deal deal = deal(BigDecimal.ZERO, null);
        deal.setPositions(List.of(episode("10", ANCHOR)));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));
        return contextBuilder(deal)
                .strategyDetail(detail("10", "3", strategySimultaneousPercent, "300"))
                .build();
    }
}
