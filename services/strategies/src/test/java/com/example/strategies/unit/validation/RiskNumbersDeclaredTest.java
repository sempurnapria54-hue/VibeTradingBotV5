package com.example.strategies.unit.validation;

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
 * Четыре риск-числа торгуемой детали — группа {@code U9} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»).
 *
 * <p><b>Пустое число выключает неравенство, в котором оно операнд.</b>
 * Подстановка умолчания мажорировала бы неравенство в разрешающую
 * сторону, поэтому у каждой клетки две половины: нарушение
 * обязательности и молчание неравенства.
 *
 * <p><b>Обязательность мерит НАЛИЧИЕ, а не величину</b> — ноль и
 * отрицательное число её проходят, и структурной охраны знака у
 * поактного потолка не объявляет ни дом, ни аннотация (пробел
 * {@code G1} документа).
 */
class RiskNumbersDeclaredTest {

    private static final String NOT_DECLARED = "STRATEGY_RISK_NUMBER_NOT_DECLARED";

    private static final String FAN = "STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE";

    private static final String ABOVE_GLOBAL = "ABOVE_GLOBAL";

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

    @Test
    @DisplayName("U9.1 — базовая сборка: все четыре числа объявлены")
    void u9_1_theReferenceDeclaresAllFourNumbers() {
        assertThat(matching(violations(reference()), NOT_DECLARED)).isEmpty();
    }

    @Test
    @DisplayName("U9.2 — поактный потолок опущен: веер не считается")
    void u9_2_anAbsentPerActionCeilingSilencesTheFan() {
        CreateStrategyApiRequest request = reference();
        bull(request).setRiskPerActionPercent(null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].riskPerActionPercent " + NOT_DECLARED);
        assertThat(matching(violations, FAN)).isEmpty();
    }

    @Test
    @DisplayName("U9.3 — множитель кумулятивного потолка опущен: в неравенствах создания его нет")
    void u9_3_theCumulativeMultiplierTakesPartInNoInequality() {
        CreateStrategyApiRequest request = reference();
        bull(request).setCumulativeRiskPerDealMultiplier(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].cumulativeRiskPerDealMultiplier " + NOT_DECLARED);
    }

    @Test
    @DisplayName("U9.4 — максимум одновременного риска опущен: ни сверка с конфигурационным, ни веер")
    void u9_4_anAbsentSimultaneousCeilingSilencesTwoChecks() {
        CreateStrategyApiRequest request = reference();
        bull(request).setStrategySimultaneousRiskPerDealPercent(null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].strategySimultaneousRiskPerDealPercent " + NOT_DECLARED);
        assertThat(matching(violations, ABOVE_GLOBAL)).isEmpty();
        assertThat(matching(violations, FAN)).isEmpty();
    }

    @Test
    @DisplayName("U9.5 — множитель катастрофического потолка опущен: запас нотинала не считается")
    void u9_5_anAbsentCatastrophicMultiplierSilencesTheHeadroom() {
        CreateStrategyApiRequest request = reference();
        bull(request).setStrategyCatastrophicRiskPerDealMultiplier(null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].strategyCatastrophicRiskPerDealMultiplier " + NOT_DECLARED);
        assertThat(matching(violations, HEADROOM)).isEmpty();
    }

    @Test
    @DisplayName("U9.6 — опущены все четыре: четыре пути в порядке проверки, неравенств нет")
    void u9_6_allFourNumbersAreReportedInCheckOrder() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        detail.setRiskPerActionPercent(null);
        detail.setCumulativeRiskPerDealMultiplier(null);
        detail.setStrategySimultaneousRiskPerDealPercent(null);
        detail.setStrategyCatastrophicRiskPerDealMultiplier(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(4);
        assertThat(violations.get(0)).contains("details[0].riskPerActionPercent");
        assertThat(violations.get(1)).contains("details[0].cumulativeRiskPerDealMultiplier");
        assertThat(violations.get(2)).contains("details[0].strategySimultaneousRiskPerDealPercent");
        assertThat(violations.get(3)).contains("details[0].strategyCatastrophicRiskPerDealMultiplier");
    }

    @Test
    @DisplayName("U9.7 — у неторгуемой детали числа опущены: нарушений нет")
    void u9_7_aNonTradingDetailNeedsNoNumbers() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U9.8 — поактный потолок объявлен нулём: обязательность мерит наличие")
    void u9_8_aZeroPerActionCeilingPassesTheDeclarationCheck() {
        CreateStrategyApiRequest request = reference();
        bull(request).setRiskPerActionPercent(decimal("0"));

        assertThat(violations(request))
                .as("нулевой веер в потолок укладывается")
                .isEmpty();
    }

    @Test
    @DisplayName("U9.9 — поактный потолок отрицателен: структурной охраны знака нет ни одной")
    void u9_9_aNegativePerActionCeilingPassesEveryCheck() {
        CreateStrategyApiRequest request = reference();
        bull(request).setRiskPerActionPercent(decimal("-1"));

        assertThat(violations(request))
                .as("пробел G1: границу поактного потолка не называет ни дом, ни аннотация")
                .isEmpty();
    }
}
