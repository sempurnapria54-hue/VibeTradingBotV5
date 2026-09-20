package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Шаблон сетки: число уровней и смещение — группа {@code U8} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/models/domain/aggregate/Strategy.md §StrategyTranche: смещение
 * обязательно при числе уровней больше одного и запрещено иначе).
 *
 * <p><b>Число уровней — операнд ОБОИХ неравенств объёма</b>, и кейс,
 * поднимающий его, обязан называть их нарушения: веер детали множится на
 * число уровней, и объявленный нотинал — тоже. Кейс, называющий только
 * сетку, был бы неполон ровно на два нарушения.
 *
 * <p><b>Пустое число уровней умолчания не имеет</b> — единица
 * мажорировала бы его в разрешающую сторону, — и сверка шаблона на нём
 * не считается вовсе.
 */
class TrancheGridTest {

    private static final String STEP_MISSING = "STRATEGY_TRANCHE_LEVEL_STEP_MISSING";

    private static final String STEP_UNEXPECTED = "STRATEGY_TRANCHE_LEVEL_STEP_UNEXPECTED";

    private static final String COUNT_NOT_DECLARED = "STRATEGY_TRANCHE_LEVEL_COUNT_NOT_DECLARED";

    private static final String FAN = "STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE";

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

    @Test
    @DisplayName("U8.1 — один уровень без смещения: нарушений нет")
    void u8_1_aSingleLevelWithoutAStepIsLegal() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U8.2 — три уровня со смещением: сетка годна, оба неравенства объёма — нет")
    void u8_2_aWellFormedGridStillBreaksBothVolumeInequalities() {
        CreateStrategyApiRequest request = reference();
        grid(request, 3, "1");

        List<String> violations = violations(request);

        assertThat(matching(violations, "STRATEGY_TRANCHE_LEVEL_STEP")).isEmpty();
        assertThat(matching(violations, FAN)).hasSize(1);
        assertThat(matching(violations, HEADROOM)).hasSize(1);
        assertThat(violations).hasSize(2);
    }

    @Test
    @DisplayName("U8.3 — три уровня без смещения: три нарушения, и два из них — неравенства")
    void u8_3_aGridWithoutAStepAddsTheStructuralViolation() {
        CreateStrategyApiRequest request = reference();
        grid(request, 3, null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(3);
        assertThat(matching(violations, "details[0].tranches[0].levelStep " + STEP_MISSING)).hasSize(1);
        assertThat(matching(violations, FAN)).hasSize(1);
        assertThat(matching(violations, HEADROOM)).hasSize(1);
    }

    @Test
    @DisplayName("U8.4 — один уровень со смещением: смещать нечего")
    void u8_4_aStepWithoutAGridIsRejected() {
        CreateStrategyApiRequest request = reference();
        grid(request, 1, "1");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[0].levelStep " + STEP_UNEXPECTED);
    }

    @Test
    @DisplayName("U8.5 — число уровней опущено: сверка шаблона не считается")
    void u8_5_anAbsentLevelCountStopsTheGridCheck() {
        CreateStrategyApiRequest request = reference();
        grid(request, null, null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].tranches[0].levelCount " + COUNT_NOT_DECLARED);
        assertThat(matching(violations, "STRATEGY_TRANCHE_LEVEL_STEP")).isEmpty();
    }

    @Test
    @DisplayName("U8.6 — число уровней опущено при объявленном смещении: запрет не считается")
    void u8_6_anAbsentLevelCountIsNotMajorisedToOne() {
        CreateStrategyApiRequest request = reference();
        grid(request, null, "1");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(COUNT_NOT_DECLARED);
    }

    @Test
    @DisplayName("U8.7 — два уровня без смещения: сетка начинается со второго уровня")
    void u8_7_theGridBoundaryIsInclusiveAtTwoLevels() {
        CreateStrategyApiRequest request = reference();
        grid(request, 2, null);

        List<String> violations = violations(request);

        assertThat(matching(violations, STEP_MISSING)).hasSize(1);
        assertThat(matching(violations, FAN)).hasSize(1);
        assertThat(matching(violations, HEADROOM)).hasSize(1);
    }

    @Test
    @DisplayName("U8.8 — ноль уровней со смещением: ноль сеткой не считается")
    void u8_8_zeroLevelsIsNotAGrid() {
        CreateStrategyApiRequest request = reference();
        grid(request, 0, "1");

        assertThat(violations(request))
                .as("достижимость исключена позитивностью числа у поверхности")
                .singleElement()
                .asString()
                .contains(STEP_UNEXPECTED);
    }

    private void grid(CreateStrategyApiRequest request, Integer levelCount, String levelStep) {
        StrategyTrancheApiModel tranche = tranche(bull(request));
        tranche.setLevelCount(levelCount);
        tranche.setLevelStep(decimal(levelStep));
    }
}
