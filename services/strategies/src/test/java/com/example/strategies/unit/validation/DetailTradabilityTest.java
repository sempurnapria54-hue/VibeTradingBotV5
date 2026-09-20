package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newTranche;
import static com.example.strategies.unit.validation.ValidationFixture.range;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Признак торгуемости детали — группа {@code U4} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/models/domain/aggregate/Strategy.md §StrategyDetail; звено —
 * {@code StrategyDefinitionValidator#tradableDetail}).
 *
 * <p><b>Признак выключает ЧЕТЫРЕ проверки детали разом</b> — риск-числа,
 * объявления, оба неравенства объёма и покрытие защиты, — поэтому клетка
 * группы называет и то, чего в сообщении нет: молчание выключенной
 * проверки есть ожидание наравне с отказом.
 *
 * <p><b>Пустая политика и неизвестная строка признаком не разводятся.</b>
 * Обе делают деталь неторгуемой, и различает их только собственное
 * нарушение перечня.
 */
class DetailTradabilityTest {

    @Test
    @DisplayName("U4.1 — неторгуемая деталь без объявлений и риск-чисел нарушений не даёт")
    void u4_1_aNonTradingDetailWithoutNumbersIsClean() {
        List<String> violations = violations(reference());

        assertThat(matching(violations, "details[2]")).isEmpty();
        assertThat(matching(violations, "details[3]")).isEmpty();
    }

    @Test
    @DisplayName("U4.2 — объявление у неторгуемой детали: сетка и переоткрытие не проверяются")
    void u4_2_aTrancheOnANonTradingDetailIsNotTraversed() {
        CreateStrategyApiRequest request = reference();
        tranches(range(request)).add(newTranche("range_main", null, null));

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[2].tranches STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL");
        assertThat(matching(violations, "STRATEGY_TRANCHE_LEVEL_COUNT_NOT_DECLARED"))
                .as("число уровней у него не объявлено, и это не мерится")
                .isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_REOPEN_NOT_DECLARED")).isEmpty();
    }

    @Test
    @DisplayName("U4.3 — риск-числа у неторгуемой детали не мерятся наличием")
    void u4_3_riskNumbersOnANonTradingDetailAreIgnored() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = range(request);
        detail.setRiskPerActionPercent(decimal("1"));
        detail.setCumulativeRiskPerDealMultiplier(decimal("2"));
        detail.setStrategySimultaneousRiskPerDealPercent(decimal("1"));
        detail.setStrategyCatastrophicRiskPerDealMultiplier(decimal("100"));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U4.4 — потолок выше конфигурационного у неторгуемой детали не считается")
    void u4_4_theGlobalCeilingIsNotCheckedOnANonTradingDetail() {
        CreateStrategyApiRequest request = reference();
        range(request).setStrategySimultaneousRiskPerDealPercent(decimal("5"));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U4.5 — политика входа опущена: деталь стала неторгуемой, риск-числа молчат")
    void u4_5_anAbsentPolicyMakesADeclaredDetailNonTrading() {
        CreateStrategyApiRequest request = reference();
        bull(request).setPhaseEntryPolicy(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, "details[0].phaseEntryPolicy: unknown value null")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_RISK_NUMBER_NOT_DECLARED"))
                .as("числа объявлены, но у неторгуемой они не мерятся вовсе")
                .isEmpty();
    }

    @Test
    @DisplayName("U4.6 — политика — неизвестная строка: тот же исход, что у пустой")
    void u4_6_anUnknownPolicyIsIndistinguishableFromAnAbsentOne() {
        CreateStrategyApiRequest request = reference();
        bull(request).setPhaseEntryPolicy("AGGRESSIVE");

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, "details[0].phaseEntryPolicy: unknown value AGGRESSIVE")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL")).hasSize(1);
    }

    @Test
    @DisplayName("U4.7 — торгуемая деталь без объявлений: обход объявлений не начинается")
    void u4_7_aTradableDetailWithoutTranchesStopsTheTraversal() {
        CreateStrategyApiRequest request = reference();
        bull(request).setTranches(null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].tranches STRATEGY_TRANCHE_NOT_DECLARED");
        assertThat(matching(violations, "STRATEGY_ENTRY_DECLARATION_MISSING")).isEmpty();
    }
}
