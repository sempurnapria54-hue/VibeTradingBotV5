package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.exitStep;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.phaseRule;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rules;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Условие непусто у шага и у клаузы классификации фазы — группа
 * {@code U35} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-condition-contract.md §«Условие непусто»; реджект —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»).
 *
 * <p><b>Опущенное условие и пустой перечень правил — одно состояние.</b>
 * Оценка читает оба истиной — нейтральным элементом конъюнкции, — и
 * потому оба получают один код: различать их автору незачем, а у шага и у
 * клаузы исход один — безусловность.
 *
 * <p><b>Экземпции у шага выхода нет.</b> Пустой пакет у него законен
 * потому, что «шаг выхода несёт только условие», — значит условие он
 * несёт обязательно, и опустошённый целиком шаг даёт ровно одно
 * нарушение, а не ни одного и не два.
 */
class ConditionPresenceTest {

    private static final String EMPTY_CONDITION = "STRATEGY_CONDITION_EMPTY";

    /** Клауза бычьего тренда — сравнение двух скользящих средних. */
    private static final int TREND_CLAUSE = 1;

    @Test
    @DisplayName("U35.1 — базовая сборка: у каждого шага и каждой клаузы условие непусто")
    void u35_1_everyReferenceConditionCarriesRules() {
        assertThat(matching(violations(reference()), EMPTY_CONDITION)).isEmpty();
    }

    @Test
    @DisplayName("U35.2 — у входного шага условие опущено: одно нарушение с путём условия")
    void u35_2_anOmittedStepConditionIsRejected() {
        CreateStrategyApiRequest request = reference();
        entryStep(bull(request)).setCondition(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stepsByStatus[PRECHECK][0].condition " + EMPTY_CONDITION);
    }

    @Test
    @DisplayName("U35.3 — у входного шага перечень правил пуст: тот же код, что у опущенного")
    void u35_3_anEmptyRuleListIsTheSameState() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).clear();

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stepsByStatus[PRECHECK][0].condition " + EMPTY_CONDITION);
    }

    @Test
    @DisplayName("U35.4 — шаг выхода без пакета и без условия: одно нарушение — условия")
    void u35_4_anExitStepWithoutItsConditionIsRejected() {
        CreateStrategyApiRequest request = reference();
        StrategyStepApiModel exit = exitStep(bull(request));
        exit.setActions(new ArrayList<>());
        exit.setCondition(null);

        assertThat(violations(request))
                .as("пустой пакет у выхода законен, пустое условие — нет")
                .singleElement()
                .asString()
                .contains(".stepsByStatus[ACTIVE][0].condition " + EMPTY_CONDITION);
    }

    @Test
    @DisplayName("U35.5 — у клаузы классификации перечень правил пуст: безусловная фаза отвергается")
    void u35_5_anEmptyClauseConditionIsRejected() {
        CreateStrategyApiRequest request = reference();
        phaseRule(request, TREND_CLAUSE).getCondition().setRules(new ArrayList<>());

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("marketPhaseSetting.phaseRules[" + TREND_CLAUSE + "].condition " + EMPTY_CONDITION);
    }

    @Test
    @DisplayName("U35.6 — пусты условия шага и клаузы: нарушения копятся, по одному на носитель")
    void u35_6_bothCarriersAccumulateTheirOwnViolation() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).clear();
        phaseRule(request, TREND_CLAUSE).setCondition(null);

        assertThat(matching(violations(request), EMPTY_CONDITION)).hasSize(2);
    }
}
