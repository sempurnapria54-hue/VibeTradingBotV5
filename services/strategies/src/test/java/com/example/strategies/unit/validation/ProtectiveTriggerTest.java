package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * База срабатывания защиты — только марк-цена: группа {@code U19}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/models/domain/core/AlgoOrder.md; реджект —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»).
 *
 * <p><b>Носителей базы у защитного действия ДВА</b> — своё поле и
 * настройки стопа, — и охраняются они по-разному: собственная база
 * сверяется с перечнем только при непустоте, у настроек стопа сверка
 * безусловна. Клетка называет это различие: иначе пустая база читалась
 * бы одинаково у обоих носителей.
 *
 * <p><b>Область читается по СОБСТВЕННОМУ типу условия действия</b>,
 * включая снимающее: у снимающего тип лишь копирует тип цели, но решение
 * стои́т на том, что объявлено.
 */
class ProtectiveTriggerTest {

    private static final String NOT_MARK = "STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK";

    @Test
    @DisplayName("U19.1 — базовая сборка: у защитных действий и их настроек база — марк-цена")
    void u19_1_theReferenceTriggersEveryProtectionByMark() {
        assertThat(matching(violations(reference()), NOT_MARK)).isEmpty();
    }

    @Test
    @DisplayName("U19.2 — база защитного действия — последняя цена: отказ с полученным значением")
    void u19_2_aLastPriceTriggerOnAProtectiveActionIsRejected() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setTriggerPriceType("LAST");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(NOT_MARK)
                .contains("получено LAST");
    }

    @Test
    @DisplayName("U19.3 — база в настройках стопа — индексная цена: проверяются оба носителя")
    void u19_3_theStopSettingsTriggerIsCheckedToo() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").getStopLossSettings().setTriggerPriceType("INDEX");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stopLossSettings.triggerPriceType " + NOT_MARK);
    }

    @Test
    @DisplayName("U19.4 — обе базы не марк-цена: два нарушения с разными путями")
    void u19_4_bothCarriersReportTheirOwnPath() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel protective = algoAction(bull(request), "bull_protection_oco");
        protective.setTriggerPriceType("LAST");
        protective.getStopLossSettings().setTriggerPriceType("INDEX");

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_MARK)).hasSize(2);
    }

    @Test
    @DisplayName("U19.5 — собственная база опущена: пустая база проверку не поднимает")
    void u19_5_anAbsentOwnTriggerIsGuardedByEmptiness() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setTriggerPriceType(null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U19.6 — база в настройках стопа опущена: у них сверка перечня БЕЗУСЛОВНА")
    void u19_6_theStopSettingsTriggerIsCheckedEvenWhenAbsent() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").getStopLossSettings().setTriggerPriceType(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stopLossSettings.triggerPriceType: unknown value null");
    }

    @Test
    @DisplayName("U19.7 — тейк-профит с базой «последняя цена»: область ограничена защитами")
    void u19_7_aTakeProfitCarriesNoTriggerRestriction() {
        CreateStrategyApiRequest request = reference();
        StrategyAlgoOrderActionApiModel action = algoAction(bull(request), "bull_protection_oco");
        action.setConditionType("TAKE_PROFIT");
        action.setTriggerPriceType("LAST");

        assertThat(matching(violations(request), NOT_MARK)).isEmpty();
    }

    /**
     * Встроенная защита входа — защита, и её база доезжает до площадки:
     * ограничение действует и на неё. Прежде обход читал только условную
     * заявку, и вход с базой «последняя цена» проходил создание.
     */
    @ParameterizedTest
    @ValueSource(strings = {"LAST", "INDEX"})
    @DisplayName("U19.9 — база встроенной защиты входа не марк-цена: отказ с путём встроенной защиты")
    void u19_9_theAttachedProtectionTriggerIsCheckedToo(String triggerPriceType) {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).getAttachedProtection().getStopLossSettings()
                .setTriggerPriceType(triggerPriceType);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".attachedProtection.stopLossSettings.triggerPriceType " + NOT_MARK)
                .contains("получено " + triggerPriceType);
    }

    @Test
    @DisplayName("U19.8 — у СНИМАЮЩЕГО действия с защитным типом условия база не марк: отказ")
    void u19_8_aCancellingActionIsSelectedByItsOwnConditionType() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_oco_cancel").setTriggerPriceType("LAST");

        assertThat(violations(request)).singleElement().asString().contains(NOT_MARK);
    }
}
