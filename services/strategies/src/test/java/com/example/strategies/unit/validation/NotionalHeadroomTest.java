package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.appetite;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newOrder;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.newTranche;
import static com.example.strategies.unit.validation.ValidationFixture.range;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Неравенство 5: объявленный нотинал под катастрофическим потолком —
 * группа {@code U12} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/risk-policy.md §«Нотинал укладывается в потолок с запасом,
 * а не в границу»; исполнимая форма —
 * docs/spec/strategy-reference.json, величина
 * {@code notionalHeadroomSatisfied}).
 *
 * <p><b>Запас — КОНСТАНТА ПРАВИЛА, а не число конфигурации.</b> При
 * долях {@code 99} и {@code 100} под одними и теми же числами тенанта
 * исход разный, и это наблюдаемо только парой: одиночный прогон не
 * отличил бы «запас есть» от «потолок не считается».
 *
 * <p><b>Пустая доля аллокации нулём не читается</b> — иначе опустить
 * поле было бы ВЫГОДНЕЕ, чем объявить сто процентов.
 */
class NotionalHeadroomTest {

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

    private static final String FAN = "STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE";

    private static final String ALLOCATION_NOT_DECLARED = "STRATEGY_ACTION_ALLOCATION_NOT_DECLARED";

    private static final String NOT_DECLARED = "STRATEGY_RISK_NUMBER_NOT_DECLARED";

    private static final String NOT_CONFIGURED = "RISK_APPETITE_NOT_CONFIGURED";

    @Test
    @DisplayName("U12.1 — базовая сборка: объявленный нотинал оставляет запас")
    void u12_1_theReferenceLeavesHeadroom() {
        assertThat(matching(violations(reference()), HEADROOM)).isEmpty();
    }

    @Test
    @DisplayName("U12.2 — доля аллокации 99: объявленное равно допустимому ровно, граница включена")
    void u12_2_theHeadroomBoundIsInclusive() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("99"));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U12.3 — доля аллокации 100: запаса не остаётся, в тексте оба числа")
    void u12_3_aFullAllocationLeavesNoHeadroom() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("100"));

        assertThat(matching(violations(request), HEADROOM))
                .singleElement()
                .asString()
                .contains("details[0] " + HEADROOM)
                .contains("базы) не оставляет запаса под катастрофическим потолком (допустимо");
    }

    @Test
    @DisplayName("U12.4 — два уровня при доле 50: доля уровня множится на число уровней")
    void u12_4_theLevelShareIsMultipliedByTheLevelCount() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        StrategyTrancheApiModel tranche = tranche(detail);
        tranche.setLevelCount(2);
        tranche.setLevelStep(decimal("1"));
        entryAction(detail).setAllocationPercents(decimal("50"));
        detail.setRiskPerActionPercent(decimal("0.5"));

        List<String> violations = violations(request);

        assertThat(matching(violations, HEADROOM)).hasSize(1);
        assertThat(matching(violations, FAN))
                .as("веер снова равен потолку — предмет другой")
                .isEmpty();
    }

    @Test
    @DisplayName("U12.5 — доля аллокации опущена: пустая доля нулём не читается")
    void u12_5_anAbsentAllocationSilencesTheHeadroom() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, ALLOCATION_NOT_DECLARED)).hasSize(1);
        assertThat(matching(violations, HEADROOM)).isEmpty();
    }

    @Test
    @DisplayName("U12.6 — множитель катастрофического потолка опущен: запас не считается")
    void u12_6_anAbsentMultiplierSilencesTheHeadroom() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        detail.setStrategyCatastrophicRiskPerDealMultiplier(null);
        entryAction(detail).setAllocationPercents(decimal("100"));

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_DECLARED)).hasSize(1);
        assertThat(matching(violations, HEADROOM)).isEmpty();
    }

    @Test
    @DisplayName("U12.7 — конфигурационный потолок пуст: запас не считается")
    void u12_7_anUnconfiguredCeilingSilencesTheHeadroom() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("100"));

        List<String> violations = violations(request, appetite(null, "100"));

        assertThat(matching(violations, NOT_CONFIGURED)).isNotEmpty();
        assertThat(matching(violations, HEADROOM)).isEmpty();
    }

    @Test
    @DisplayName("U12.8 — два объявления с долями 95 и 50: доли складываются")
    void u12_8_theSharesOfTwoTranchesAreSummed() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(entryTranche("bull_second", "50"));

        assertThat(matching(violations(request), HEADROOM))
                .singleElement()
                .asString()
                .contains("1.45");
    }

    @Test
    @DisplayName("U12.9 — доля запаса не приходит ни одним числом тенанта: она константа правила")
    void u12_9_theHeadroomShareIsARuleConstant() {
        CreateStrategyApiRequest atBound = reference();
        entryAction(bull(atBound)).setAllocationPercents(decimal("99"));

        CreateStrategyApiRequest above = reference();
        entryAction(bull(above)).setAllocationPercents(decimal("100"));

        assertThat(matching(violations(atBound), HEADROOM)).isEmpty();
        assertThat(matching(violations(above), HEADROOM))
                .as("одна сотая потолка обязана остаться свободной, и поля конфигурации у неё нет")
                .hasSize(1);
    }

    @Test
    @DisplayName("U12.10 — полная доля у неторгуемой детали: запас не считается")
    void u12_10_theHeadroomIsNotCheckedOnANonTradingDetail() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = range(request);
        detail.setStrategyCatastrophicRiskPerDealMultiplier(decimal("100"));
        tranches(detail).add(entryTranche("range_main", "100"));

        List<String> violations = violations(request);

        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL")).hasSize(1);
        assertThat(matching(violations, HEADROOM)).isEmpty();
    }

    @Test
    @DisplayName("U12.11 — простой вход вместо входа с встроенной защитой: в нотинал входят ОБЕ формы")
    void u12_11_bothEntryOrderTypesCountTowardsTheNotional() {
        CreateStrategyApiRequest legal = reference();
        entryAction(bull(legal)).setOrderType("ENTRY");

        CreateStrategyApiRequest above = reference();
        entryAction(bull(above)).setOrderType("ENTRY");
        entryAction(bull(above)).setAllocationPercents(decimal("100"));

        assertThat(violations(legal)).isEmpty();
        assertThat(matching(violations(above), HEADROOM))
                .as("доля простого входа считается по-прежнему")
                .hasSize(1);
    }

    @Test
    @DisplayName("U12.12 — тип заявки — неизвестная строка: доля действия в нотинал не входит")
    void u12_12_anUnknownOrderTypeDropsItsShareFromTheNotional() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setOrderType("MARKET");
        entryAction(bull(request)).setAllocationPercents(decimal("100"));

        List<String> violations = violations(request);

        assertThat(matching(violations, "details[0].tranches[bull_main].stepsByStatus[PRECHECK][0]"
                + ".actions[0].orderType: unknown value MARKET")).hasSize(1);
        assertThat(matching(violations, HEADROOM))
                .as("направление разрешающее, но создание уже отвергнуто нарушением перечня (пробел G2)")
                .isEmpty();
    }

    /** Объявление с одним уровнем и одним входным действием названной доли. */
    private StrategyTrancheApiModel entryTranche(String key, String allocation) {
        StrategyTrancheApiModel tranche = newTranche(key, 1, false);
        tranche.setStepsByStatus(new LinkedHashMap<>(Map.of("PRECHECK",
                List.of(newStep("ENTRY", newOrder(key + "_entry", "ENTRY", "LONG", allocation))))));
        return tranche;
    }
}
