package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Встроенная защита входа — группа {@code U23} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»;
 * счётчик — docs/spec/strategy-reference.json, величина
 * {@code entryActionsWithoutAttachedProtection}).
 *
 * <p><b>Обязательность привязана к ТИПУ действия, а не к наличию
 * блока:</b> у простого входа блок допустим и не обязателен, у входа с
 * встроенной защитой — обязателен. Кейс предъявляет обе стороны: иначе
 * он был бы зелен у валидатора, требующего блок у всякого входа.
 */
class AttachedProtectionTest {

    private static final String REQUIRED = "attachedProtection is required for ENTRY_ATTACHED_STOP_LOSS";

    @Test
    @DisplayName("U23.1 — базовая сборка: вход с встроенной защитой несёт её блок")
    void u23_1_theReferenceEntryCarriesItsAttachedProtection() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U23.2 — блок защиты удалён у входа с встроенной защитой: он обязателен")
    void u23_2_theAttachedProtectionIsRequiredByTheOrderType() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAttachedProtection(null);

        assertThat(violations(request)).singleElement().asString().contains(REQUIRED);
    }

    @Test
    @DisplayName("U23.3 — тип сменён на простой вход, блок удалён: обязательности больше нет")
    void u23_3_aPlainEntryNeedsNoAttachedProtection() {
        CreateStrategyApiRequest request = reference();
        StrategyOrderActionApiModel entry = entryAction(bull(request));
        entry.setOrderType("ENTRY");
        entry.setAttachedProtection(null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U23.4 — тип встроенной защиты — неизвестная строка: отказ перечня")
    void u23_4_anUnknownAttachedTypeIsRejected() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).getAttachedProtection().setAttachedType("ATTACHED_TAKE_PROFIT");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".attachedProtection.attachedType: unknown value ATTACHED_TAKE_PROFIT");
    }

    @Test
    @DisplayName("U23.5 — способ расчёта в настройках встроенной защиты неизвестен: тот же обход")
    void u23_5_theAttachedStopSettingsAreTraversedToo() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).getAttachedProtection().getStopLossSettings()
                .setCalculationType("FIBONACCI");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".attachedProtection.stopLossSettings.calculationType: unknown value FIBONACCI");
    }

    @Test
    @DisplayName("U23.6 — блок встроенной защиты у ПРОСТОГО входа: он допустим")
    void u23_6_aPlainEntryMayStillCarryAnAttachedProtection() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setOrderType("ENTRY");

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U23.7 — ссылка на индикатор внутри встроенной защиты не резолвится")
    void u23_7_theAttachedStopReferenceIsResolvedToo() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).getAttachedProtection().getStopLossSettings()
                .setIndicatorKey("no_such_indicator");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".attachedProtection.stopLossSettings.indicatorKey")
                .contains("references unknown indicator setting key no_such_indicator");
    }
}
