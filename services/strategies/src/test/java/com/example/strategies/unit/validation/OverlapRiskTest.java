package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Неравенство 2: веер объявлений против потолка детали — группа
 * {@code U11} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Исключения: неравенства,
 * проверяемые на создании»; исполнимая форма —
 * docs/spec/strategy-reference.json, величина
 * {@code overlapRiskSatisfiable}).
 *
 * <p><b>Эталон стои́т НА ГРАНИЦЕ этого неравенства:</b> один уровень при
 * поактном потолке {@code 1.0} и максимуме одновременного {@code 1.0}
 * дают произведение, равное потолку. Поэтому у группы обе стороны
 * наблюдаемы одной мутацией: сдвиг числа уровней роняет неравенство, а
 * встречное снижение поактного потолка возвращает его на границу.
 *
 * <p><b>Число одновременно живых считается СУММОЙ по объявлениям</b>, а
 * не максимумом, и пустое число уровней обнуляет счёт целиком — ни по
 * первому объявлению, ни частично.
 */
class OverlapRiskTest {

    private static final String FAN = "STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE";

    private static final String ABOVE_GLOBAL = "STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL";

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

    private static final String NOT_DECLARED = "STRATEGY_RISK_NUMBER_NOT_DECLARED";

    @Test
    @DisplayName("U11.1 — базовая сборка: произведение равно потолку, граница включена")
    void u11_1_theReferenceSitsExactlyOnTheBound() {
        assertThat(matching(violations(reference()), FAN)).isEmpty();
    }

    @Test
    @DisplayName("U11.2 — два уровня со смещением: веер выше потолка, и нотинал тоже")
    void u11_2_aTwoLevelGridBreaksTheFan() {
        CreateStrategyApiRequest request = reference();
        StrategyTrancheApiModel tranche = tranche(bull(request));
        tranche.setLevelCount(2);
        tranche.setLevelStep(decimal("1"));

        List<String> violations = violations(request);

        assertThat(matching(violations, FAN))
                .singleElement()
                .asString()
                .contains("веер детали (2 × 1.0) выше её максимума одновременного риска 1.0");
        assertThat(matching(violations, ABOVE_GLOBAL))
                .as("величины разведены по реджект-кодам")
                .isEmpty();
        assertThat(matching(violations, HEADROOM)).hasSize(1);
    }

    @Test
    @DisplayName("U11.3 — два объявления по уровню: число живых считается суммой, а не максимумом")
    void u11_3_theOverlapCountIsASumAcrossTranches() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(newTranche("bull_second", 1, false));

        List<String> violations = violations(request);

        assertThat(matching(violations, FAN))
                .singleElement()
                .asString()
                .contains("веер детали (2 × 1.0)");
    }

    @Test
    @DisplayName("U11.4 — два уровня при вдвое сниженном потолке: произведение снова равно потолку")
    void u11_4_aHalvedPerActionCeilingReturnsTheFanToTheBound() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        StrategyTrancheApiModel tranche = tranche(detail);
        tranche.setLevelCount(2);
        tranche.setLevelStep(decimal("1"));
        detail.setRiskPerActionPercent(decimal("0.5"));

        List<String> violations = violations(request);

        assertThat(matching(violations, FAN)).isEmpty();
        assertThat(matching(violations, HEADROOM))
                .as("поактный потолок в объявленный нотинал не входит")
                .hasSize(1);
    }

    @Test
    @DisplayName("U11.5 — поактный потолок опущен: веер не считается")
    void u11_5_anAbsentPerActionCeilingSilencesTheFan() {
        CreateStrategyApiRequest request = reference();
        bull(request).setRiskPerActionPercent(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_DECLARED)).hasSize(1);
        assertThat(matching(violations, FAN)).isEmpty();
    }

    @Test
    @DisplayName("U11.6 — число уровней опущено у второго объявления: счёт обнуляется целиком")
    void u11_6_anAbsentLevelCountVoidsTheWholeOverlapCount() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(newTranche("bull_second", null, false));

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].tranches[1].levelCount STRATEGY_TRANCHE_LEVEL_COUNT_NOT_DECLARED");
        assertThat(matching(violations, FAN))
                .as("ни по первому объявлению, ни частично")
                .isEmpty();
    }

    @Test
    @DisplayName("U11.7 — максимум одновременного риска опущен: веер не считается")
    void u11_7_anAbsentSimultaneousCeilingSilencesTheFan() {
        CreateStrategyApiRequest request = reference();
        bull(request).setStrategySimultaneousRiskPerDealPercent(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_DECLARED)).hasSize(1);
        assertThat(matching(violations, FAN)).isEmpty();
    }

    @Test
    @DisplayName("U11.8 — потолок и два уровня у неторгуемой детали: веер не считается")
    void u11_8_theFanIsNotCheckedOnANonTradingDetail() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = range(request);
        detail.setRiskPerActionPercent(decimal("1"));
        detail.setStrategySimultaneousRiskPerDealPercent(decimal("1"));
        StrategyTrancheApiModel tranche = newTranche("range_main", 2, false);
        tranche.setLevelStep(decimal("1"));
        tranches(detail).add(tranche);

        List<String> violations = violations(request);

        assertThat(matching(violations, "STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL")).hasSize(1);
        assertThat(matching(violations, FAN)).isEmpty();
    }

    @Test
    @DisplayName("U11.9 — веер выше потолка детали, а потолок выше конфигурационного: разные коды")
    void u11_9_theTwoCeilingViolationsCarryDifferentCodes() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        detail.setStrategySimultaneousRiskPerDealPercent(decimal("2"));
        StrategyTrancheApiModel tranche = tranche(detail);
        tranche.setLevelCount(3);
        tranche.setLevelStep(decimal("1"));

        List<String> violations = violations(request);

        assertThat(matching(violations, FAN)).hasSize(1);
        assertThat(matching(violations, ABOVE_GLOBAL)).hasSize(1);
    }
}
