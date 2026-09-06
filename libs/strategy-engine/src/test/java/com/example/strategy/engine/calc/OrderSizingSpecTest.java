package com.example.strategy.engine.calc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Размер действия — исполнимая форма docs/spec/order-sizing.json. Каждый
 * тест назван примером спеки, из которого взяты и состояние, и ожидание:
 * девятнадцать её примеров суть готовые кейсы, и размер строится ОТ НЕЁ, а
 * не портируется из донора.
 *
 * <p><b>Состояние собирается настоящими полями</b> — правилами
 * инструмента, объявлением действия, траншем с наливом, — а не
 * подменёнными предикатами: подменённый предикат проверял бы модель,
 * которой в проде не существует (.claude/rules/codestyle.md §«Тесты
 * доменных моделей»). Экспозиция транша поэтому не подставляется числом, а
 * вытекает из его налива.
 */
class OrderSizingSpecTest {

    private static final BigDecimal ANCHOR = new BigDecimal("3000");
    private static final String FEE_RATE = "0.0005";

    private final SizeCalculator calculator = new SizeCalculator();

    // --- вход: который из двух пределов связывает размер -----------------

    /** Пример «широкий стоп: размер связывает потолок риска». */
    @Test
    void wideStopBindsTheRiskCeiling() {
        CalculatedSize size = calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", FEE_RATE), "1"),
                entryPrice(ANCHOR, "2910"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
        assertThat(size.getSizeMode()).isEqualTo(SizeMode.OPEN_OR_INCREASE);
        assertThat(size.getNotionalUsdt()).isEqualByComparingTo("3000");
    }

    /** Пример «граница связывания сверху: стоп чуть ШИРЕ переломной дистанции». */
    @Test
    void stopJustWiderThanTheBindingDistanceBindsTheRiskCeiling() {
        CalculatedSize size = calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", FEE_RATE), "1"),
                entryPrice(ANCHOR, "2972"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("32");
    }

    /** Пример «граница связывания снизу: стоп чуть ТЕСНЕЕ переломной дистанции». */
    @Test
    void stopJustTighterThanTheBindingDistanceBindsTheAllocationShare() {
        CalculatedSize size = calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", FEE_RATE), "1"),
                entryPrice(ANCHOR, "2974"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("33");
    }

    /**
     * Пример «рост ставки комиссии СНИЖАЕТ переломную дистанцию»: при
     * вдвое большей ставке тот же стоп связывает уже потолок риска.
     */
    @Test
    void higherFeeRateMovesTheBindingDistanceDown() {
        CalculatedSize size = calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", "0.001"), "1"),
                entryPrice(ANCHOR, "2974"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("31");
    }

    /**
     * Пример «узкий стоп: связывает аллокация, риск на действие остаётся
     * НИЖЕ объявленного потолка» — штатное следствие, не дефект сайзинга.
     */
    @Test
    void tightStopLeavesTheActualRiskBelowTheCeiling() {
        CalculatedSize size = calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", FEE_RATE), "1"),
                entryPrice(ANCHOR, "2997"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("33");
    }

    /**
     * Пример «размер упёрся в минимум и всё равно выходит за потолок»:
     * калькулятор возвращает минимум, а вход блокирует преконтроль — он
     * сам действие не отменяет.
     */
    @Test
    void sizeFloorsAtTheMinimumEvenWhenItBreachesTheCeiling() {
        CalculatedSize size = calculator.calculate(
                entryContext("100", rules("1", "1", "5", FEE_RATE), "1"),
                entryPrice(ANCHOR, "2910"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("5");
    }

    /** Пример «иная база и иная доля аллокации»: оба числа считаются, а не берутся константами. */
    @Test
    void anotherBaseAndAnotherAllocationShareAreComputedNotAssumed() {
        CalculationContext context = entryContext("20000", rules("0.1", "1", "1", FEE_RATE), "2");
        ((StrategyOrderAction) context.getAction()).setAllocationPercents(new BigDecimal("60"));

        CalculatedSize size = calculator.calculate(context, entryPrice(ANCHOR, "2910"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("40");
    }

    // --- вход: охраны знаменателя ----------------------------------------

    /** Пример «уровень остановки на прибыльной стороне: сайзинг отказывает». */
    @Test
    void stopOnTheProfitSideRefusesInsteadOfFlooringAtTheMinimumLot() {
        assertThatThrownBy(() -> calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", FEE_RATE), "1"),
                entryPrice(ANCHOR, "3100")))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING");
    }

    /** Пример «уровень ровно на себестоимости при нулевой ставке: тот же отказ, а не деление на ноль». */
    @Test
    void stopExactlyAtTheAnchorWithZeroFeeRefusesInsteadOfDividingByZero() {
        assertThatThrownBy(() -> calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", "0"), "1"),
                entryPrice(ANCHOR, "3000")))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING");
    }

    /** Пример «пустая ставка комиссии отказывает, а не считается нулём». */
    @Test
    void absentFeeRateRefusesInsteadOfBeingReadAsZero() {
        assertThatThrownBy(() -> calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", null), "1"),
                entryPrice(ANCHOR, "2910")))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("FEE_RATE_UNAVAILABLE");
    }

    /**
     * Вход без объявленного уровня остановки убытка не сайзится долей
     * аллокации: он состоялся бы без известного worst-case выхода
     * (docs/concept.md, П1 следствие 1). Донорский слой здесь возвращал
     * размер по аллокации — это и есть правка, ради которой размер
     * строится от спеки.
     */
    @Test
    void entryWithoutAStopLevelRefusesInsteadOfSizingByAllocationAlone() {
        assertThatThrownBy(() -> calculator.calculate(
                entryContext("10000", rules("0.1", "1", "1", FEE_RATE), "1"),
                CalculatedPrice.builder().roundedPrice(ANCHOR).build()))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("MISSING_STOP_PRICE_FOR_SIZING");
    }

    // --- reduce-only выход: четыре исхода округления ----------------------

    /** Пример «частичный выход объявленной доли проходит штатно». */
    @Test
    void declaredPartialExitGoesThroughAsPartial() {
        CalculatedSize size = calculator.calculate(
                exitContext("10", rules("0.1", "1", "1", FEE_RATE), "50"), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.PARTIAL);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("5");
    }

    /** Пример «объявленная доля забрала экспозицию целиком — полный выход по доле». */
    @Test
    void declaredShareTakingTheWholeExposureIsFullByFraction() {
        CalculatedSize size = calculator.calculate(
                exitContext("10", rules("0.1", "1", "1", FEE_RATE), "100"), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL_BY_FRACTION);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    /** Пример «доля меньше минимума, и остаток тоже меньше — выходим целиком и объявляем это». */
    @Test
    void bothShareAndRemainderBelowTheMinimumExitInFull() {
        CalculatedSize size = calculator.calculate(
                exitContext("6", rules("0.1", "1", "5", FEE_RATE), "50"), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("6");
    }

    /** Пример «доля исполнима, а остаток ниже минимума — выходим целиком». */
    @Test
    void viableShareWithADeadRemainderExitsInFull() {
        CalculatedSize size = calculator.calculate(
                exitContext("6", rules("0.1", "1", "5", FEE_RATE), "85"), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("6");
    }

    /**
     * Пример «экспозиция ниже минимального размера, объявленная доля
     * забирает её целиком»: округлять нечего, и признак полного выхода по
     * доле от выразимости размера не зависит.
     */
    @Test
    void exposureBelowTheMinimumTakenWholeIsStillFullByFraction() {
        CalculatedSize size = calculator.calculate(
                exitContext("3", rules("0.1", "1", "5", FEE_RATE), "100"), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL_BY_FRACTION);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("3");
    }

    /** Пример «доля меньше минимума, остаток достаточен — действие не исполняется». */
    @Test
    void shareBelowTheMinimumWithAViableRemainderIsSkipped() {
        CalculatedSize size = calculator.calculate(
                exitContext("100", rules("0.1", "1", "5", FEE_RATE), "2"), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.SKIPPED);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("0");
    }

    /**
     * <b>Пола минимального размера у выхода нет.</b> Подъём размера до
     * минимума менял бы объявленное действие: система сняла бы риск,
     * который стратегия снимать не объявляла, и ни одна проверка этого не
     * выражает. Донор поднимал — здесь на том же состоянии выход
     * пропускается.
     */
    @Test
    void exitSizeIsNeverRaisedToTheMinimum() {
        CalculatedSize size = calculator.calculate(
                exitContext("100", rules("0.1", "1", "5", FEE_RATE), "2"), null);

        assertThat(size.getSizeContracts()).isNotEqualByComparingTo("5");
    }

    // --- защитная лестница -----------------------------------------------

    /** Пример «последняя ступень лестницы забирает остаток округления». */
    @Test
    void lastLadderStepTakesTheRoundingRemainder() {
        CalculatedSize size = calculator.calculate(
                ladderContext("103", rules("0.1", "1", "1", FEE_RATE), "25", "75"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("28");
    }

    /** Пример «сетка: ступень транша считается от ЕГО экспозиции». */
    @Test
    void ladderStepIsTakenFromTheTrancheExposure() {
        CalculatedSize size = calculator.calculate(
                ladderContext("100", rules("0.1", "1", "1", FEE_RATE), "25", "75"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("25");
    }

    /** Пример «ступень лестницы меньше минимального размера — отказ шага». */
    @Test
    void ladderStepBelowTheMinimumIsAnExplicitRefusal() {
        assertThatThrownBy(() -> calculator.calculate(
                ladderContext("100", rules("0.1", "1", "5", FEE_RATE), "3", "9"), null))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("PROTECTION_LADDER_STEP_BELOW_MIN_SIZE");
    }

    /**
     * Лестница из нескольких ступеней с непредъявленной суммой уже
     * поставленных ОТКАЗЫВАЕТ: пустота, прочитанная нулём, отдала бы
     * последней ступени всю экспозицию — то есть покрыла бы чужие доли
     * (docs/rules/absent-value-semantics.md).
     */
    @Test
    void ladderWithoutThePlacedTotalRefusesInsteadOfReadingItAsZero() {
        CalculationContext context = ladderContext("100", rules("0.1", "1", "1", FEE_RATE), "25", null);

        assertThatThrownBy(() -> calculator.calculate(context, null))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("LADDER_PREVIOUS_STEPS_UNKNOWN");
    }

    /** Набор из одной ступени сумму поставленных не требует — до неё ставить было нечего. */
    @Test
    void singleStepSetNeedsNoPlacedTotal() {
        CalculationContext context = singleProtectionContext("40", rules("0.1", "1", "1", FEE_RATE));

        CalculatedSize size = calculator.calculate(context, null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("40");
        assertThat(size.getExitOutcome()).isNull();
    }

    // --- сборка состояния -------------------------------------------------

    private static InstrumentExternalRules rules(String contractValue, String lotSize, String minSize,
                                                 String feeRate) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalContractValue(contractValue);
        rules.setExternalLotSize(lotSize);
        rules.setExternalMinSize(minSize);
        rules.setExternalTakerFeeRate(feeRate);
        rules.setExternalTickSize("0.1");
        return rules;
    }

    private static CalculatedPrice entryPrice(BigDecimal anchor, String stopPrice) {
        return CalculatedPrice.builder()
                .purpose(StrategyPricePurpose.ORDER_LIMIT_PRICE)
                .priceMode(PriceMode.EXPLICIT)
                .roundedPrice(anchor)
                .stopLossPrice(ResolvedStopLossPrice.builder()
                        .triggerPrice(new BigDecimal(stopPrice))
                        .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                        .build())
                .build();
    }

    private static CalculationContext entryContext(String riskBase, InstrumentExternalRules rules,
                                                   String riskPerActionPercent) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(1L);
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setAllocationPercents(new BigDecimal("100"));
        return baseContext(action, rules)
                .riskBase(new BigDecimal(riskBase))
                .strategyDetail(detail(riskPerActionPercent, List.of(action)))
                .build();
    }

    private static CalculationContext exitContext(String exposure, InstrumentExternalRules rules,
                                                  String closeFractionPercents) {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.PARTIAL_TAKE_PROFIT,
                closeFractionPercents);
        return baseContext(action, rules)
                .dealTranche(tranche(exposure))
                .strategyDetail(detail("1", List.of(action)))
                .build();
    }

    /**
     * Лестница из четырёх ступеней, где рассчитываемая — последняя по
     * идентификатору: селектор объявлен домом правила
     * (docs/rules/live-risk-protection.md), и тест собирает набор целиком,
     * а не подменяет признак «последняя».
     */
    private static CalculationContext ladderContext(String exposure, InstrumentExternalRules rules,
                                                    String stepPercents, String previousStepsTotal) {
        List<StrategyAction> ladder = new ArrayList<>();
        for (long index = 1; index <= 4; index++) {
            ladder.add(algoAction(index, AlgoOrder.ConditionType.PARTIAL_STOP_LOSS, stepPercents));
        }
        StrategyAlgoOrderAction last = (StrategyAlgoOrderAction) ladder.get(ladder.size() - 1);
        return baseContext(last, rules)
                .dealTranche(tranche(exposure))
                .strategyDetail(detail("1", ladder))
                .ladderPreviousStepsTotal(isNull(previousStepsTotal)
                        ? null : new BigDecimal(previousStepsTotal))
                .build();
    }

    private static CalculationContext singleProtectionContext(String exposure, InstrumentExternalRules rules) {
        StrategyAlgoOrderAction action = algoAction(1L, AlgoOrder.ConditionType.STOP_LOSS, null);
        return baseContext(action, rules)
                .dealTranche(tranche(exposure))
                .strategyDetail(detail("1", List.of(action)))
                .build();
    }

    private static CalculationContext.CalculationContextBuilder baseContext(StrategyAction action,
                                                                            InstrumentExternalRules rules) {
        return CalculationContext.builder()
                .action(action)
                .instrumentExternalRules(rules)
                .strategyDirection(StrategyTradeDirection.LONG);
    }

    private static StrategyAlgoOrderAction algoAction(Long id, AlgoOrder.ConditionType conditionType,
                                                      String closeFractionPercents) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setKey("algo-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(conditionType);
        action.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        if (nonNull(closeFractionPercents)) {
            action.setCloseFractionPercents(new BigDecimal(closeFractionPercents));
        }
        return action;
    }

    private static DealTranche tranche(String exposure) {
        DealTranche tranche = new DealTranche();
        tranche.setId(7L);
        tranche.setEntryFilled(new BigDecimal(exposure));
        return tranche;
    }

    private static StrategyDetail detail(String riskPerActionPercent, List<StrategyAction> actions) {
        StrategyStep step = new StrategyStep();
        step.setId(11L);
        step.setStepType(StrategyStepType.MAIN_PROTECTION);
        step.setActions(actions);

        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(21L);
        declaration.setKey("t1");
        declaration.setStepsByStatus(Map.of(DealTranche.Status.MANAGING, List.of(step)));

        StrategyDetail detail = new StrategyDetail();
        detail.setId(31L);
        detail.setRiskPerActionPercent(new BigDecimal(riskPerActionPercent));
        detail.setTranches(List.of(declaration));
        return detail;
    }
}
