package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newOperand;
import static com.example.strategies.unit.validation.ValidationFixture.phaseConditionRule;
import static com.example.strategies.unit.validation.ValidationFixture.phaseRule;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rules;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyConditionOperandApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionRuleApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Контекст классификации фазы: белые списки — группа {@code U26}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/models/domain/aggregate/Strategy.md §«Настройки рыночных
 * данных»; сами списки живут константами предмета и домом не объявлены —
 * пробел {@code G7}).
 *
 * <p><b>Белый список — свойство КОНТЕКСТА, а не типа правила.</b> Тот же
 * тип на шаге транша законен, и кейс предъявляет обе стороны: иначе он
 * был бы зелен у валидатора, запрещающего тип везде.
 *
 * <p><b>Правило клаузы проходит ту же тропу, что правило шага</b>: набор
 * полей по типу, перечень оператора, снятый таймфрейм и контракт типа
 * считаются в обоих контекстах, а белые списки — сверх них только здесь.
 */
class PhaseClassificationContextTest {

    private static final String RULE_NOT_ALLOWED = "is not allowed in market phase classification context";

    /** Реджект поля, которого оценка типа не читает. */
    private static final String FIELD_NOT_READ = "STRATEGY_CONDITION_FIELD_NOT_READ";

    /** Реджект нескалярного операнда сравнения или пересечения. */
    private static final String NOT_SCALAR = "STRATEGY_CONDITION_OPERAND_NOT_SCALAR";

    /** Клауза бычьего тренда — сравнение двух скользящих средних. */
    private static final int TREND_CLAUSE = 1;

    /** Клауза диапазона — утверждение о структуре рынка. */
    private static final int RANGE_CLAUSE = 0;

    @Test
    @DisplayName("U26.1 — базовая сборка: три клаузы классификации внутри белых списков")
    void u26_1_theReferenceClausesStayInsideBothWhitelists() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U26.2 — тип правила классификации неизвестен: операнды и контракт не проверяются")
    void u26_2_anUnknownRuleTypeStopsTheClauseTraversal() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionRuleApiModel rule = phaseConditionRule(request, TREND_CLAUSE);
        rule.getLeftOperand().setIndicatorKey("no_such_indicator");
        rule.setRuleType("MOON_PHASE");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".ruleType: unknown value MOON_PHASE");
    }

    /**
     * Обход правила продолжается и после отказа белого списка: поля правила
     * сверяются набором его типа. Порог прибыли операндов не читает, и
     * унаследованные от клаузы оператор и операнды отвергаются как поля, а не
     * разбираются внутрь — ссылка операнда поэтому не сверяется.
     */
    @Test
    @DisplayName("U26.3 — тип правила вне белого списка: поля правила проверяются дальше")
    void u26_3_aForbiddenRuleTypeDoesNotStopTheOperandTraversal() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionRuleApiModel rule = phaseConditionRule(request, TREND_CLAUSE);
        rule.setRuleType("PROFIT_PERCENTS_REACHED");
        rule.setPercents(decimal("1"));
        rule.getLeftOperand().setIndicatorKey("no_such_indicator");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".ruleType PROFIT_PERCENTS_REACHED " + RULE_NOT_ALLOWED)).hasSize(1);
        assertThat(matching(violations, ".operator " + FIELD_NOT_READ)).hasSize(1);
        assertThat(matching(violations, ".leftOperand " + FIELD_NOT_READ)).hasSize(1);
        assertThat(matching(violations, ".rightOperand " + FIELD_NOT_READ)).hasSize(1);
        assertThat(matching(violations, "references unknown indicator setting key")).isEmpty();
    }

    @Test
    @DisplayName("U26.4 — тот же тип правила на шаге транша: белый список — свойство контекста")
    void u26_4_theSameRuleTypeIsLegalOnATrancheStep() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionRuleApiModel rule = rules(entryStep(bull(request))).get(2);
        rule.setRuleType("PROFIT_PERCENTS_REACHED");
        rule.setPercents(decimal("1"));
        rule.setOperator(null);
        rule.setLeftOperand(null);
        rule.setRightOperand(null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U26.5 — тип фазы у правила классификации неизвестен: условие проверяется дальше")
    void u26_5_anUnknownPhaseTypeDoesNotStopTheConditionTraversal() {
        CreateStrategyApiRequest request = reference();
        phaseRule(request, TREND_CLAUSE).setType("SIDEWAYS");
        phaseConditionRule(request, TREND_CLAUSE).getLeftOperand().setIndicatorKey("no_such_indicator");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".type: unknown value SIDEWAYS")).hasSize(1);
        assertThat(matching(violations, "references unknown indicator setting key")).hasSize(1);
    }

    /**
     * Сверх белого списка операнд фазы в сравнении нескаляр — его отвергает и
     * контракт типа независимо от контекста ({@code U28.45}); клетка пинит оба
     * нарушения, каждое своим путём.
     */
    @Test
    @DisplayName("U26.6 — источник операнда — рыночная фаза: классификация не опирается на свой результат")
    void u26_6_theMarketPhaseSourceIsForbiddenInItsOwnClassification() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setLeftOperand(newOperand("MARKET_PHASE"));

        List<String> violations = violations(request);

        assertThat(matching(violations, ".sourceType MARKET_PHASE " + RULE_NOT_ALLOWED)).hasSize(1);
        assertThat(matching(violations, ".leftOperand " + NOT_SCALAR)).hasSize(1);
    }

    @Test
    @DisplayName("U26.7 — источник операнда — позиция сделки: значение вне перечня, белый список не считается")
    void u26_7_theRuntimeDealSourceIsNotInTheGrammar() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setLeftOperand(newOperand("POSITION"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".leftOperand.sourceType: unknown value POSITION");
    }

    @Test
    @DisplayName("U26.8 — оператор правила классификации неизвестен: нарушение перечня")
    void u26_8_theClauseOperatorIsCheckedAgainstItsEnum() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setOperator("APPROX");

        assertThat(violations(request))
                .as("перечень операторов закрыт и сверяется в обоих контекстах правила")
                .singleElement()
                .asString()
                .contains(".operator: unknown value APPROX");
    }

    @Test
    @DisplayName("U26.9 — таймфрейм правила классификации объявлен годной строкой: поля таймфрейма нет")
    void u26_9_theClauseTimeframeIsNotRead() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setTimeframe("ONE_HOUR");

        assertThat(violations(request))
                .as("поля таймфрейма у правила нет ни у одного типа и ни в одном контексте")
                .singleElement()
                .asString()
                .contains(".timeframe " + FIELD_NOT_READ);
    }

    /**
     * Клауза без условия — безусловная фаза: оценка читает пустое условие
     * истиной, и фаза назначалась бы каждому инструменту на каждом проходе
     * (docs/rules/strategy-condition-contract.md §«Условие непусто»). Обход
     * правил при этом не начинается — отказ ровно один.
     */
    @Test
    @DisplayName("U26.10 — условие клаузы не объявлено: отказ пустого условия, обход правил не начинается")
    void u26_10_aClauseWithoutAConditionIsRejectedAndNotTraversed() {
        CreateStrategyApiRequest request = reference();
        phaseRule(request, TREND_CLAUSE).setCondition(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("marketPhaseSetting.phaseRules[" + TREND_CLAUSE + "].condition STRATEGY_CONDITION_EMPTY");
    }

    @Test
    @DisplayName("U26.13 — пересечение цены с константой в клаузе: контракт пары считается и здесь")
    void u26_13_theCrossoverPricePairIsCheckedInTheClauseContextToo() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionRuleApiModel rule = phaseConditionRule(request, TREND_CLAUSE);
        StrategyConditionOperandApiModel price = newOperand("PRICE");
        StrategyConditionOperandApiModel constant = newOperand("CONSTANT");
        constant.setValueType("NUMBER");
        constant.setValue("100");
        rule.setRuleType("CROSSOVER");
        rule.setOperator("CROSSED_ABOVE");
        rule.setLeftOperand(price);
        rule.setRightOperand(constant);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("marketPhaseSetting.phaseRules[")
                .contains("STRATEGY_CROSSOVER_PRICE_WITHOUT_INDICATOR");
    }

    @Test
    @DisplayName("U26.11 — утверждение о структуре без операнда-константы: контракт считается в обоих контекстах")
    void u26_11_theRuleContractIsEvaluatedInTheClauseContextToo() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, RANGE_CLAUSE).setRightOperand(newOperand("PRICE"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("MARKET_STRUCTURE_IS requires a CONSTANT operand with the structure type");
    }

    /**
     * Ценовой операнд в сравнении клаузы законен — индикатор против цены, —
     * и отказ один: источника цены у операнда нет ни в одном контексте.
     */
    @Test
    @DisplayName("U26.12 — ценовой операнд клаузы с индексной ценой: поля источника цены нет и здесь")
    void u26_12_aPriceSourceIsNotReadInTheClauseContextToo() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel price = newOperand("PRICE");
        price.setPriceSource("INDEX_PRICE");
        phaseConditionRule(request, TREND_CLAUSE).setRightOperand(price);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("marketPhaseSetting.phaseRules[")
                .contains(".rightOperand.priceSource " + FIELD_NOT_READ);
    }
}
