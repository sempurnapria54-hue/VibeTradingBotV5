package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.appetite;
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
 * Неравенства 1 и 3 и пустое конфигурационное число — группа {@code U10}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/rules/strategy-validation.md §«Исключения: неравенства,
 * проверяемые на создании»).
 *
 * <p><b>Число ядра — операнд КАЖДОЙ торгуемой детали</b>, поэтому его
 * мутация поднимает нарушение у обеих торгуемых деталей эталона, а
 * мутация дерева — у одной. Клетка называет счёт явно: иначе она была бы
 * зелена у валидатора, проверяющего только первую деталь.
 *
 * <p><b>Незаданное конфигурационное число отвергает создание, а не
 * пропускает его:</b> сверять объявление автора не с чем, и пропуск был
 * бы разрешающей ошибкой ровно там, где стои́т охрана.
 */
class RiskWithinGlobalTest {

    private static final String SIMULTANEOUS_ABOVE = "STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL";

    private static final String CUMULATIVE_ABOVE = "STRATEGY_CUMULATIVE_MULTIPLIER_ABOVE_GLOBAL";

    /** Код незаданного конфигурационного числа — дом называет его с префиксом сущности (находка F2). */
    private static final String NOT_CONFIGURED = "STRATEGY_RISK_APPETITE_NOT_CONFIGURED";

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

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
    @DisplayName("U10.7 — конфигурационный потолок пуст: код с общим префиксом сущности")
    void u10_7_anUnconfiguredCeilingIsRejectedByItsNamedCode() {
        List<String> violations = violations(reference(), appetite(null, "2", "1"));

        assertThat(matching(violations, "details[0].strategySimultaneousRiskPerDealPercent " + NOT_CONFIGURED))
                .as("код несёт общий префикс сущности")
                .hasSize(1);
        assertThat(matching(violations, HEADROOM))
                .as("запас нотинала от потолка одновременного риска не зависит")
                .isEmpty();
    }

    @Test
    @DisplayName("U10.8 — конфигурационный предел множителя пуст: тот же код на своём пути")
    void u10_8_anUnconfiguredMultiplierLimitIsRejectedToo() {
        List<String> violations = violations(reference(), appetite("1", null, "1"));

        assertThat(matching(violations,
                "details[0].cumulativeRiskPerDealMultiplier " + NOT_CONFIGURED)).hasSize(1);
    }

    @Test
    @DisplayName("U10.9 — пусты все три конфигурационных числа: по нарушению на путь у каждой детали")
    void u10_9_allUnconfiguredNumbersAreReportedPerDetail() {
        List<String> violations = violations(reference(), appetite(null, null, null));

        assertThat(matching(violations, NOT_CONFIGURED))
                .as("две торгуемые детали, по три пути у каждой: два объявленных числа и сама деталь")
                .hasSize(6);
        assertThat(matching(violations, HEADROOM))
                .as("запас нотинала на пустом пределе плеча не считается")
                .isEmpty();
    }

    @Test
    @DisplayName("U10.10 — пусты и конфигурационное, и объявленное: сверка не начинается вовсе")
    void u10_10_anAbsentDeclaredNumberStopsTheComparison() {
        CreateStrategyApiRequest request = reference();
        bull(request).setStrategySimultaneousRiskPerDealPercent(null);

        List<String> violations = matching(violations(request, appetite(null, "2", "1")), "details[0]");

        assertThat(violations)
                .singleElement()
                .asString()
                .contains(NOT_DECLARED);
    }

    @Test
    @DisplayName("U10.11 — все конфигурационные числа пусты: охрана второго рубежа стои́т")
    void u10_11_theSecondLineGuardRejectsAnUnconfiguredTenant() {
        assertThat(violations(reference(), appetite(null, null, null)))
                .as("достижимость исключена читателем чисел: он отвергает вызов своим кодом раньше")
                .isNotEmpty();
    }
}
