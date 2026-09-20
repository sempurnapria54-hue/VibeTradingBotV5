package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newStopSettings;
import static com.example.strategies.unit.validation.ValidationFixture.newTrailingSettings;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Источник уровня: ровно один блок настроек — группа {@code U20}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/rules/strategy-validation.md §«Что проверяется на создании»;
 * счётчик — docs/spec/strategy-reference.json, величина
 * {@code levelSourceAmbiguous}).
 *
 * <p><b>Сверка стои́т на НАЛИЧИИ блоков, а класс действия её не
 * выключает:</b> два блока у снимающего действия отвергаются так же, как
 * у ставящего. Обратная сторона названа домом и сегодня не проверяется —
 * ставящее уровень действие без единого блока проходит создание
 * (находка {@code F5}).
 */
class LevelSourceTest {

    private static final String AMBIGUOUS = "STRATEGY_LEVEL_SOURCE_AMBIGUOUS";

    @Test
    @DisplayName("U20.1 — базовая сборка: двух блоков нет ни у одного действия")
    void u20_1_noReferenceActionDeclaresBothBlocks() {
        assertThat(matching(violations(reference()), AMBIGUOUS)).isEmpty();
    }

    @Test
    @DisplayName("U20.2 — у трейлингового действия только блок трейлинга: источник резолвится")
    void u20_2_aTrailingOnlyActionResolvesItsLevel() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel trailing = algoAction(bull(request), "bull_trailing");

        assertThat(trailing.getStopLossSettings())
                .as("эталон объявляет у него ровно один блок")
                .isNull();
        assertThat(matching(violations(request), AMBIGUOUS)).isEmpty();
    }

    @Test
    @DisplayName("U20.3 — у первичной защиты только блок стопа: источник резолвится")
    void u20_3_aStopOnlyActionResolvesItsLevel() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel protection = algoAction(bull(request), "bull_protection_oco");

        assertThat(protection.getTrailingSettings()).isNull();
        assertThat(matching(violations(request), AMBIGUOUS)).isEmpty();
    }

    @Test
    @DisplayName("U20.4 — трейлинговому действию добавлен блок стопа: источник не резолвится")
    void u20_4_twoBlocksMakeTheLevelSourceAmbiguous() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_trailing").setStopLossSettings(newStopSettings());

        assertThat(violations(request)).singleElement().asString().contains(AMBIGUOUS);
    }

    @Test
    @DisplayName("U20.5 — оба блока у СНИМАЮЩЕГО действия: класс действия сверку не выключает")
    void u20_5_theCheckIsUnconditionalAcrossActionClasses() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel cancel = algoAction(bull(request), "bull_oco_cancel");
        cancel.setTrailingSettings(newTrailingSettings());
        cancel.setStopLossSettings(newStopSettings());

        assertThat(violations(request)).singleElement().asString().contains(AMBIGUOUS);
    }

    @Test
    @Tag("debt")
    @DisplayName("U20.6 — у защитного создания нет НИ ОДНОГО блока уровня: дом требует отказа (F5)")
    void u20_6_aLevelSettingActionWithoutASourceIsRejectedByTheHome() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setStopLossSettings(null);

        assertThat(violations(request))
                .as("ожидание из дома: ставящее уровень действие объявляет его источник")
                .isNotEmpty();
    }

    @Test
    @DisplayName("U20.7 — ни одного блока у СНИМАЮЩЕГО действия: снятие уровня не ставит вовсе")
    void u20_7_aCancellingActionNeedsNoLevelSource() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel cancel = algoAction(bull(request), "bull_oco_cancel");

        assertThat(cancel.getStopLossSettings()).isNull();
        assertThat(cancel.getTrailingSettings()).isNull();
        assertThat(violations(request)).isEmpty();
    }
}
