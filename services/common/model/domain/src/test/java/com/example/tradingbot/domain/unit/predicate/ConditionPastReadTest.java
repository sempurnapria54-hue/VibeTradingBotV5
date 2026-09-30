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
 * Грамматика условия: чьё ПРЕДЫДУЩЕЕ значение читает оценка — продолжение
 * группы `U17` документа `.claude/tests/cases/domain-model-predicates.md`
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

    /** Цена предыдущей половины не имеет: её пустоту оценщик читает ложью сам. */
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
