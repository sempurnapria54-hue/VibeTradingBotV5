package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.account;
import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.episodeWithLiquidation;
import static com.example.tradingcore.unit.risk.RiskFixture.pairStateWithLeverage;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.specTiers;
import static com.example.tradingcore.unit.risk.RiskFixture.tier;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.workingRules;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.PositionTier;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Оценка ликвидации до входа — группа {@code U33} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом — docs/rules/risk-policy.md,
 * правило о ликвидации до входа; форма и примеры — docs/spec/risk-limits.json,
 * цепочка {@code legUnfilledContracts} … {@code entryStopBeforeLiquidation}).
 *
 * <p><b>Числа клеток — числа примеров спеки:</b> стоимость контракта 0.1,
 * ставка 0.0005, тиры до 1000 контрактов — 0.004, от 1000 до 5000 — 0.006.
 * Плечо пары задаётся клеткой.
 *
 * <p><b>Прочие неравенства разведены с предметом группы</b> — база счёта
 * миллион, проценты стратегии и риск-аппетита по десять: крупные позиции
 * примеров иначе перебирали бы потолки, и их коды подмешивались бы к коду
 * ликвидации. Снимка средств у клеток нет — проверки средств не меряются.
 */
class EntryLiquidationEstimateTest {

    /** Уровень остановки длинного входа: 0.6 % от цены 3000. */
    private static final String LONG_STOP = "2982";

    /** Уровень остановки короткого входа: 0.6 % от цены 3000. */
    private static final String SHORT_STOP = "3018";

    /** Размер входа примеров спеки в контрактах. */
    private static final String SIZE = "10";

    /** Код отказа группы. */
    private static final RiskCheckCode LIQUIDATION = RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION;

    /** Пояснение отказа по НЕИЗМЕРЕННОЙ оценке — отличает его от нарушения. */
    private static final String NOT_MEASURED = "not measured";

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenSpecTiersAndRoomyCeilings() {
        harness.givenRules(rulesWithTiers(specTiers()));
        harness.givenAppetite(appetite("10", 3));
    }

    @Test
    @DisplayName("U33.1 — первый вход LONG при плече 10: стоп в 0.6 % лежит до оценки ликвидации")
    void u33_1_aLongEntryAtLeverageTenPasses() {
        harness.givenPairState(pairStateWithLeverage(10));

        assertThat(codes(harness.validate(entry(SIZE, "3000", LONG_STOP),
                roomy(emptyDeal(StrategyTradeDirection.LONG)))))
                .as("оценка 2713.7 — дальше стопа 2982 на порядок")
                .isEmpty();
    }

    @Test
    @DisplayName("U33.2 — тот же вход при плече 100: ликвидация раньше стопа — отказ")
    void u33_2_aLongEntryAtLeverageHundredIsRejected() {
        harness.givenPairState(pairStateWithLeverage(100));

        RiskValidationResult result = harness.validate(entry(SIZE, "3000", LONG_STOP),
                roomy(emptyDeal(StrategyTradeDirection.LONG)));

        assertThat(codes(result)).as("оценка 2984.93 выше стопа 2982").containsExactly(LIQUIDATION);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo(LONG_STOP);
        assertThat(result.getChecks().getFirst().getComment()).doesNotContain(NOT_MEASURED);
    }

    @Test
    @DisplayName("U33.3 — SHORT при плече 100: стоп выше оценки ликвидации — отказ")
    void u33_3_aShortEntryAtLeverageHundredIsRejected() {
        harness.givenPairState(pairStateWithLeverage(100));

        assertThat(codes(harness.validate(entry(SIZE, "3000", SHORT_STOP),
                roomy(emptyDeal(StrategyTradeDirection.SHORT)))))
                .as("оценка 3014.93 ниже стопа 3018")
                .containsExactly(LIQUIDATION);
    }

    @Test
    @DisplayName("U33.4 — SHORT при плече 10: стоп ниже оценки ликвидации")
    void u33_4_aShortEntryAtLeverageTenPasses() {
        harness.givenPairState(pairStateWithLeverage(10));

        assertThat(codes(harness.validate(entry(SIZE, "3000", SHORT_STOP),
                roomy(emptyDeal(StrategyTradeDirection.SHORT)))))
                .as("оценка 3283.7 выше стопа 3018")
                .isEmpty();
    }

    @Test
    @DisplayName("U33.5 — добор уводит позицию во второй тир: по своей ставке — отказ")
    void u33_5_anAddIntoTheSecondTierIsRejectedAtItsOwnRate() {
        harness.givenPairState(pairStateWithLeverage(20));

        RiskValidationResult result = harness.validate(entry(SIZE, "2950", "2866"), roomy(secondTierDeal()));

        assertThat(codes(result))
                .as("позиция 1012 контрактов, ставка 0.006, оценка 2869.6 выше ближайшего стопа 2866")
                .containsExactly(LIQUIDATION);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo("2866");
    }

    @Test
    @DisplayName("U33.6 — тот же добор, если бы ставка первого тира шла дальше: оценка 2863.9, отказа нет")
    void u33_6_theSameAddAtTheFirstTierRatePasses() {
        harness.givenRules(rulesWithTiers(new ArrayList<>(List.of(tier("0", "5000", "0.004")))));
        harness.givenPairState(pairStateWithLeverage(20));

        assertThat(codes(harness.validate(entry(SIZE, "2950", "2866"), roomy(secondTierDeal()))))
                .as("контроль U33.5: различает тир позиции после акта от тира одного акта")
                .isEmpty();
    }

    @Test
    @DisplayName("U33.7 — тиры не материализованы: оценка не измерена, и это отказ, а не проход")
    void u33_7_anEntryWithoutTiersIsRejectedAsUnmeasured() {
        harness.givenPairState(pairStateWithLeverage(10));
        harness.givenRules(rulesWithTiers(new ArrayList<>()));

        RiskValidationResult emptyTiers = harness.validate(entry(SIZE, "3000", LONG_STOP),
                roomy(emptyDeal(StrategyTradeDirection.LONG)));

        assertThat(codes(emptyTiers)).as("перечень тиров пуст").containsExactly(LIQUIDATION);
        assertThat(emptyTiers.getChecks().getFirst().getComment()).contains(NOT_MEASURED);

        harness.givenRules(rulesWithTiers(null));

        assertThat(codes(harness.validate(entry(SIZE, "3000", LONG_STOP),
                roomy(emptyDeal(StrategyTradeDirection.LONG)))))
                .as("ключа тиров в навесе нет вовсе")
                .containsExactly(LIQUIDATION);
    }

    @Test
    @DisplayName("U33.8 — позиция выше последнего тира: оценка не измерена — отказ")
    void u33_8_aPositionAboveTheLastTierIsRejectedAsUnmeasured() {
        harness.givenPairState(pairStateWithLeverage(10));

        RiskValidationResult result = harness.validate(entry("6000", "3000", LONG_STOP),
                roomy(emptyDeal(StrategyTradeDirection.LONG)));

        assertThat(codes(result)).as("ставка первого тира вместо неизвестной не подставляется")
                .containsExactly(LIQUIDATION);
        assertThat(result.getChecks().getFirst().getComment()).contains(NOT_MEASURED);
    }

    @Test
    @DisplayName("U33.9 — стык тиров: позиция на общей границе берёт большую ставку")
    void u33_9_aPositionOnTheTierBoundaryTakesTheLargerRate() {
        harness.givenPairState(pairStateWithLeverage(10));
        Deal deal = emptyDeal(StrategyTradeDirection.LONG);

        assertThat(codes(harness.validate(entry("1000", "3000", "2716"), roomy(deal))))
                .as("ставка 0.006 даёт оценку 2719.2 — выше стопа 2716")
                .containsExactly(LIQUIDATION);
        assertThat(codes(harness.validate(entry("999", "3000", "2716"), roomy(deal))))
                .as("контракт внутрь первого тира: ставка 0.004, оценка 2713.7 ниже стопа")
                .isEmpty();
    }

    @Test
    @DisplayName("U33.10 — добор к позиции, чья маржа убыла: цена ликвидации площадки строже оценки — отказ")
    void u33_10_theVenueLiquidationPriceTighterThanTheEstimateRejectsTheAdd() {
        harness.givenPairState(pairStateWithLeverage(10));

        assertThat(codes(harness.validate(entry(SIZE, "3000", "2740"),
                roomy(liveDeal(StrategyTradeDirection.LONG, episodeWithLiquidation("10", ANCHOR, "2750"), "2745")))))
                .as("оценка 2713.7 пропустила бы стоп 2740, площадка называет 2750")
                .containsExactly(LIQUIDATION);
        assertThat(codes(harness.validate(entry(SIZE, "3000", "2740"),
                roomy(liveDeal(StrategyTradeDirection.LONG, episode("10", ANCHOR), "2745")))))
                .as("цена площадки не наблюдена — граница есть оценка")
                .isEmpty();
    }

    @Test
    @DisplayName("U33.11 — SHORT добор: граница берёт нижнюю из оценки и цены площадки")
    void u33_11_aShortAddTakesTheLowerOfTheEstimateAndTheVenuePrice() {
        harness.givenPairState(pairStateWithLeverage(10));

        assertThat(codes(harness.validate(entry(SIZE, "3000", "3260"),
                roomy(liveDeal(StrategyTradeDirection.SHORT, episodeWithLiquidation("10", ANCHOR, "3250"), "3255")))))
                .as("оценка 3283.7, площадка 3250; ближайший стоп 3260 за границей")
                .containsExactly(LIQUIDATION);
    }

    @Test
    @DisplayName("U33.12 — живой эпизод без действующего уровня на всю позицию: уровень акта не подставляется")
    void u33_12_aLiveEpisodeWithoutAStandingLevelLeavesTheEstimateUnmeasured() {
        harness.givenPairState(pairStateWithLeverage(10));
        Deal deal = emptyDeal(StrategyTradeDirection.LONG);
        deal.setPositions(List.of(episode("30", ANCHOR)));

        RiskValidationResult result = harness.validate(entry(SIZE, "3000", LONG_STOP), roomy(deal));

        assertThat(codes(result)).containsExactly(LIQUIDATION);
        assertThat(result.getChecks().getFirst().getComment()).contains(NOT_MEASURED);
    }

    @Test
    @DisplayName("U33.13 — акт, риска не создающий, под оценку не подпадает — даже без тиров")
    void u33_13_aNonRiskCreatingActIsNotEstimated() {
        harness.givenRules(rulesWithTiers(new ArrayList<>()));

        assertThat(codes(harness.validate(protectionAction(LONG_STOP),
                roomy(emptyDeal(StrategyTradeDirection.LONG)))))
                .isEmpty();
    }

    /** Правила рабочего инструмента с названными тирами; пусто — тиры не материализованы. */
    private static InstrumentExternalRules rulesWithTiers(List<PositionTier> tiers) {
        InstrumentExternalRules rules = workingRules();
        rules.setPositionTiers(tiers);
        return rules;
    }

    /** Вход с названными размером, плановой ценой и уровнем остановки убытка. */
    private static CalculatedStrategyAction entry(String size, String price, String stop) {
        return entryAction(size, new BigDecimal(price), stop);
    }

    /** Сделка без траншей и эпизодов с названным направлением. */
    private static Deal emptyDeal(StrategyTradeDirection direction) {
        Deal deal = deal(BigDecimal.ZERO, null);
        deal.setDirection(direction);
        return deal;
    }

    /** Сделка с названным живым эпизодом и защитой транша на названном уровне. */
    private static Deal liveDeal(StrategyTradeDirection direction, Position episode, String stopLevel) {
        Deal deal = emptyDeal(direction);
        deal.setPositions(List.of(episode));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(stopLevel)))));
        return deal;
    }

    /**
     * Сделка примера «добор во второй тир»: живой эпизод 995 контрактов по
     * средней 3000 с ценой ликвидации площадки 2850, живая нога входа —
     * 12 контрактов по плановой 2990, налито 5, — и защита транша на 2900.
     */
    private static Deal secondTierDeal() {
        Order leg = entryLeg(Order.Status.ACTIVE, "20", "12", "5");
        leg.setPlannedEntryPrice(new BigDecimal("2990"));
        Deal deal = emptyDeal(StrategyTradeDirection.LONG);
        deal.setPositions(List.of(episodeWithLiquidation("995", ANCHOR, "2850")));
        deal.setTranches(List.of(tranche(List.of(leg), List.of(protection("2900")))));
        return deal;
    }

    /** Контекст с просторными потолками: база миллион, проценты стратегии по десять. */
    private static DealContext roomy(Deal deal) {
        return contextBuilder(deal)
                .exchangeAccount(account("1000000"))
                .strategyDetail(detail("10", "3", "10", "300"))
                .build();
    }
}
