package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.actions;
import static com.example.strategies.unit.validation.ValidationFixture.bear;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.dealStepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.indicators;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.phaseConditionRule;
import static com.example.strategies.unit.validation.ValidationFixture.phaseRule;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rules;
import static com.example.strategies.unit.validation.ValidationFixture.steps;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.structures;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseRuleApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Член коллекции определения не пуст — группа {@code U36} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (реджект —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»).
 *
 * <p><b>Каждая клетка несёт однозначный ассерт, а не «не упало».</b>
 * Прежде пустой член доезжал до проверок узла и ронял обход
 * разыменованием: поверхность отвечала {@code 500}. Клетка поэтому
 * ожидает ровно одно нарушение с кодом и путём члена — и исключение
 * другого класса роняет её, а не проходит молча.
 *
 * <p><b>Отказ с пустым членом других нарушений не несёт.</b> Обход дерева
 * начинается, когда все члены на месте; клетка {@code U36.13} мерит эту
 * цену на испорченной второй оси.
 */
class CollectionMemberPresenceTest {

    private static final String EMPTY_MEMBER = "STRATEGY_COLLECTION_MEMBER_EMPTY";

    /** Путь входного шага бычьей детали — дорожка предвходовой проверки первого объявления. */
    private static final String ENTRY_STEP = "details[0].tranches[0].stepsByStatus[PRECHECK][0]";

    /** Клауза бычьего тренда — сравнение двух скользящих средних. */
    private static final int TREND_CLAUSE = 1;

    @Test
    @DisplayName("U36.1 — базовая сборка: пустых членов нет")
    void u36_1_theReferenceHasNoEmptyMembers() {
        assertThat(matching(violations(reference()), EMPTY_MEMBER)).isEmpty();
    }

    @Test
    @DisplayName("U36.2 — пустое правило внутри непустого условия шага: отказ создания, а не отказ сервера")
    void u36_2_anEmptyStepRuleIsRejected() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).add(0, null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(ENTRY_STEP + ".condition.rules[0] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.3 — пустое правило внутри условия клаузы классификации фазы")
    void u36_3_anEmptyClauseRuleIsRejected() {
        CreateStrategyApiRequest request = reference();
        phaseConditionRule(request, TREND_CLAUSE);
        phaseRule(request, TREND_CLAUSE).getCondition().getRules().add(0, null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("marketPhaseSetting.phaseRules[" + TREND_CLAUSE + "].condition.rules[0] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.4 — пустая клауза классификации фазы")
    void u36_4_anEmptyClauseIsRejected() {
        CreateStrategyApiRequest request = reference();
        List<StrategyMarketPhaseRuleApiModel> clauses = clauses(request);
        int index = clauses.size();
        clauses.add(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("marketPhaseSetting.phaseRules[" + index + "] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.5 — пустая деталь: покрытие фаз не считается, отказ один")
    void u36_5_anEmptyDetailIsRejected() {
        CreateStrategyApiRequest request = reference();
        request.getDetails().add(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[4] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.6 — пустое объявление транша")
    void u36_6_anEmptyTrancheIsRejected() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[1] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.7 — пустой шаг в дорожке объявления")
    void u36_7_anEmptyTrancheStepIsRejected() {
        CreateStrategyApiRequest request = reference();
        steps(tranche(bull(request)), "PRECHECK").add(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[0].stepsByStatus[PRECHECK][1] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.8 — пустое значение карты шагов по статусу: тот же код с путём статуса")
    void u36_8_anEmptyStatusLaneIsRejected() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("MANAGING", null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[0].stepsByStatus[MANAGING] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.9 — пустое действие в пакете шага")
    void u36_9_anEmptyActionIsRejected() {
        CreateStrategyApiRequest request = reference();
        actions(entryStep(bull(request))).add(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(ENTRY_STEP + ".actions[1] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.10 — пустой шаг уровня сделки")
    void u36_10_anEmptyDealLevelStepIsRejected() {
        CreateStrategyApiRequest request = reference();
        dealStepsByStatus(bull(request)).get("ACTIVE").add(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].stepsByStatus[ACTIVE][1] " + EMPTY_MEMBER);
    }

    @Test
    @DisplayName("U36.11 — пустые настройки индикатора и структуры: по нарушению на каталог")
    void u36_11_emptyCatalogueSettingsAreRejected() {
        CreateStrategyApiRequest request = reference();
        int indicatorIndex = indicators(request).size();
        indicators(request).add(null);
        int structureIndex = structures(request).size();
        structures(request).add(null);

        assertThat(violations(request)).satisfiesExactly(
                indicator -> assertThat(indicator)
                        .startsWith("strategy.indicatorSettings[" + indicatorIndex + "] " + EMPTY_MEMBER),
                structure -> assertThat(structure)
                        .startsWith("strategy.marketStructureSettings[" + structureIndex + "] " + EMPTY_MEMBER));
    }

    @Test
    @DisplayName("U36.12 — пустые члены в разных деталях: копятся между собой")
    void u36_12_emptyMembersAccumulate() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).add(0, null);
        tranches(bear(request)).add(null);

        assertThat(matching(violations(request), EMPTY_MEMBER)).hasSize(2);
    }

    @Test
    @DisplayName("U36.13 — пустой член при испорченной второй оси: прочие нарушения не считаются")
    void u36_13_anEmptyMemberStopsTheTreeWalk() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).add(0, null);
        bull(request).setPhaseEntryPolicy("MOON_POLICY");

        assertThat(violations(request))
                .as("обход дерева начинается, когда все члены на месте")
                .singleElement()
                .asString()
                .contains(EMPTY_MEMBER);
    }

    /** Изменяемый перечень клауз классификации эталона. */
    private List<StrategyMarketPhaseRuleApiModel> clauses(CreateStrategyApiRequest request) {
        phaseRule(request, 0);
        return request.getMarketPhaseSetting().getPhaseRules();
    }
}
