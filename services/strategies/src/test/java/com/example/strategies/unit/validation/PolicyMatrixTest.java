package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bear;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.range;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.unknownPhase;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Матрица «политика входа × фаза» — группа {@code U3} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/models/domain/aggregate/Strategy.md §StrategyDetail; инвариант
 * живёт предикатом доменной модели).
 *
 * <p><b>Матрица берётся настоящей, а не подменяется.</b> Предикат
 * доменной модели здесь операнд предмета, и подменённый он проверял бы
 * валидатор против матрицы, которой в проде не существует
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 *
 * <p><b>Смена политики двигает и признак торгуемости</b>, поэтому клетка
 * называет обе стороны: нарушение матрицы и то, что стало с проверками,
 * которые признак выключает.
 */
class PolicyMatrixTest {

    private static final String NOT_ALLOWED = "is not allowed for phase";

    @Test
    @DisplayName("U3.1 — базовая сборка: следование в трендовых фазах матрицу не нарушает")
    void u3_1_followingTheTrendIsAllowedInTrendPhases() {
        assertThat(matching(violations(reference()), NOT_ALLOWED)).isEmpty();
    }

    @Test
    @DisplayName("U3.2 — следование у детали диапазона: нарушение матрицы плюс торгуемость")
    void u3_2_followingIsForbiddenInRange() {
        CreateStrategyApiRequest request = reference();
        range(request).setPhaseEntryPolicy("FOLLOW_PHASE");

        List<String> violations = violations(request);

        assertThat(matching(violations, "phaseEntryPolicy FOLLOW_PHASE " + NOT_ALLOWED + " RANGE")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_TRANCHE_NOT_DECLARED"))
                .as("деталь стала торгуемой: объявлений у неё нет")
                .hasSize(1);
        assertThat(matching(violations, "STRATEGY_RISK_NUMBER_NOT_DECLARED"))
                .as("и ни одного из четырёх риск-чисел")
                .hasSize(4);
    }

    @Test
    @DisplayName("U3.3 — контр-игра в медвежьей фазе: матрица её допускает")
    void u3_3_contrarianIsAllowedInABearTrend() {
        CreateStrategyApiRequest request = reference();
        bear(request).setPhaseEntryPolicy("CONTRARIAN");

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U3.4 — контр-игра в неизвестной фазе: нарушение матрицы плюс торгуемость")
    void u3_4_contrarianIsForbiddenInTheUnknownPhase() {
        CreateStrategyApiRequest request = reference();
        unknownPhase(request).setPhaseEntryPolicy("CONTRARIAN");

        List<String> violations = violations(request);

        assertThat(matching(violations, "phaseEntryPolicy CONTRARIAN " + NOT_ALLOWED + " UNKNOWN")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_TRANCHE_NOT_DECLARED")).hasSize(1);
    }

    @Test
    @DisplayName("U3.5 — сетка у детали диапазона: матрица молчит, торгуемость говорит")
    void u3_5_theGridPolicyIsAllowedInRangeAndMakesTheDetailTradable() {
        CreateStrategyApiRequest request = reference();
        range(request).setPhaseEntryPolicy("GRID");

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_ALLOWED)).isEmpty();
        assertThat(violations)
                .as("одно нарушение объявлений и четыре риск-числа")
                .hasSize(5);
    }

    @Test
    @DisplayName("U3.6 — сетка у трендовой детали: только нарушение матрицы")
    void u3_6_theGridPolicyIsForbiddenInATrendPhase() {
        CreateStrategyApiRequest request = reference();
        bull(request).setPhaseEntryPolicy("GRID");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("phaseEntryPolicy GRID " + NOT_ALLOWED + " BULL_TREND");
    }

    @Test
    @DisplayName("U3.7 — отказ от торговли допустим в любой фазе: нарушений матрицы нет ни одного")
    void u3_7_refusingToTradeIsAllowedEverywhere() {
        CreateStrategyApiRequest request = reference();
        request.getDetails().forEach(detail -> detail.setPhaseEntryPolicy("NO_TRADE"));

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_ALLOWED)).isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL"))
                .as("обе прежде торгуемые детали объявлений теперь не вправе нести")
                .hasSize(2);
    }

    @Test
    @DisplayName("U3.8 — политика входа — неизвестная строка: матрица не считается вовсе")
    void u3_8_anUnknownPolicyIsNeverMatchedAgainstTheMatrix() {
        CreateStrategyApiRequest request = reference();
        bull(request).setPhaseEntryPolicy("AGGRESSIVE");

        List<String> violations = violations(request);

        assertThat(matching(violations, "details[0].phaseEntryPolicy: unknown value AGGRESSIVE")).hasSize(1);
        assertThat(matching(violations, NOT_ALLOWED)).isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL")).hasSize(1);
    }

    @Test
    @DisplayName("U3.9 — тип фазы неизвестен при годной политике: матрица не считается")
    void u3_9_anUnknownPhaseTypeAlsoSilencesTheMatrix() {
        CreateStrategyApiRequest request = reference();
        bull(request).setMarketPhaseType("SIDEWAYS");

        List<String> violations = violations(request);

        assertThat(matching(violations, "details[0].marketPhaseType: unknown value SIDEWAYS")).hasSize(1);
        assertThat(matching(violations, NOT_ALLOWED)).isEmpty();
    }
}
