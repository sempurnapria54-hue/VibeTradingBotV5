package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Трейлинг абсолютным откатом не объявляется — группа {@code U38} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»,
 * реджект {@code STRATEGY_CONDITION_TYPE_UNSUPPORTED}).
 *
 * <p><b>Мутируется тип условия у действия эталона, а не подкладывается
 * новое действие.</b> Эталон несёт все три вида действия над условной
 * заявкой — создание трейлинга, замещение защиты и её снятие, — и смена
 * одного лишь типа не задевает ни покрытия, ни источника уровня: отказ
 * остаётся единственным, и клетка мерит ровно его.
 */
class ConditionTypeSupportedTest {

    private static final String UNSUPPORTED = "STRATEGY_CONDITION_TYPE_UNSUPPORTED";
    private static final String MANAGING_PATH = "details[0].tranches[bull_main].stepsByStatus[MANAGING]";
    private static final String TRAILING_VALUE = "TRAILING_VALUE";
    private static final String TRAILING_PERCENTS = "TRAILING_PERCENTS";

    @Test
    @DisplayName("U38.1 — базовая сборка: трейлинг эталона объявлен процентным откатом, нарушений нет")
    void u38_1_theReferenceDeclaresOnlyPercentTrailing() {
        CreateStrategyApiRequest request = reference();

        assertThat(algoAction(bull(request), "bull_trailing").getConditionType()).isEqualTo(TRAILING_PERCENTS);
        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U38.2 — создание трейлинга абсолютным откатом: ровно одно нарушение типа")
    void u38_2_aValueTrailingCreationIsRejected() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_trailing").setConditionType(TRAILING_VALUE);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(MANAGING_PATH + "[1].actions[1].conditionType " + UNSUPPORTED);
    }

    @Test
    @DisplayName("U38.3 — замещение защиты типом абсолютного отката: ровно одно нарушение типа")
    void u38_3_aValueTrailingReplacementIsRejected() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setConditionType(TRAILING_VALUE);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(MANAGING_PATH + "[0].actions[0].conditionType " + UNSUPPORTED);
    }

    @Test
    @DisplayName("U38.4 — снятие с типом абсолютного отката: отвергается и у снимающего действия")
    void u38_4_aValueTrailingCancellationIsRejected() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_oco_cancel").setConditionType(TRAILING_VALUE);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(MANAGING_PATH + "[1].actions[0].conditionType " + UNSUPPORTED);
    }

    @Test
    @DisplayName("U38.5 — замещение и снятие с процентным трейлингом: нарушений нет")
    void u38_5_percentTrailingIsLegalOnEveryActionType() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setConditionType(TRAILING_PERCENTS);
        algoAction(bull(request), "bull_oco_cancel").setConditionType(TRAILING_PERCENTS);

        assertThat(violations(request)).isEmpty();
    }
}
