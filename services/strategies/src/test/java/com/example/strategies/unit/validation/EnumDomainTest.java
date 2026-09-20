package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.dealStepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.indicator;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newPlacement;
import static com.example.strategies.unit.validation.ValidationFixture.phaseRule;
import static com.example.strategies.unit.validation.ValidationFixture.protectionStep;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rules;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.structure;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionOperandApiModel;
import com.example.strategies.api.model.strategy.StrategyPricePlacementApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Перечни значений — группа {@code U29} документа
 * `.claude/tests/cases/strategy-definition-validation.md`. Единица
 * клетки здесь — ПЕРЕЧЕНЬ, а не поле: категория «значение вне домена»
 * задана перечнем, и поле, несущее тот же перечень вторым носителем,
 * входит в ту же клетку своим путём.
 *
 * <p><b>Дом группы</b> — .claude/rules/codestyle.md §«Слои моделей и
 * enum'ы»: на границе строка, в домене перечень, и сверку делает звено
 * {@code validateEnum}.
 *
 * <p><b>Пустое значение и неизвестное валидатор НЕ различает</b> — оба
 * дают одно нарушение, — и разводит их только охрана непустоты
 * вызывающего. Две последние клетки предъявляют обе стороны этого
 * различия.
 */
class EnumDomainTest {

    private static final String UNKNOWN = "unknown value";

    @Test
    @DisplayName("U29.1 — тип рыночной фазы у детали")
    void u29_1_theDetailMarketPhaseType() {
        CreateStrategyApiRequest request = reference();
        bull(request).setMarketPhaseType("SIDEWAYS");

        assertThat(matching(violations(request), "details[0].marketPhaseType: " + UNKNOWN + " SIDEWAYS"))
                .hasSize(1);
    }

    @Test
    @DisplayName("U29.2 — тип рыночной фазы у правила классификации")
    void u29_2_theClauseMarketPhaseType() {
        CreateStrategyApiRequest request = reference();
        phaseRule(request, 1).setType("SIDEWAYS");

        assertThat(matching(violations(request),
                "marketPhaseSetting.phaseRules[1].type: " + UNKNOWN + " SIDEWAYS")).hasSize(1);
    }

    @Test
    @DisplayName("U29.3 — политика входа детали")
    void u29_3_theDetailPhaseEntryPolicy() {
        CreateStrategyApiRequest request = reference();
        bull(request).setPhaseEntryPolicy("AGGRESSIVE");

        assertThat(matching(violations(request), "details[0].phaseEntryPolicy: " + UNKNOWN + " AGGRESSIVE"))
                .hasSize(1);
    }

    @Test
    @DisplayName("U29.4 — тип индикатора настройки")
    void u29_4_theIndicatorSettingType() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setIndicatorType("SUPERTREND");

        assertThat(matching(violations(request), ".indicatorType: " + UNKNOWN + " SUPERTREND")).hasSize(1);
    }

    @Test
    @DisplayName("U29.5 — назначение настройки индикатора и настройки структуры: по пути на носитель")
    void u29_5_theDestinyOfBothSettingKinds() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setDestiny("SIGNAL");
        structure(request, "phase_structure_1h").setDestiny("SIGNAL");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".destiny: " + UNKNOWN + " SIGNAL")).hasSize(2);
        assertThat(matching(violations, "strategy.indicatorSettings")).hasSize(1);
        assertThat(matching(violations, "strategy.marketStructureSettings")).hasSize(1);
    }

    @Test
    @DisplayName("U29.6 — таймфрейм: у параметров индикатора, у настройки структуры и у правила шага")
    void u29_6_theTimeframeOfThreeCarriers() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").getParams().setTimeframe("TEN_MINUTES");
        structure(request, "phase_structure_1h").setTimeframe("TEN_MINUTES");
        rules(entryStep(bull(request))).get(4).setTimeframe("TEN_MINUTES");

        assertThat(matching(violations(request), "timeframe: " + UNKNOWN + " TEN_MINUTES")).hasSize(3);
    }

    @Test
    @DisplayName("U29.7 — статус транша как ключ карты шагов объявления")
    void u29_7_theTrancheStatusMapKey() {
        CreateStrategyApiRequest request = reference();
        Map<String, List<StrategyStepApiModel>> byStatus = stepsByStatus(tranche(bull(request)));
        byStatus.put("ACTIVE", byStatus.remove("MANAGING"));

        assertThat(matching(violations(request), "stepsByStatus key: " + UNKNOWN + " ACTIVE")).hasSize(1);
    }

    @Test
    @DisplayName("U29.8 — статус сделки как ключ карты агрегатных шагов")
    void u29_8_theDealStatusMapKey() {
        CreateStrategyApiRequest request = reference();
        Map<String, List<StrategyStepApiModel>> byStatus = dealStepsByStatus(bull(request));
        byStatus.put("MANAGING", byStatus.remove("ACTIVE"));

        assertThat(matching(violations(request),
                "details[0].stepsByStatus key: " + UNKNOWN + " MANAGING")).hasSize(1);
    }

    @Test
    @DisplayName("U29.9 — тип шага")
    void u29_9_theStepType() {
        CreateStrategyApiRequest request = reference();
        protectionStep(bull(request)).setStepType("TEARDOWN");

        assertThat(matching(violations(request), ".stepType: " + UNKNOWN + " TEARDOWN")).hasSize(1);
    }

    @Test
    @DisplayName("U29.10 — действие на устаревание данных: два носителя, при опущенном блоке — ни одного")
    void u29_10_theMarketDataExpiredActions() {
        CreateStrategyApiRequest declared = reference();
        entryStep(bull(declared)).getMarketDataExpiredSetting().setProtectedPositionAction("PANIC");
        entryStep(bull(declared)).getMarketDataExpiredSetting().setUnprotectedPositionAction("PANIC");

        CreateStrategyApiRequest absent = reference();
        entryStep(bull(absent)).setMarketDataExpiredSetting(null);

        assertThat(matching(violations(declared), "PositionAction: " + UNKNOWN + " PANIC")).hasSize(2);
        assertThat(violations(absent)).isEmpty();
    }

    @Test
    @DisplayName("U29.11 — тип правила условия")
    void u29_11_theConditionRuleType() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).get(2).setRuleType("MOON_PHASE");

        assertThat(matching(violations(request), ".ruleType: " + UNKNOWN + " MOON_PHASE")).hasSize(1);
    }

    @Test
    @DisplayName("U29.12 — оператор правила ШАГА: у правила классификации того же нарушения нет")
    void u29_12_theStepRuleOperator() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).get(2).setOperator("APPROX");

        assertThat(matching(violations(request), ".operator: " + UNKNOWN + " APPROX")).hasSize(1);
    }

    @Test
    @DisplayName("U29.13 — источник операнда")
    void u29_13_theOperandSourceType() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).get(2).getLeftOperand().setSourceType("ORACLE");

        assertThat(matching(violations(request), ".sourceType: " + UNKNOWN + " ORACLE")).hasSize(1);
    }

    @Test
    @DisplayName("U29.14 — источник рыночной цены: у операнда и у размещения")
    void u29_14_thePriceSourceOfTwoCarriers() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel operand = rules(entryStep(bull(request))).get(2).getLeftOperand();
        operand.setIndicatorKey(null);
        operand.setSourceType("PRICE");
        operand.setPriceSource("ORACLE_PRICE");
        StrategyPricePlacementApiModel placement = newPlacement("MARKET_PRICE");
        placement.setPriceSource("ORACLE_PRICE");
        entryAction(bull(request)).setPlacement(placement);

        assertThat(matching(violations(request), "priceSource: " + UNKNOWN + " ORACLE_PRICE")).hasSize(2);
    }

    @Test
    @DisplayName("U29.15 — тип значения константы")
    void u29_15_theConstantValueType() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).get(3).getRightOperand().setValueType("DECIMAL");

        assertThat(matching(violations(request), ".valueType: " + UNKNOWN + " DECIMAL")).hasSize(1);
    }

    @Test
    @DisplayName("U29.16 — компонент индикатора")
    void u29_16_theIndicatorComponent() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "ema_fast_15m").setIndicatorType("MACD");
        rules(entryStep(bull(request))).get(2).getLeftOperand().setIndicatorComponent("TRIPLE_LINE");

        assertThat(matching(violations(request), ".indicatorComponent: " + UNKNOWN + " TRIPLE_LINE")).hasSize(1);
    }

    @Test
    @DisplayName("U29.17 — тип действия: вид действия при этом разбирается дальше по его форме")
    void u29_17_theActionType() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel action = algoAction(bull(request), "bull_protection_oco");
        action.setActionType("PATCH_ACTION");
        action.setTriggerPriceType("LAST");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".actionType: " + UNKNOWN + " PATCH_ACTION")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK"))
                .as("форма действия разбирается независимо от типа")
                .hasSize(1);
    }

    @Test
    @DisplayName("U29.18 — тип заявки входного действия")
    void u29_18_theOrderType() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setOrderType("MARKET");

        assertThat(matching(violations(request), ".orderType: " + UNKNOWN + " MARKET")).hasSize(1);
    }

    @Test
    @DisplayName("U29.19 — направление сделки у действия-заявки")
    void u29_19_theTradeDirection() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setDirection("SIDEWAYS");

        assertThat(matching(violations(request), ".direction: " + UNKNOWN + " SIDEWAYS")).hasSize(1);
    }

    @Test
    @DisplayName("U29.20 — тип встроенной защиты: при опущенном блоке — ни одного")
    void u29_20_theAttachedProtectionType() {
        CreateStrategyApiRequest declared = reference();
        entryAction(bull(declared)).getAttachedProtection().setAttachedType("ATTACHED_TAKE_PROFIT");

        CreateStrategyApiRequest absent = reference();
        entryAction(bull(absent)).setOrderType("ENTRY");
        entryAction(bull(absent)).setAttachedProtection(null);

        assertThat(matching(violations(declared), ".attachedType: " + UNKNOWN)).hasSize(1);
        assertThat(matching(violations(absent), ".attachedType")).isEmpty();
    }

    @Test
    @DisplayName("U29.21 — база цены размещения")
    void u29_21_thePlacementBaseType() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPlacement(newPlacement("PIVOT"));

        assertThat(matching(violations(request), ".baseType: " + UNKNOWN + " PIVOT")).hasSize(1);
    }

    @Test
    @DisplayName("U29.22 — сторона смещения размещения: при опущенной стороне — ни одного")
    void u29_22_thePlacementOffsetSide() {
        CreateStrategyApiRequest declared = reference();
        StrategyPricePlacementApiModel placement = newPlacement("ENTRY_PRICE");
        placement.setOffsetSide("SIDEWAYS");
        entryAction(bull(declared)).setPlacement(placement);

        CreateStrategyApiRequest absent = reference();
        entryAction(bull(absent)).setPlacement(newPlacement("ENTRY_PRICE"));

        assertThat(matching(violations(declared), ".offsetSide: " + UNKNOWN + " SIDEWAYS")).hasSize(1);
        assertThat(matching(violations(absent), ".offsetSide")).isEmpty();
    }

    @Test
    @DisplayName("U29.23 — тип условия действия условной заявки")
    void u29_23_theAlgoConditionType() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setConditionType("BRACKET");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".conditionType: " + UNKNOWN + " BRACKET")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE"))
                .as("действие перестало считаться защитой, и покрытие шага упало до нуля")
                .hasSize(1);
    }

    @Test
    @DisplayName("U29.24 — база триггера: у действия и у настроек стопа")
    void u29_24_theTriggerPriceTypeOfTwoCarriers() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel action = algoAction(bull(request), "bull_protection_oco");
        action.setTriggerPriceType("AVERAGE");
        action.getStopLossSettings().setTriggerPriceType("AVERAGE");

        assertThat(matching(violations(request), "triggerPriceType: " + UNKNOWN + " AVERAGE")).hasSize(2);
    }

    @Test
    @DisplayName("U29.25 — способ расчёта уровня стопа")
    void u29_25_theStopCalculationType() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").getStopLossSettings().setCalculationType("FIBONACCI");

        assertThat(matching(violations(request), ".calculationType: " + UNKNOWN + " FIBONACCI")).hasSize(1);
    }

    @Test
    @DisplayName("U29.26 — пустое значение у БЕЗУСЛОВНОЙ сверки: то же нарушение, что у неизвестного")
    void u29_26_anAbsentValueIsRejectedLikeAnUnknownOne() {
        CreateStrategyApiRequest request = reference();
        bull(request).setPhaseEntryPolicy(null);

        assertThat(matching(violations(request), "details[0].phaseEntryPolicy: " + UNKNOWN + " null")).hasSize(1);
    }

    @Test
    @DisplayName("U29.27 — пустое значение у ОХРАНЯЕМОЙ сверки: поле выводится из-под сверки")
    void u29_27_aGuardedFieldIsNotMatchedAgainstItsEnumWhenAbsent() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).get(2).setOperator(null);
        entryAction(bull(request)).setPlacement(newPlacement("ENTRY_PRICE"));
        algoAction(bull(request), "bull_protection_oco").setTriggerPriceType(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, "operator: " + UNKNOWN)).isEmpty();
        assertThat(matching(violations, "offsetSide: " + UNKNOWN)).isEmpty();
        assertThat(matching(violations, "triggerPriceType: " + UNKNOWN))
                .as("собственная база действия сверяется только при непустоте")
                .isEmpty();
    }
}
