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
import org.junit.jupiter.api.Tag;
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
 * <p><b>Контракт типа правила считается в ОБОИХ контекстах</b>, а
 * перечни оператора и таймфрейма — только у правила шага: ровно это
 * расхождение и предъявляют красные клетки группы (находка {@code F4}).
 */
class PhaseClassificationContextTest {

    private static final String RULE_NOT_ALLOWED = "is not allowed in market phase classification context";

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

    @Test
    @DisplayName("U26.3 — тип правила вне белого списка: операнды проверяются дальше")
    void u26_3_aForbiddenRuleTypeDoesNotStopTheOperandTraversal() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionRuleApiModel rule = phaseConditionRule(request, TREND_CLAUSE);
        rule.setRuleType("PROFIT_PERCENTS_REACHED");
        rule.setPercents(decimal("1"));
        rule.getLeftOperand().setIndicatorKey("no_such_indicator");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".ruleType PROFIT_PERCENTS_REACHED " + RULE_NOT_ALLOWED)).hasSize(1);
        assertThat(matching(violations, "references unknown indicator setting key no_such_indicator"))
                .hasSize(1);
    }

    @Test
    @DisplayName("U26.4 — тот же тип правила на шаге транша: белый список — свойство контекста")
    void u26_4_theSameRuleTypeIsLegalOnATrancheStep() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionRuleApiModel rule = rules(entryStep(bull(request))).get(2);
        rule.setRuleType("PROFIT_PERCENTS_REACHED");
        rule.setPercents(decimal("1"));

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

    @Test
    @DisplayName("U26.6 — источник операнда — рыночная фаза: классификация не опирается на свой результат")
    void u26_6_theMarketPhaseSourceIsForbiddenInItsOwnClassification() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setLeftOperand(newOperand("MARKET_PHASE"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".sourceType MARKET_PHASE " + RULE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("U26.7 — источник операнда — позиция сделки: runtime-состояние недоступно")
    void u26_7_theRuntimeDealSourceIsForbiddenToo() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setLeftOperand(newOperand("POSITION"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".sourceType POSITION " + RULE_NOT_ALLOWED);
    }

    @Test
    @Tag("debt")
    @DisplayName("U26.8 — оператор правила классификации неизвестен: дом требует отказа (F4)")
    void u26_8_theClauseOperatorIsCheckedAgainstItsEnumByTheHome() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setOperator("APPROX");

        assertThat(violations(request))
                .as("ожидание из дома: перечень операторов закрыт, и у правила шага он сверяется")
                .isNotEmpty();
    }

    @Test
    @Tag("debt")
    @DisplayName("U26.9 — таймфрейм правила классификации неизвестен: дом требует отказа (F4)")
    void u26_9_theClauseTimeframeIsCheckedAgainstItsEnumByTheHome() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE).setTimeframe("TEN_MINUTES");

        assertThat(violations(request))
                .as("ожидание из дома: у правила шага таймфрейм сверяется, у клаузы — нет")
                .isNotEmpty();
    }

    @Test
    @DisplayName("U26.10 — условие клаузы не объявлено: обход правил не начинается")
    void u26_10_aClauseWithoutAConditionIsNotTraversed() {
        CreateStrategyApiRequest request = reference();
        phaseRule(request, TREND_CLAUSE).setCondition(null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U26.11 — утверждение о структуре без операнда-константы: контракт считается в обоих контекстах")
    void u26_11_theRuleContractIsEvaluatedInTheClauseContextToo() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel price = newOperand("PRICE");
        price.setPriceSource("LAST_PRICE");
        phaseConditionRule(request, RANGE_CLAUSE).setRightOperand(price);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("MARKET_STRUCTURE_IS requires a CONSTANT operand with the structure type");
    }
}
