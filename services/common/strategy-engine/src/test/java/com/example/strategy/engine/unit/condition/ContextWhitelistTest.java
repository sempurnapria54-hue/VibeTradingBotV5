package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.FAST_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.constant;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicator;
import static com.example.strategy.engine.unit.condition.ConditionFixture.livePosition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Whitelist контекста выражен пустотой операндов — группа `U11` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/components/StrategyConditionEvaluator.md §Назначение;
 * docs/spec/deal-condition.json).
 *
 * <p><b>Базовая сборка:</b> контекст классификации фазы — раскладки
 * собраны и пусты, цены нет, фазы нет, <b>фактов сделки нет ни одного</b>:
 * ни эпизода, ни транша, ни фазы входа.
 *
 * <p><b>Whitelist выражен ПУСТОТОЙ ОПЕРАНДОВ, а не перечнем типов:</b>
 * отдельный список разрешённых типов был бы вторым носителем того же
 * различения и разошёлся бы с ним при добавлении первого же типа
 * (.claude/rules/policy-home.md).
 */
class ContextWhitelistTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Эпизода нет — предикат живого риска ложен. */
    @Test
    @DisplayName("U11.1 — правило POSITION_OPENED: ложь")
    void u11_1_thePositionRuleIsFalseWithoutFacts() {
        assertThat(evaluate(StrategyConditionRuleType.POSITION_OPENED)).isFalse();
    }

    /** Транша нет — налив входной ноги невыразим. */
    @Test
    @DisplayName("U11.2 — правило ENTRY_ORDER_FINALIZED: ложь")
    void u11_2_theEntryRuleIsFalseWithoutFacts() {
        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED)).isFalse();
    }

    /** Транша нет — отдельная защита невыразима. */
    @Test
    @DisplayName("U11.3 — правило MAIN_PROTECTION_EXISTS: ложь")
    void u11_3_theStandaloneProtectionRuleIsFalseWithoutFacts() {
        assertThat(evaluate(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS)).isFalse();
    }

    /** Транша нет — встроенная защита невыразима. */
    @Test
    @DisplayName("U11.4 — правило ATTACHED_STOP_LOSS_EXISTS: ложь")
    void u11_4_theAttachedProtectionRuleIsFalseWithoutFacts() {
        assertThat(evaluate(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS)).isFalse();
    }

    /** Операндов хода нет — порог не срабатывает даже при объявленном пороге. */
    @Test
    @DisplayName("U11.5 — правило PROFIT_PERCENTS_REACHED с порогом 2 в обеих формах: ложь")
    void u11_5_theProfitThresholdIsFalseWithoutFacts() {
        StrategyConditionRule moveRule = rule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED,
                StrategyConditionOperator.GTE, null, constant("2", ConstantValueType.PERCENT));
        moveRule.setPercents(new BigDecimal("2"));

        assertThat(evaluator.evaluate(condition(moveRule), base().build())).isFalse();
    }

    /** Фазы входа нет — смена тренда невыразима. */
    @Test
    @DisplayName("U11.6 — правило TREND_CHANGED: ложь")
    void u11_6_theTrendChangedRuleIsFalseWithoutFacts() {
        assertThat(evaluate(StrategyConditionRuleType.TREND_CHANGED)).isFalse();
    }

    /**
     * Строгое отрицание отсутствующего эпизода консервативным НЕ является,
     * и это названное следствие формы whitelist'а: типа в перечне
     * разрешённых у классификации фазы нет, и запрещает его СОЗДАНИЕ, а не
     * интерпретатор (docs/rules/strategy-validation.md).
     */
    @Test
    @DisplayName("U11.7 — правило NO_OPEN_POSITION: истина — строгое отрицание консервативным не является")
    void u11_7_theStrictNegationIsTrueOnAnEmptyContext() {
        assertThat(evaluate(StrategyConditionRuleType.NO_OPEN_POSITION)).isTrue();
    }

    /** Пара к U11.1: различает их ровно наличие факта в контексте, а не перечень типов. */
    @Test
    @DisplayName("U11.8 — POSITION_OPENED, контекст сделки с живым эпизодом: истина")
    void u11_8_theSameRuleIsTrueOnceTheFactIsPresent() {
        ConditionEvaluationContext dealContext = base()
                .activePosition(livePosition("100"))
                .tranche(tranche())
                .build();

        assertThat(evaluator.evaluate(condition(rule(StrategyConditionRuleType.POSITION_OPENED)), dealContext))
                .as("перечень типов тот же — различает наличие факта")
                .isTrue();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * Несобранная раскладка есть недоступный операнд, и дом объявляет
     * консервативную ложь (docs/rules/absent-value-semantics.md); код
     * обращается к пустой раскладке и роняет оценку. Красный прогон и есть
     * предъявление находки `F-8` (`.claude/work/backlog.md`
     * §«Интерпретатор бросает там, где объявлена консервативная ложь»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U11.9 — контекст собран без раскладок вовсе: ложь (дом), код роняет оценку")
    void u11_9_aContextWithoutLayoutsIsConservativelyFalse() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE,
                StrategyConditionOperator.GT, indicator(FAST_KEY, null), number("10")));

        assertThat(evaluator.evaluate(condition, ConditionEvaluationContext.builder().build()))
                .as("несобранная раскладка — недоступный операнд, а не отказ")
                .isFalse();
    }

    private Boolean evaluate(StrategyConditionRuleType type) {
        return evaluator.evaluate(condition(rule(type)), base().build());
    }
}
