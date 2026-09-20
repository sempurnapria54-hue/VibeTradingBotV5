package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.actions;
import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.cancelStep;
import static com.example.strategies.unit.validation.ValidationFixture.dealStepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newAlgo;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.newTranche;
import static com.example.strategies.unit.validation.ValidationFixture.protectionStep;
import static com.example.strategies.unit.validation.ValidationFixture.range;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Неравенство 4: покрытие защиты после шага — группа {@code U13}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/rules/live-risk-protection.md §«Покрытие»; исполнимые формы —
 * docs/spec/strategy-reference.json, величины
 * {@code stepCoverageComplete} и {@code partialStepsReturningLess}).
 *
 * <p><b>Пустая доля читается ДВУМЯ способами, и это несущее
 * различие:</b> у действия, ставящего покрытие, она входит в сумму
 * нулём, а у защитной ЦЕЛИ замещения — читается как ВСЁ покрытие.
 * Первое роняет шаг полного набора, второе делает шаг полным набором.
 *
 * <p><b>Неразрешённая цель из-под проверки шаг не выводит</b> — она
 * читается как всё действующее покрытие, — поэтому битая ссылка сама по
 * себе нарушения покрытия не даёт: его даёт только объявленное меньше
 * ста.
 */
class ProtectionCoverageTest {

    private static final String INCOMPLETE = "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE";

    private static final String UNKNOWN_TARGET = "references unknown action key";

    private static final String PROTECTION_PATH = "details[0].tranches[bull_main].ENTRY_FINALIZED[0]";

    @Test
    @DisplayName("U13.1 — базовая сборка: нарушений покрытия нет ни у одного шага дорожки")
    void u13_1_everyStepOfTheReferenceKeepsFullCoverage() {
        assertThat(matching(violations(reference()), INCOMPLETE)).isEmpty();
    }

    @Test
    @DisplayName("U13.2 — шаг входа защитных действий не несёт: полным набором он не объявлен")
    void u13_2_anEntryStepIsNeitherAFullSetNorATaker() {
        assertThat(matching(violations(reference()), "PRECHECK[0] " + INCOMPLETE)).isEmpty();
    }

    @Test
    @DisplayName("U13.3 — доля первичной постановки снижена до 50: шаг полного набора обязан дать сто")
    void u13_3_aFullSetStepDeclaringLessThanHundredIsRejected() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(decimal("50"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(PROTECTION_PATH + " " + INCOMPLETE)
                .contains("шаг полного набора защиты объявляет 50 % вместо ста");
    }

    @Test
    @DisplayName("U13.4 — второе защитное создание с долей 50: сумма выше ста тоже невыполнима")
    void u13_4_aFullSetStepDeclaringMoreThanHundredIsRejectedToo() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel extra = newAlgo("bull_second_stop", "CREATE_ACTION", "STOP_LOSS");
        extra.setCloseFractionPercents(decimal("50"));
        actions(protectionStep(bull(request))).add(extra);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("объявляет 150 % вместо ста");
    }

    @Test
    @DisplayName("U13.5 — доля первичной постановки опущена: в сумме покрытия пустая доля — ноль")
    void u13_5_anAbsentFractionCountsAsZeroInTheSum() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("объявляет 0 % вместо ста");
    }

    @Test
    @DisplayName("U13.6 — трейлинг снят из шага «снятие плюс трейлинг»: снятие адресует всё покрытие")
    void u13_6_aCancelWithoutReplacementBecomesAFullSetStep() {
        CreateStrategyApiRequest request = reference();
        StrategyStepApiModel step = cancelStep(bull(request));
        actions(step).removeIf(action -> "bull_trailing".equals(action.getKey()));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[bull_main].MANAGING[1] " + INCOMPLETE)
                .contains("объявляет 0 % вместо ста");
    }

    @Test
    @DisplayName("U13.7 — создание долей 50 и замещение долей 30: вторая ветвь неравенства")
    void u13_7_aPartialStepMustReturnAtLeastWhatItTakes() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel created = newAlgo("partial_stop", "CREATE_ACTION", "STOP_LOSS");
        created.setCloseFractionPercents(decimal("50"));
        StrategyAlgoOrderActionApiModel replaced = newAlgo("partial_replace", "REPLACE_ACTION", "STOP_LOSS");
        replaced.setTargetActionKey("partial_stop");
        replaced.setCloseFractionPercents(decimal("30"));
        stepsByStatus(tranche(bull(request))).put("PROTECTION_SWITCHED",
                List.of(newStep("PROTECTION_ADJUSTMENT", created),
                        newStep("PROTECTION_ADJUSTMENT", replaced)));

        assertThat(violations(request))
                .as("шаг-создатель нарушения не даёт: он ничего не забирает")
                .singleElement()
                .asString()
                .contains("шаг забирает 50 % действующего покрытия и возвращает 30 %");
    }

    @Test
    @DisplayName("U13.8 — цель замещения — несуществующий ключ: неразрешённая цель читается как ВСЁ")
    void u13_8_anUnresolvedTargetDoesNotDropTheStepOutOfTheCheck() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setTargetActionKey("no_such_action");

        List<String> violations = violations(request);

        assertThat(matching(violations, UNKNOWN_TARGET)).hasSize(1);
        assertThat(matching(violations, INCOMPLETE))
                .as("шаг стал шагом полного набора, а свои сто процентов он объявляет")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.9 — цель снятия не названа вовсе: резолв охраняется непустотой, покрытие полно")
    void u13_9_anAbsentTargetIsNeitherResolvedNorIncomplete() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_oco_cancel").setTargetActionKey(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, UNKNOWN_TARGET))
                .as("пустая цель под резолв не попадает")
                .isEmpty();
        assertThat(matching(violations, INCOMPLETE))
                .as("пустая цель читается как ВСЁ покрытие, и трейлинг шага возвращает сто")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.10 — цель замещения не защита: забранное покрытие — ноль")
    void u13_10_aNonProtectiveTargetContributesNoCoverage() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setTargetActionKey("bull_entry");

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U13.11 — пустая доля цели и доля 50 у замещения: два нарушения разных шагов")
    void u13_11_anAbsentFractionReadsAsZeroInASumAndAsAllAtATarget() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        algoAction(detail, "bull_protection_oco").setCloseFractionPercents(null);
        algoAction(detail, "bull_sl_to_breakeven").setCloseFractionPercents(decimal("50"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, PROTECTION_PATH))
                .singleElement()
                .asString()
                .contains("объявляет 0 % вместо ста");
        assertThat(matching(violations, "details[0].tranches[bull_main].MANAGING[0]"))
                .singleElement()
                .asString()
                .contains("объявляет 50 % вместо ста");
    }

    @Test
    @DisplayName("U13.12 — тип условия сменён на тейк-профит: он защитой не является")
    void u13_12_aTakeProfitCountsTowardsNoCoverage() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setConditionType("TAKE_PROFIT");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(PROTECTION_PATH)
                .contains("объявляет 0 % вместо ста");
    }

    @Test
    @DisplayName("U13.13 — тот же дефект на шаге уровня СДЕЛКИ: область инварианта — транш")
    void u13_13_theCoverageInvariantIsTrancheScoped() {
        CreateStrategyApiRequest dealLevel = reference();
        dealStepsByStatus(bull(dealLevel)).put("EXIT_PENDING", List.of(bareCancelStep("deal_cancel")));

        CreateStrategyApiRequest trancheLevel = reference();
        stepsByStatus(tranche(bull(trancheLevel))).put("PROTECTION_SWITCHED",
                List.of(bareCancelStep("tranche_cancel")));

        assertThat(matching(violations(dealLevel), INCOMPLETE))
                .as("обход идёт по дорожкам объявлений (пробел G3)")
                .isEmpty();
        assertThat(matching(violations(trancheLevel), INCOMPLETE))
                .as("та же конструкция на транше нарушение даёт")
                .hasSize(1);
    }

    @Test
    @DisplayName("U13.14 — тот же дефект у неторгуемой детали: покрытие не считается")
    void u13_14_theCoverageIsNotCheckedOnANonTradingDetail() {
        CreateStrategyApiRequest request = reference();
        StrategyTrancheApiModel tranche = newTranche("range_main", 1, false);
        tranche.setStepsByStatus(new LinkedHashMap<>(Map.of("PROTECTION_SWITCHED",
                List.of(bareCancelStep("range_cancel")))));
        tranches(range(request)).add(tranche);

        List<String> violations = violations(request);

        assertThat(matching(violations, INCOMPLETE)).isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL")).hasSize(1);
    }

    @Test
    @DisplayName("U13.15 — цель замещения в шаге другого объявления: набор ключей собирается по детали")
    void u13_15_aTargetInAnotherTrancheOfTheSameDetailResolves() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        StrategyAlgoOrderActionApiModel protection = newAlgo("second_protection", "CREATE_ACTION", "STOP_LOSS");
        protection.setCloseFractionPercents(decimal("100"));
        StrategyTrancheApiModel second = newTranche("bull_second", 1, false);
        second.setStepsByStatus(new LinkedHashMap<>(Map.of("ENTRY_FINALIZED",
                List.of(newStep("MAIN_PROTECTION", protection)))));
        tranches(detail).add(second);
        algoAction(detail, "bull_oco_cancel").setTargetActionKey("second_protection");

        List<String> violations = violations(request);

        assertThat(matching(violations, UNKNOWN_TARGET)).isEmpty();
        assertThat(matching(violations, INCOMPLETE)).isEmpty();
    }

    /** Шаг, забирающий всё покрытие и не возвращающий ничего. */
    private StrategyStepApiModel bareCancelStep(String actionKey) {
        StrategyAlgoOrderActionApiModel cancel = newAlgo(actionKey, "CANCEL_ACTION", "OCO_FULL");
        cancel.setTargetActionKey("bull_protection_oco");
        return newStep("FAIL_SAFE", cancel);
    }
}
