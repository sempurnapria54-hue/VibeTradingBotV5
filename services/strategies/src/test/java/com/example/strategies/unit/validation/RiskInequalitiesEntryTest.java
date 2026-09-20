package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.action;
import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.appetite;
import static com.example.strategies.unit.validation.ValidationFixture.baseAppetite;
import static com.example.strategies.unit.validation.ValidationFixture.bear;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.inequalityViolations;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rules;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Вторая точка входа: пять неравенств на текущих числах — группа
 * {@code U30} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Что проверяется на активации»).
 *
 * <p><b>Перепроверяются ВСЕ ПЯТЬ, а не только зависящие от чисел.</b>
 * Разделение «эти зависят, эти нет» пришлось бы поддерживать при каждой
 * правке неравенств, а цена повтора — обход уже прочитанного дерева.
 *
 * <p><b>Структурные проверки создания здесь НЕ повторяются</b>: дерево
 * неизменяемо, и клетка называет это молчание — дубль ключа, битая
 * ссылка и неизвестное значение перечня отказа не дают.
 */
class RiskInequalitiesEntryTest {

    private static final String SIMULTANEOUS_ABOVE = "STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL";

    private static final String CATASTROPHIC_ABOVE = "STRATEGY_CATASTROPHIC_MULTIPLIER_ABOVE_GLOBAL";

    private static final String FAN = "STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE";

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

    private static final String COVERAGE = "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE";

    private static final String NOT_DECLARED = "STRATEGY_RISK_NUMBER_NOT_DECLARED";

    @Test
    @DisplayName("U30.1 — годное дерево и те же числа, что при создании: отказа нет")
    void u30_1_theReferenceTreePassesOnTheSameNumbers() {
        CreateStrategyApiRequest request = reference();

        assertThat(inequalityViolations(request.getDetails(), baseAppetite())).isEmpty();
    }

    @Test
    @DisplayName("U30.2 — конфигурационный потолок снижен после создания: первое неравенство")
    void u30_2_theCheckStandsOnTheCurrentCeiling() {
        CreateStrategyApiRequest request = reference();

        assertThat(matching(inequalityViolations(request.getDetails(), appetite("0.5", "100")),
                SIMULTANEOUS_ABOVE)).isNotEmpty();
    }

    @Test
    @DisplayName("U30.3 — конфигурационный предел множителя снижен: третье неравенство")
    void u30_3_theMultiplierLimitIsRecheckedToo() {
        CreateStrategyApiRequest request = reference();

        assertThat(matching(inequalityViolations(request.getDetails(), appetite("1", "50")),
                CATASTROPHIC_ABOVE)).isNotEmpty();
    }

    @Test
    @DisplayName("U30.4 — потолок снижен так, что нотинал перестаёт оставлять запас: пятое неравенство")
    void u30_4_theNotionalHeadroomIsRecheckedOnCurrentNumbers() {
        CreateStrategyApiRequest request = reference();

        assertThat(matching(inequalityViolations(request.getDetails(), appetite("0.95", "100")),
                HEADROOM)).isNotEmpty();
    }

    @Test
    @DisplayName("U30.5 — дерево несёт веер выше потолка детали: второе неравенство")
    void u30_5_theFanIsRecheckedThoughItDependsOnNoTenantNumber() {
        CreateStrategyApiRequest request = reference();
        StrategyTrancheApiModel tranche = tranche(bull(request));
        tranche.setLevelCount(2);
        tranche.setLevelStep(decimal("1"));

        assertThat(matching(inequalityViolations(request.getDetails(), baseAppetite()), FAN)).hasSize(1);
    }

    @Test
    @DisplayName("U30.6 — дерево несёт шаг с неполным покрытием: четвёртое неравенство")
    void u30_6_theProtectionCoverageIsRecheckedToo() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(decimal("50"));

        assertThat(matching(inequalityViolations(request.getDetails(), baseAppetite()), COVERAGE)).hasSize(1);
    }

    @Test
    @DisplayName("U30.7 — у детали опущено риск-число: обязательность проверяется и здесь")
    void u30_7_theDeclarationCheckRunsAtActivationToo() {
        CreateStrategyApiRequest request = reference();
        bull(request).setRiskPerActionPercent(null);

        assertThat(matching(inequalityViolations(request.getDetails(), baseAppetite()), NOT_DECLARED))
                .hasSize(1);
    }

    @Test
    @DisplayName("U30.8 — дубль ключа, битая ссылка и неизвестный перечень: структурное не повторяется")
    void u30_8_structuralChecksAreNotRepeatedAtActivation() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        action(detail, "bull_trailing").setKey("bull_oco_cancel");
        algoAction(detail, "bull_sl_to_breakeven").setTargetActionKey("no_such_action");
        rules(entryStep(detail)).get(2).setRuleType("MOON_PHASE");

        assertThat(inequalityViolations(request.getDetails(), baseAppetite()))
                .as("дерево неизменяемо, и повторять структурные проверки незачем")
                .isEmpty();
    }

    @Test
    @DisplayName("U30.9 — все детали неторгуемые: они пропускаются по построению")
    void u30_9_nonTradingDetailsAreSkipped() {
        CreateStrategyApiRequest request = reference();
        request.getDetails().forEach(detail -> detail.setPhaseEntryPolicy("NO_TRADE"));

        assertThat(inequalityViolations(request.getDetails(), baseAppetite())).isEmpty();
    }

    @Test
    @DisplayName("U30.10 — список деталей пуст: охрана незаданных чисел стои́т ДО этой точки")
    void u30_10_anEmptyDetailListRejectsNothingHere() {
        assertThat(inequalityViolations(List.of(), appetite(null, null)))
                .as("иначе определение без торгуемых деталей прошло бы мимо охраны чисел")
                .isEmpty();
    }

    @Test
    @DisplayName("U30.11 — два неравенства в разных деталях: один отказ, оба нарушения в тексте")
    void u30_11_bothViolationsTravelInOneRejection() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setLevelCount(2);
        tranche(bull(request)).setLevelStep(decimal("1"));
        bear(request).setStrategySimultaneousRiskPerDealPercent(decimal("2"));

        List<String> violations = inequalityViolations(request.getDetails(), baseAppetite());

        assertThat(matching(violations, "details[0]")).isNotEmpty();
        assertThat(matching(violations,
                "details[1].strategySimultaneousRiskPerDealPercent " + SIMULTANEOUS_ABOVE)).hasSize(1);
    }
}
