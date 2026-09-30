package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Грамматика условия: чьё ПРЕДЫДУЩЕЕ значение читает оценка — индикатора
 * (`U17.24`-`U17.29`) и цены (`U17.30`-`U17.35`); продолжение группы `U17` документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/rules/market-data-freshness.md, покрытие по обеим половинам
 * сравнения; docs/components/StrategyConditionEvaluator.md).
 *
 * <p><b>Базовая сборка:</b> условие с коллекцией правил настоящими полями —
 * тип правила, левый и правый операнды со своими типами источника и
 * авторскими именами. Истинность правила здесь не считается: предмет —
 * какие имена гейт покрытия обязан найти в раскладке предыдущих значений.
 */
class ConditionPastReadTest {

    private static final String FAST = "ema-fast";
    private static final String SLOW = "ema-slow";
    private static final String VOLUME = "obv";

    @Test
    @DisplayName("U17.24 — пересечение двух индикаторов")
    void u17_24_aCrossoverReadsThePastOfBothOperands() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, indicator(FAST), indicator(SLOW)));

        assertThat(subject.pastIndicatorKeys()).containsExactlyInAnyOrder(FAST, SLOW);
    }

    /** Объёмный фильтр сравнивает левый операнд с его же прошлым. */
    @Test
    @DisplayName("U17.25 — объёмный фильтр")
    void u17_25_aVolumeFilterReadsThePastOfItsLeftOperandOnly() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.VOLUME_FILTER_PASSED, indicator(VOLUME), indicator(SLOW)));

        assertThat(subject.pastIndicatorKeys()).containsExactly(VOLUME);
    }

    /** Сравнение с текущим значением прошлого не спрашивает. */
    @Test
    @DisplayName("U17.26 — сравнение индикатора с индикатором")
    void u17_26_aPlainCompareReadsNoPast() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.INDICATOR_COMPARE, indicator(FAST), indicator(SLOW)));

        assertThat(subject.pastIndicatorKeys()).isEmpty();
        assertThat(subject.indicatorKeys()).containsExactlyInAnyOrder(FAST, SLOW);
    }

    /**
     * У ценового операнда ключа прошлого индикатора нет: его прошлое ключует
     * индикатор-пара, и в перечень прошлого цены оно уходит (U17.30).
     */
    @Test
    @DisplayName("U17.27 — пересечение цены с индикатором")
    void u17_27_aPriceOperandHasNoPastKey() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, operand(StrategyConditionSourceType.PRICE, null),
                        indicator(SLOW)));

        assertThat(subject.pastIndicatorKeys()).containsExactly(SLOW);
    }

    @Test
    @DisplayName("U17.28 — пересечение с пустым правым операндом и пустым именем левого")
    void u17_28_absentOperandsAndBlankKeysAreNotCollected() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, indicator("  "), null),
                rule(StrategyConditionRuleType.VOLUME_FILTER_PASSED, null, indicator(VOLUME)));

        assertThat(subject.pastIndicatorKeys()).isEmpty();
    }

    /** Пустые правило, тип правила и коллекция отказа разыменования не дают. */
    @Test
    @DisplayName("U17.29 — коллекция правил пуста, правило пусто либо без типа")
    void u17_29_absentRulesReadNoPast() {
        assertThat(new StrategyCondition().pastIndicatorKeys()).isEmpty();
        assertThat(condition(null, rule(null, indicator(FAST), indicator(SLOW))).pastIndicatorKeys()).isEmpty();
    }

    /** Прошлое цены ключуется индикатором по другую сторону пересечения. */
    @Test
    @DisplayName("U17.30 — пересечение цены с индикатором: ключ прошлого цены")
    void u17_30_aPriceCrossoverKeysThePastPriceByTheIndicatorPair() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, price(), indicator(SLOW)));

        assertThat(subject.pastPriceKeys()).containsExactly(SLOW);
    }

    /** Сторона цены в пересечении значения не имеет: ключ — индикатор по другую сторону. */
    @Test
    @DisplayName("U17.31 — пересечение индикатора с ценой справа")
    void u17_31_thePriceOnTheRightKeysThePastPriceByTheLeftIndicator() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, indicator(FAST), price()));

        assertThat(subject.pastPriceKeys()).containsExactly(FAST);
    }

    /** Пересечение без цены прошлого цены не читает. */
    @Test
    @DisplayName("U17.32 — пересечение двух индикаторов")
    void u17_32_aCrossoverWithoutPriceReadsNoPastPrice() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, indicator(FAST), indicator(SLOW)));

        assertThat(subject.pastPriceKeys()).isEmpty();
    }

    /**
     * Пары-индикатора нет — ключа нет: у цены против константы и против цены
     * прошлого нет вовсе, и оценщик читает его ложью без раскладки.
     */
    @Test
    @DisplayName("U17.33 — пересечение цены с константой и цены с ценой")
    void u17_33_aPriceCrossoverWithoutAnIndicatorPairHasNoKey() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.CROSSOVER, price(), operand(StrategyConditionSourceType.CONSTANT, null)),
                rule(StrategyConditionRuleType.CROSSOVER, price(), price()));

        assertThat(subject.pastPriceKeys()).isEmpty();
    }

    /**
     * Прошлое цены читает только пересечение: объёмный фильтр и сравнение с
     * ценой его не спрашивают, хотя индикатор по другую сторону у них есть.
     */
    @Test
    @DisplayName("U17.34 — объёмный фильтр и сравнение с ценой")
    void u17_34_onlyTheCrossoverReadsThePastPrice() {
        StrategyCondition subject = condition(
                rule(StrategyConditionRuleType.VOLUME_FILTER_PASSED, price(), indicator(VOLUME)),
                rule(StrategyConditionRuleType.PRICE_COMPARE, price(), indicator(SLOW)));

        assertThat(subject.pastPriceKeys()).isEmpty();
    }

    /** Пустые правило, тип, пара и пробельное имя пары отказа разыменования не дают. */
    @Test
    @DisplayName("U17.35 — пустые правило, тип правила, пара цены и пробельное имя пары")
    void u17_35_absentPartsGiveNoPastPriceKey() {
        StrategyCondition subject = condition(
                null,
                rule(null, price(), indicator(SLOW)),
                rule(StrategyConditionRuleType.CROSSOVER, price(), null),
                rule(StrategyConditionRuleType.CROSSOVER, price(), indicator("  ")));

        assertThat(subject.pastPriceKeys()).isEmpty();
        assertThat(new StrategyCondition().pastPriceKeys()).isEmpty();
    }

    private static StrategyCondition condition(StrategyConditionRule... rules) {
        return new StrategyCondition(new ArrayList<>(Arrays.asList(rules)));
    }

    private static StrategyConditionRule rule(StrategyConditionRuleType type,
                                              StrategyConditionOperand left, StrategyConditionOperand right) {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(type);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return rule;
    }

    private static StrategyConditionOperand price() {
        return operand(StrategyConditionSourceType.PRICE, null);
    }

    private static StrategyConditionOperand indicator(String key) {
        return operand(StrategyConditionSourceType.INDICATOR, key);
    }

    private static StrategyConditionOperand operand(StrategyConditionSourceType sourceType, String indicatorKey) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(sourceType);
        operand.setIndicatorKey(indicatorKey);
        return operand;
    }
}
