package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.enumConstant;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Условие как конъюнкция и её вырождения — группа `U1` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (структура условия — docs/models/domain/aggregate/Strategy.md §Условия;
 * форма связки — §«Дом предиката: восемь типов корпус объявляет
 * бездомными сам», звено {@code StrategyConditionEvaluator#evaluate}).
 *
 * <p><b>Базовая сборка:</b> раскладки индикаторов и структур пусты, цены
 * нет, фактов сделки нет; условие — одно правило `MARKET_PHASE_IS` с
 * константой `BULL_TREND`, фаза прохода `BULL_TREND` (правило истинно).
 *
 * <p><b>Три формы пустого условия (`U1.6`-`U1.8`) и правило с пустым типом
 * (`U1.9`) здесь не прогоняются:</b> «пустое условие истинно» живёт только
 * в javadoc, а что обязан ответить интерпретатор на невалидном правиле, не
 * называет ни один носитель — §«Кейсы, не прогоняемые сегодня» того же
 * документа.
 */
class ConditionConjunctionTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Одно истинное правило; журнального следа у истины нет. */
    @Test
    @DisplayName("U1.1 — базовая сборка: истина, записи журнала нет")
    void u1_1_theBaseAssemblyIsTrueAndSilent() {
        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            assertThat(evaluator.evaluate(condition(phaseRule("BULL_TREND")), bullTrend())).isTrue();

            assertThat(log.messages()).as("истина следа в журнале не оставляет").isEmpty();
        }
    }

    /** Оба правила истинны — истинна и связка. */
    @Test
    @DisplayName("U1.2 — два правила, оба истинны: истина")
    void u1_2_twoTrueRulesMakeTheConditionTrue() {
        StrategyCondition condition = condition(phaseRule("BULL_TREND"),
                rule(StrategyConditionRuleType.NO_OPEN_POSITION));

        assertThat(evaluator.evaluate(condition, bullTrend())).isTrue();
    }

    /** Ложь второго правила гасит связку. */
    @Test
    @DisplayName("U1.3 — первое истинно, второе ложно: ложь")
    void u1_3_aFalseSecondRuleMakesTheConditionFalse() {
        StrategyCondition condition = condition(phaseRule("BULL_TREND"),
                rule(StrategyConditionRuleType.POSITION_OPENED));

        assertThat(evaluator.evaluate(condition, bullTrend())).isFalse();
    }

    /** Связка конъюнктивна и порядка не знает: ложь первого гасит её так же. */
    @Test
    @DisplayName("U1.4 — первое ложно, второе истинно: ложь — конъюнкция порядка не знает")
    void u1_4_aFalseFirstRuleMakesTheConditionFalse() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.POSITION_OPENED),
                phaseRule("BULL_TREND"));

        assertThat(evaluator.evaluate(condition, bullTrend())).isFalse();
    }

    /**
     * Значение от порядка в перечне не зависит, и поле {@code level}
     * интерпретатор не читает вовсе — это безвредно ровно потому, что
     * связка конъюнктивна.
     */
    @Test
    @DisplayName("U1.5 — те же два правила в обратном порядке и с объявленными уровнями: тот же исход, что у U1.4")
    void u1_5_theOrderOfRulesDoesNotChangeTheAnswer() {
        StrategyConditionRule truthy = phaseRule("BULL_TREND");
        truthy.setLevel(1);
        StrategyConditionRule falsy = rule(StrategyConditionRuleType.POSITION_OPENED);
        falsy.setLevel(2);

        assertThat(evaluator.evaluate(condition(falsy, truthy), bullTrend()))
                .as("прямой порядок")
                .isFalse();
        assertThat(evaluator.evaluate(condition(truthy, falsy), bullTrend()))
                .as("обратный порядок — тот же исход")
                .isFalse();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * Консервативная ложь объявлена домом
     * (docs/rules/absent-value-semantics.md), а пустой элемент перечня
     * правил роняет чтение типа: {@code #evaluate} уходит в
     * {@code #evaluateRule} и падает. Красный прогон и есть предъявление
     * находки `F-8` (`.claude/work/backlog.md` §«Интерпретатор бросает там,
     * где объявлена консервативная ложь»).
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G6`: вход производится
     * битым разбором тела определения, а не авторингом, и потому ждал
     * кейсов `F-8`.
     */
    @Test
    @Tag("debt")
    @DisplayName("U1.10 — элемент перечня правил пуст: ложь (дом), код роняет чтение типа")
    void u1_10_aNullRuleInTheListIsConservativelyFalse() {
        StrategyCondition condition = new StrategyCondition();
        condition.setRules(Arrays.asList(phaseRule("BULL_TREND"), null));

        assertThat(evaluator.evaluate(condition, bullTrend()))
                .as("пустой элемент перечня — недоступный операнд, а не отказ")
                .isFalse();
    }

    private StrategyConditionRule phaseRule(String declaredPhase) {
        return rule(StrategyConditionRuleType.MARKET_PHASE_IS, StrategyConditionOperator.EQ,
                null, enumConstant(declaredPhase));
    }

    private ConditionEvaluationContext bullTrend() {
        return base().marketPhase(MarketPhase.Type.BULL_TREND).build();
    }
}
