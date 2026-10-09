package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.appetite;
import static com.example.strategies.unit.validation.ValidationFixture.baseAppetite;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Неравенства 1 и 3 — группа {@code U10}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/rules/strategy-validation.md §«Исключения: неравенства,
 * проверяемые на создании»).
 *
 * <p><b>Число ядра — операнд КАЖДОЙ торгуемой детали</b>, поэтому его
 * мутация поднимает нарушение у обеих торгуемых деталей эталона, а
 * мутация дерева — у одной. Клетка называет счёт явно: иначе она была бы
 * зелена у валидатора, проверяющего только первую деталь.
 *
 * <p><b>Конфигурационные числа приходят заданными всегда:</b> ответ ядра
 * без любого из них отвергает чтец чисел до валидации
 * (docs/rules/strategy-validation.md), и пустого числа валидатор не видит.
 */
class RiskWithinGlobalTest {

    private static final String SIMULTANEOUS_ABOVE = "STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL";

    private static final String CUMULATIVE_ABOVE = "STRATEGY_CUMULATIVE_MULTIPLIER_ABOVE_GLOBAL";

    private static final String NOT_DECLARED = "STRATEGY_RISK_NUMBER_NOT_DECLARED";

    @Test
    @DisplayName("U10.1 — объявленный максимум ниже конфигурационного: нарушений нет")
    void u10_1_aDeclaredCeilingBelowTheConfiguredOneIsLegal() {
        assertThat(violations(reference(), appetite("2", "2", "1"))).isEmpty();
    }

    @Test
    @DisplayName("U10.2 — объявленный максимум равен конфигурационному: граница включена")
    void u10_2_theBoundIsInclusive() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U10.3 — объявленный максимум выше конфигурационного: код первого неравенства")
    void u10_3_aDeclaredCeilingAboveTheConfiguredOneIsRejected() {
        CreateStrategyApiRequest request = reference();
        bull(request).setStrategySimultaneousRiskPerDealPercent(decimal("2"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].strategySimultaneousRiskPerDealPercent " + SIMULTANEOUS_ABOVE);
    }

    @Test
    @DisplayName("U10.4 — объявленный множитель выше предела: код третьего неравенства")
    void u10_4_aDeclaredMultiplierAboveTheConfiguredLimitIsRejected() {
        CreateStrategyApiRequest request = reference();
        bull(request).setCumulativeRiskPerDealMultiplier(decimal("3"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].cumulativeRiskPerDealMultiplier " + CUMULATIVE_ABOVE);
    }

    @Test
    @DisplayName("U10.5 — объявленный множитель равен пределу: граница включена")
    void u10_5_theMultiplierBoundIsInclusiveToo() {
        CreateStrategyApiRequest request = reference();
        bull(request).setCumulativeRiskPerDealMultiplier(decimal("2"));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U10.6 — оба объявления выше своих пределов: РАЗНЫЕ коды, адресность не теряется")
    void u10_6_bothInequalitiesCarryTheirOwnCode() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        detail.setStrategySimultaneousRiskPerDealPercent(decimal("2"));
        detail.setCumulativeRiskPerDealMultiplier(decimal("3"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, SIMULTANEOUS_ABOVE)).hasSize(1);
        assertThat(matching(violations, CUMULATIVE_ABOVE)).hasSize(1);
    }

    @Test
    @DisplayName("U10.10 — объявленное число пусто: сверка с конфигурационным не начинается")
    void u10_10_anAbsentDeclaredNumberStopsTheComparison() {
        CreateStrategyApiRequest request = reference();
        bull(request).setStrategySimultaneousRiskPerDealPercent(null);

        List<String> violations = matching(violations(request, baseAppetite()), "details[0]");

        assertThat(violations)
                .singleElement()
                .asString()
                .contains(NOT_DECLARED);
    }
}
