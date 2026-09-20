package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.account;
import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codesOf;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Вторая точка входа: неравенства при НУЛЕВОМ акте — группа {@code U18}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §«Что делает»; потребитель —
 * docs/components/AnomalyJob.md §«Что ищет»).
 *
 * <p><b>Базовая сборка</b> — живая сделка: эпизод в десять контрактов по
 * цене 3000 и действующий уровень защиты 2910, то есть живой риск равен
 * 92.955, а экспозиция сделки — 3000. Граф предъявлен целиком, оба числа
 * детали объявлены, строка тенанта несёт максимальный риск на сделку.
 *
 * <p><b>Отсутствие операнда — МОЛЧАНИЕ, а не находка</b>: у клеток
 * U18.6-U18.13 пустой перечень означает «не проверялось», и отличить его
 * от «нарушений нет» второй точке входа нечем по построению — она отдаёт
 * один перечень.
 */
class CeilingsWithoutActTest {

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenAnAssignedTenantNumber() {
        harness.givenAppetite(appetite("10", 3));
    }

    @Test
    @DisplayName("U18.1 — живая сделка укладывается в потолки: перечень нарушений пуст")
    void u18_1_aLiveDealWithinTheCeilingsBreachesNothing() {
        assertThat(harness.ceilingsBreachedWithoutAct(liveContext("10", "300"))).isEmpty();
    }

    @Test
    @DisplayName("U18.2 — живой риск выше потолка стратегии: ровно одно нарушение")
    void u18_2_onlyTheStrategyEditionIsBreached() {
        assertThat(codesOf(harness.ceilingsBreachedWithoutAct(liveContext("0.9", "300"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("U18.3 — нарушены все три: перечень в порядке проверок")
    void u18_3_allThreeBreachesComeInTheOrderOfChecks() {
        harness.givenAppetite(appetite("0.9", 3));

        assertThat(codesOf(harness.ceilingsBreachedWithoutAct(liveContext("0.9", "10"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                        RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U18.4 — поактный потолок в набор не входит: его нет ни при каком входе")
    void u18_4_thePerActionCeilingIsNotInTheSet() {
        DealContext impossiblyTightPerAction = liveContextWithDetail(detail("0.0001", "3", "10", "300"));

        assertThat(codesOf(harness.ceilingsBreachedWithoutAct(impossiblyTightPerAction)))
                .doesNotContain(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                        RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET);
    }

    @Test
    @DisplayName("U18.5 — кумулятивный потолок в набор не входит: он мерит акт, которого нет")
    void u18_5_theCumulativeCeilingIsNotInTheSet() {
        Deal dealWithHugeRiskTaken = liveDeal();
        dealWithHugeRiskTaken.setPlannedRiskAmount(new BigDecimal("100000"));
        DealContext dealContext = contextBuilder(dealWithHugeRiskTaken)
                .strategyDetail(detail("10", "0.0001", "10", "300"))
                .build();

        assertThat(codesOf(harness.ceilingsBreachedWithoutAct(dealContext)))
                .doesNotContain(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
    }

    @Test
    @DisplayName("U18.6 — граф предъявлен не целиком: перечень пуст — «не проверялось»")
    void u18_6_anIncompleteGraphIsSilence() {
        DealContext dealContext = contextBuilder(liveDeal())
                .graphComplete(false)
                .strategyDetail(detail("0.9", "3", "0.9", "10"))
                .build();

        assertThat(harness.ceilingsBreachedWithoutAct(dealContext)).isEmpty();
    }

    @Test
    @DisplayName("U18.7 — детали стратегии нет: перечень пуст")
    void u18_7_anAbsentStrategyDetailIsSilence() {
        assertThat(harness.ceilingsBreachedWithoutAct(liveContextWithDetail(null))).isEmpty();
    }

    @Test
    @DisplayName("U18.8 — база риска пуста либо непозитивна: перечень пуст")
    void u18_8_anAbsentOrNonPositiveBaseIsSilence() {
        assertThat(harness.ceilingsBreachedWithoutAct(baseContext(null)))
                .as("живой базы нет вовсе")
                .isEmpty();
        assertThat(harness.ceilingsBreachedWithoutAct(baseContext("0")))
                .as("живая база непозитивна")
                .isEmpty();
    }

    @Test
    @DisplayName("U18.9 — правила инструмента не материализованы: перечень пуст")
    void u18_9_unmaterializedRulesAreSilence() {
        harness.givenRules(null);

        assertThat(harness.ceilingsBreachedWithoutAct(liveContext("0.9", "10"))).isEmpty();
    }

    @Test
    @DisplayName("U18.10 — максимальный риск на сделку не назначен: незаданное число молчит")
    void u18_10_anUnassignedTenantNumberIsSilence() {
        harness.givenAppetite(appetite(null, 3));

        assertThat(harness.ceilingsBreachedWithoutAct(liveContext("0.9", "10"))).isEmpty();
    }

    @Test
    @DisplayName("U18.11 — процент одновременного риска деталью не объявлен: перечень пуст")
    void u18_11_anUndeclaredStrategyPercentIsSilence() {
        assertThat(harness.ceilingsBreachedWithoutAct(liveContext(null, "10"))).isEmpty();
    }

    @Test
    @DisplayName("U18.12 — множитель катастрофического потолка не объявлен: перечень пуст")
    void u18_12_anUndeclaredCatastrophicMultiplierIsSilence() {
        assertThat(harness.ceilingsBreachedWithoutAct(liveContext("0.9", null))).isEmpty();
    }

    @Test
    @DisplayName("U18.13 — действующего уровня защиты нет вовсе: это ПОТЕРЯ ПОКРЫТИЯ")
    void u18_13_anAbsentProtectionLevelBelongsToAnotherTrigger() {
        Deal withoutProtection = deal(BigDecimal.ZERO, null);
        withoutProtection.setPositions(List.of(episode("10", ANCHOR)));
        withoutProtection.setTranches(List.of(tranche(List.of(), List.of())));
        DealContext dealContext = contextBuilder(withoutProtection)
                .strategyDetail(detail("0.9", "3", "0.9", "10"))
                .build();

        assertThat(harness.ceilingsBreachedWithoutAct(dealContext)).isEmpty();
    }

    @Test
    @DisplayName("U18.14 — эпизода нет, живых ног нет: оба слагаемых нули, экспозиция ноль")
    void u18_14_anEmptyDealBreachesNothing() {
        Deal withoutRisk = deal(BigDecimal.ZERO, null);
        withoutRisk.setTranches(List.of(tranche(List.of(), List.of())));
        DealContext dealContext = contextBuilder(withoutRisk)
                .strategyDetail(detail("0.0001", "3", "0.0001", "0.0001"))
                .build();

        assertThat(harness.ceilingsBreachedWithoutAct(dealContext)).isEmpty();
    }

    @Test
    @DisplayName("U18.15 — состояния пары вторая точка входа не читает: блок-сет в её набор не входит")
    void u18_15_thePairStateIsNeverRead() {
        harness.ceilingsBreachedWithoutAct(liveContext("0.9", "10"));

        verifyNoInteractions(harness.pairStateBoundary());
    }

    @Test
    @DisplayName("U18.16 — живая неисполненная нога поднимает живой риск второй точки")
    void u18_16_theUnfilledLegShareRaisesTheLiveRiskHere() {
        Deal withLiveLeg = deal(BigDecimal.ZERO, null);
        withLiveLeg.setPositions(List.of(episode("10", ANCHOR)));
        withLiveLeg.setTranches(List.of(tranche(List.of(entryLeg(Order.Status.ACTIVE, "100", "100", "30")),
                List.of(protection(STOP.toPlainString())))));

        assertThat(harness.ceilingsBreachedWithoutAct(withDetail(withLiveLeg, "1.62955")))
                .as("70 + 92.955 = 162.955 ровно в потолок")
                .isEmpty();
        assertThat(codesOf(harness.ceilingsBreachedWithoutAct(withDetail(withLiveLeg, "1.6295"))))
                .as("потолок на волос ниже — и та же сумма его перебирает")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    /** Живая сделка группы: эпизод в десять контрактов и действующий уровень 2910. */
    private static Deal liveDeal() {
        Deal deal = deal(BigDecimal.ZERO, null);
        deal.setPositions(List.of(episode("10", ANCHOR)));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));
        return deal;
    }

    /** Контекст живой сделки с названными процентом стратегии и катастрофическим множителем. */
    private static DealContext liveContext(String strategyPercent, String catastrophicMultiplier) {
        return liveContextWithDetail(detail("10", "3", strategyPercent, catastrophicMultiplier));
    }

    /** Контекст живой сделки с названной деталью стратегии. */
    private static DealContext liveContextWithDetail(StrategyDetail detail) {
        return contextBuilder(liveDeal()).strategyDetail(detail).build();
    }

    /** Контекст названной сделки с процентом одновременного риска стратегии. */
    private static DealContext withDetail(Deal deal, String strategyPercent) {
        return contextBuilder(deal).strategyDetail(detail("10", "3", strategyPercent, "300")).build();
    }

    /** Контекст живой сделки с названной живой базой счёта. */
    private static DealContext baseContext(String riskBase) {
        return contextBuilder(liveDeal())
                .exchangeAccount(account(riskBase))
                .strategyDetail(detail("0.9", "3", "0.9", "10"))
                .build();
    }
}
