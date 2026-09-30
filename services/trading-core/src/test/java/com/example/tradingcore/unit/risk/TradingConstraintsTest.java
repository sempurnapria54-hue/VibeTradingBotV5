package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.FEE;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.pairState;
import static com.example.tradingcore.unit.risk.RiskFixture.priceBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static com.example.tradingcore.unit.risk.RiskFixture.takeProfitAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.transferAction;
import static com.example.tradingcore.unit.risk.RiskFixture.withPrice;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static com.example.tradingcore.unit.risk.RiskFixture.workingPairState;
import static com.example.tradingcore.unit.risk.RiskFixture.workingRules;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.PriceMode;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Торговые ограничения инструмента — группа {@code U2} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/rules/trading-constraints.md; перечень кодов —
 * docs/components/RiskValidator.md §Проверки, таблица накопления).
 *
 * <p><b>Отказы этой группы НАКАПЛИВАЮТСЯ:</b> перечень несёт все
 * сработавшие, а не первый, — поэтому каждая клетка называет перечень
 * целиком, а не наличие своего кода.
 *
 * <p><b>Базовая сборка</b> — U1.1: размер 10 контрактов при минимуме 1,
 * шаге 1 и лимитах, которых он не достаёт; плечо пары не назначено.
 *
 * <p><b>Торгуемость запирает набор риска, а не защиту</b> (клетки
 * U2.19-U2.23; дом — docs/rules/risk-validator-scope.md): область
 * проверки — блок-сет ступени, и защитное действие, уровня своего транша
 * не ослабляющее, на неторгуемом инструменте проходит.
 */
class TradingConstraintsTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U2.1 — инструмент не торгуется: остальные проверки группы отработали и молчат")
    void u2_1_aNonTradeableInstrumentIsRejected() {
        InstrumentExternalRules suspended = workingRules();
        suspended.setStatus(InstrumentExternalRules.Status.SUSPEND);
        harness.givenRules(suspended);

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.INSTRUMENT_NOT_LIVE);
    }

    @Test
    @DisplayName("U2.2 — режим маржи пары кросс: изолированность требуется контуром")
    void u2_2_aCrossMarginPairIsRejected() {
        AccountInstrumentState cross = workingPairState();
        cross.setMarginMode(Instrument.MarginMode.CROSS);
        harness.givenPairState(cross);

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED);
    }

    @Test
    @DisplayName("U2.3 — режим маржи не задан: изолированность утверждается явно")
    void u2_3_anUnsetMarginModeIsRejectedToo() {
        AccountInstrumentState unset = workingPairState();
        unset.setMarginMode(null);
        harness.givenPairState(unset);

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED);
    }

    @Test
    @DisplayName("U2.4 — размер ниже минимального размера инструмента")
    void u2_4_aSizeBelowTheInstrumentMinimumIsRejected() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "20", FEE));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.SIZE_BELOW_MIN);
    }

    @Test
    @DisplayName("U2.5 — размер РАВЕН минимальному: граница включена")
    void u2_5_aSizeExactlyAtTheMinimumPasses() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "10", FEE));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.6 — минимальный размер у правил пуст: сверять не с чем")
    void u2_6_anAbsentInstrumentMinimumRejectsNothing() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", null, FEE));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.7 — размер не кратен шагу лота")
    void u2_7_aSizeOffTheLotStepIsRejected() {
        harness.givenRules(rules(CONTRACT_VALUE, "3", "1", FEE));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.SIZE_LOT_STEP_INVALID);
    }

    @Test
    @DisplayName("U2.8 — шаг лота ноль: отказа нет, деления на ноль тоже")
    void u2_8_aZeroLotStepRejectsNothingAndDividesByNothing() {
        harness.givenRules(rules(CONTRACT_VALUE, "0", "1", FEE));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.9 — шаг лота пуст: отказа нет")
    void u2_9_anAbsentLotStepRejectsNothing() {
        harness.givenRules(rules(CONTRACT_VALUE, null, "1", FEE));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.10 — цена режима «явная»: сверка идёт с limit-лимитом, market не читается")
    void u2_10_theExplicitPriceModePicksTheLimitCap() {
        InstrumentExternalRules tightLimit = workingRules();
        tightLimit.setExternalMaxLimitSize("5");
        tightLimit.setExternalMaxMarketSize("100000");
        harness.givenRules(tightLimit);

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.SIZE_ABOVE_LIMIT);
    }

    @Test
    @DisplayName("U2.11 — цена «по рынку»: применимый лимит выбирается режимом цены")
    void u2_11_theMarketPriceModePicksTheMarketCap() {
        InstrumentExternalRules tightMarket = workingRules();
        tightMarket.setExternalMaxLimitSize("100000");
        tightMarket.setExternalMaxMarketSize("5");
        harness.givenRules(tightMarket);

        assertThat(codes(harness.validate(marketPricedEntry(PriceMode.MARKET_LIKE), workingContext())))
                .containsExactly(RiskCheckCode.SIZE_ABOVE_LIMIT);
    }

    @Test
    @DisplayName("U2.12 — режим цены не объявлен: пустой режим даёт market-лимит")
    void u2_12_anUndeclaredPriceModeFallsBackToTheMarketCap() {
        InstrumentExternalRules tightMarket = workingRules();
        tightMarket.setExternalMaxLimitSize("100000");
        tightMarket.setExternalMaxMarketSize("5");
        harness.givenRules(tightMarket);

        assertThat(codes(harness.validate(marketPricedEntry(null), workingContext())))
                .containsExactly(RiskCheckCode.SIZE_ABOVE_LIMIT);
    }

    @Test
    @DisplayName("U2.13 — плечо пары выше биржевого максимума")
    void u2_13_aLeverageAboveTheExchangeMaximumIsRejected() {
        harness.givenPairState(pairStateWithLeverage(200));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.EXCHANGE_MAX_LEVERAGE_EXCEEDED);
    }

    @Test
    @DisplayName("U2.14 — плечо пары РАВНО максимуму: граница включена")
    void u2_14_aLeverageExactlyAtTheMaximumPasses() {
        harness.givenPairState(pairStateWithLeverage(125));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.15 — плечо пары не назначено, акт создаёт риск: отказ, а не молчание")
    void u2_15_anUnassignedLeverageRejectsARiskCreatingEntry() {
        harness.givenPairState(pairStateWithLeverage(null));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.LEVERAGE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U2.18 — плечо пары не назначено, акт риска не создаёт: отказа нет")
    void u2_18_anUnassignedLeverageLeavesAProtectiveActAlone() {
        harness.givenPairState(pairStateWithLeverage(null));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.16 — биржевой максимум плеча у правил пуст: отказа нет")
    void u2_16_anAbsentExchangeMaximumRejectsNothing() {
        InstrumentExternalRules withoutMaximum = workingRules();
        withoutMaximum.setExternalMaxLeverage(null);
        harness.givenRules(withoutMaximum);
        harness.givenPairState(pairStateWithLeverage(200));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U2.17 — живость, маржа и размер сразу: три члена в порядке проверок")
    void u2_17_threeConstraintsAccumulateInTheOrderOfChecks() {
        InstrumentExternalRules suspendedWithHighMinimum = rules(CONTRACT_VALUE, "1", "20", FEE);
        suspendedWithHighMinimum.setStatus(InstrumentExternalRules.Status.SUSPEND);
        harness.givenRules(suspendedWithHighMinimum);
        AccountInstrumentState cross = workingPairState();
        cross.setMarginMode(Instrument.MarginMode.CROSS);
        harness.givenPairState(cross);

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                        RiskCheckCode.MARGIN_MODE_NOT_ISOLATED,
                        RiskCheckCode.SIZE_BELOW_MIN);
    }

    @Test
    @DisplayName("U2.19 — инструмент не торгуется, первая защита над непокрытым траншем: отказа нет")
    void u2_19_theFirstProtectionOverAnUncoveredTranchePassesOnANonTradeableInstrument() {
        harness.givenRules(suspendedRules());

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext(),
                tranche(List.of(), List.of())))).isEmpty();
    }

    @Test
    @DisplayName("U2.20 — инструмент не торгуется, перенос стопа ближе к цене, чем защита транша: отказа нет")
    void u2_20_aTighteningTransferPassesOnANonTradeableInstrument() {
        harness.givenRules(suspendedRules());

        assertThat(codes(harness.validate(transferAction("2950"), workingContext(), protectedTranche())))
                .isEmpty();
    }

    @Test
    @DisplayName("U2.21 — инструмент не торгуется, постановка уровня фиксации прибыли: отказа нет")
    void u2_21_aTakeProfitPlacementPassesOnANonTradeableInstrument() {
        harness.givenRules(suspendedRules());

        assertThat(codes(harness.validate(takeProfitAction(), workingContext(), protectedTranche()))).isEmpty();
    }

    @Test
    @DisplayName("U2.22 — инструмент не торгуется, перенос стопа дальше от цены, чем защита транша: отказ")
    void u2_22_aLooseningTransferIsRejectedOnANonTradeableInstrument() {
        harness.givenRules(suspendedRules());

        assertThat(codes(harness.validate(transferAction("2850"), workingContext(), protectedTranche())))
                .containsExactly(RiskCheckCode.INSTRUMENT_NOT_LIVE);
    }

    /** Транша у действия нет: ослабляет ли уровень защиту, спросить не у кого. */
    @Test
    @DisplayName("U2.23 — инструмент не торгуется, защита с уровнем, транш не назван: отказ")
    void u2_23_aProtectiveActWithoutItsTrancheIsRejectedOnANonTradeableInstrument() {
        harness.givenRules(suspendedRules());

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext())))
                .containsExactly(RiskCheckCode.INSTRUMENT_NOT_LIVE);
    }

    /** Правила рабочего инструмента, у которого торги приостановлены. */
    private static InstrumentExternalRules suspendedRules() {
        InstrumentExternalRules suspended = workingRules();
        suspended.setStatus(InstrumentExternalRules.Status.SUSPEND);
        return suspended;
    }

    /** Транш с одной живой отдельной защитой на уровне базовой сборки. */
    private static DealTranche protectedTranche() {
        return tranche(List.of(), List.of(protection(STOP.toPlainString())));
    }

    /** Вход базовой сборки с названным режимом рассчитанной цены. */
    private static CalculatedStrategyAction marketPricedEntry(PriceMode priceMode) {
        return withPrice(entryAction(),
                priceBuilder(ANCHOR, STOP.toPlainString()).priceMode(priceMode).build());
    }

    /** Строка пары без ступени с названным рабочим плечом. */
    private static AccountInstrumentState pairStateWithLeverage(Integer leverage) {
        AccountInstrumentState state = pairState(Instrument.SafetyRung.ACTIVE);
        state.setLeverage(leverage);
        return state;
    }
}
