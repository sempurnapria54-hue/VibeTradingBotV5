package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.FEE;
import static com.example.tradingcore.unit.risk.RiskFixture.NEIGHBOUR_TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.weakeningAction;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Уровень защиты после акта: три ветви — группа {@code U13} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина {@code stopPriceAfterAct};
 * контракт операнда — там же, величина {@code stopPriceIsAfterAct}).
 *
 * <p><b>Живое слагаемое наблюдается ОДНОВРЕМЕННЫМ потолком стратегии,</b>
 * и процент его задаётся клеткой: прочие числа группы подняты так, что к
 * границе не подходит ни одно. Эпизод несёт десять контрактов по цене
 * 3000, действующий уровень сделки — 2910, то есть живое слагаемое при
 * нём равно {@code 92.955 × 10 × 0.1 = 92.955}, а при уровне акта 2800 —
 * {@code 202.9}.
 */
class StopLevelAfterActTest {

    /** Уровень, который ставит сам акт: живое слагаемое при нём — 202.9. */
    private static final String ACT_STOP = "2800";

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenRoomyNeighbouringCeilings() {
        harness.givenAppetite(appetite("10", 3));
    }

    @Test
    @DisplayName("U13.1 — акт ставит свой уровень: действующий уровень сделки в расчёт не входит")
    void u13_1_theActLevelWinsOverTheStandingDealLevel() {
        DealContext dealContext = context(dealWith(episode("10", ANCHOR), tranche(List.of(),
                List.of(protection(STOP.toPlainString())))), "3.5");

        assertThat(codes(harness.validate(entryAction("10", ANCHOR, ACT_STOP), dealContext)))
                .as("202.9 + 202.9 перебирает потолок 350; по уровню 2910 вышло бы 295.855")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("U13.2 — акт уровня не касается: слагаемое посчитано по действующему уровню сделки")
    void u13_2_withoutAnActLevelTheStandingDealLevelIsUsed() {
        Deal deal = dealWith(episode("10", ANCHOR), tranche(List.of(), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.92955"))))
                .as("потолок, равный 92.955, живым слагаемым не перебирается")
                .isEmpty();
        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.9295"))))
                .as("потолок на волос ниже — и то же слагаемое его перебирает")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("U13.3 — акт снимает защиту, уровня после акта не остаётся: обе редакции не считаются")
    void u13_3_withoutAnyLevelAfterTheActTheValueFailsToCompute() {
        Deal deal = dealWith(episode("10", ANCHOR), tranche(List.of(), List.of()));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.0001"))))
                .containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U13.4 — тот же акт, но эпизода нет: уровень не нужен, слагаемое ноль")
    void u13_4_withoutAnEpisodeTheMissingLevelIsHarmless() {
        Deal deal = dealWith(null, tranche(List.of(), List.of()));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "10")))).isEmpty();
    }

    @Test
    @DisplayName("U13.5 — эпизод жив, внешний размер ноль: слагаемое ноль, отказа нет")
    void u13_5_aZeroSizedEpisodeContributesNothing() {
        Deal deal = dealWith(episode("0", ANCHOR), tranche(List.of(), List.of()));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "10")))).isEmpty();
    }

    @Test
    @DisplayName("U13.6 — уровень после акта есть, якорь пуст: величина отказывает вычислением")
    void u13_6_anEmptyAnchorMakesTheValueFail() {
        Deal deal = dealWith(episode("10", null), tranche(List.of(), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "10"))))
                .containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U13.7 — стоимость контракта пуста: отказ «правила не материализованы» до потолков")
    void u13_7_anAbsentContractValueMakesTheValueFail() {
        harness.givenRules(rules(null, "1", "1", FEE));
        Deal deal = dealWith(episode("10", ANCHOR), tranche(List.of(), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "10"))))
                .containsExactly(RiskCheckCode.INSTRUMENT_RULES_MISSING);
    }

    @Test
    @DisplayName("U13.8 — ставка пуста: тот же отказ, и параллельно отказ по ставке")
    void u13_8_anAbsentFeeRateMakesTheValueFailAlongsideItsOwnCode() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", null));
        Deal deal = dealWith(episode("10", ANCHOR), tranche(List.of(), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "10"))))
                .containsExactly(RiskCheckCode.FEE_RATE_UNAVAILABLE,
                        RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U13.9 — уровень после акта за безубытком: клэмп стои́т на СВОЁМ слагаемом")
    void u13_9_aLevelBeyondBreakevenZeroesItsOwnSummandOnly() {
        Deal deal = dealWith(episode("10", ANCHOR),
                tranche(List.of(liveLeg("100", "100", "0")), List.of(protection("3100"))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.5"))))
                .as("доля живой ноги 100 остаётся против потолка 50; без клэмпа сумма была бы 3.05")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("U13.10 — неисполненная доля живой ноги: операнд — сумма обоих слагаемых")
    void u13_10_theUnfilledLegShareAddsToTheEpisodeSummand() {
        Deal deal = dealWith(episode("10", ANCHOR),
                tranche(List.of(liveLeg("100", "100", "30")), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "1.62955"))))
                .as("70 + 92.955 = 162.955 ровно в потолок")
                .isEmpty();
        assertThat(codes(harness.validate(weakeningAction(), context(deal, "1.6295"))))
                .as("потолок на волос ниже — и та же сумма его перебирает")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("U13.11 — плановый размер живой ноги ноль: вклад ноль, деления нет")
    void u13_11_aZeroPlannedSizeContributesNothingAndDividesByNothing() {
        Deal deal = dealWith(episode("10", ANCHOR),
                tranche(List.of(liveLeg("100", "0", "0")), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.92955")))).isEmpty();
    }

    @Test
    @DisplayName("U13.12 — нога помечена «только уменьшает позицию»: в сумму она не входит")
    void u13_12_aPositionReducingLegStaysOutOfTheSum() {
        Order reducing = liveLeg("100", "100", "0");
        reducing.setPositionReducingOnly(true);
        Deal deal = dealWith(episode("10", ANCHOR),
                tranche(List.of(reducing), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.92955")))).isEmpty();
    }

    @Test
    @DisplayName("U13.13 — нога не живая (снята): в сумму не входит")
    void u13_13_aCanceledLegStaysOutOfTheSum() {
        Order canceled = entryLeg(Order.Status.CANCELED, "100", "100", "0");
        Deal deal = dealWith(episode("10", ANCHOR),
                tranche(List.of(canceled), List.of(protection(STOP.toPlainString()))));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "0.92955")))).isEmpty();
    }

    @Test
    @DisplayName("U13.14 — ноги на РАЗНЫХ траншах: потолки агрегатные, транши их не множат")
    void u13_14_legsOfEveryTrancheEnterTheSum() {
        DealTranche first = tranche(TRANCHE_ID, List.of(liveLeg("100", "100", "30")),
                List.of(protection(STOP.toPlainString())));
        DealTranche second = tranche(NEIGHBOUR_TRANCHE_ID,
                List.of(entryLeg(NEIGHBOUR_TRANCHE_ID, Order.Status.ACTIVE, "100", "100", "30")), List.of());
        Deal deal = deal(BigDecimal.ZERO, null);
        deal.setPositions(List.of(episode("10", ANCHOR)));
        deal.setTranches(List.of(first, second));

        assertThat(codes(harness.validate(weakeningAction(), context(deal, "1.62955"))))
                .as("70 + 70 + 92.955 перебирает потолок, в который одна нога укладывалась ровно")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    /** Живая входная нога транша базовой сборки. */
    private static Order liveLeg(String plannedRisk, String plannedSize, String filled) {
        return entryLeg(Order.Status.ACTIVE, plannedRisk, plannedSize, filled);
    }

    /** Сделка базовой сборки с названными эпизодом и траншем. */
    private static Deal dealWith(Position episode, DealTranche tranche) {
        Deal deal = deal(BigDecimal.ZERO, null);
        deal.setPositions(episode == null ? List.of() : List.of(episode));
        deal.setTranches(List.of(tranche));
        return deal;
    }

    /** Контекст группы: процент одновременного риска стратегии задаётся клеткой. */
    private static DealContext context(Deal deal, String strategySimultaneousPercent) {
        return contextBuilder(deal)
                .strategyDetail(detail("10", "3", strategySimultaneousPercent, "300"))
                .build();
    }
}
