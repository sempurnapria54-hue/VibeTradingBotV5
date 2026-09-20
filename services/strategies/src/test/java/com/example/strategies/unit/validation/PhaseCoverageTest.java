package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.bullDetailCopy;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.range;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.unknownPhase;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Покрытие фаз деталями — группа {@code U2} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»:
 * ровно одна деталь на каждый тип рыночной фазы, неторгуемая фаза —
 * явной деталью).
 *
 * <p><b>Пустой тип фазы дублем не считается, а непокрытой фазой —
 * считается.</b> Деталь с неразобранным типом в множество увиденных не
 * попадает, поэтому её фаза остаётся без детали, а дубля не возникает:
 * две стороны одного механизма, и кейс называет обе.
 */
class PhaseCoverageTest {

    private static final String DUPLICATE = "duplicate detail for marketPhaseType";

    private static final String MISSING = "missing detail for marketPhaseType";

    @Test
    @DisplayName("U2.1 — базовая сборка: нарушений покрытия нет")
    void u2_1_theReferenceCoversEveryPhaseExactlyOnce() {
        List<String> violations = violations(reference());

        assertThat(matching(violations, DUPLICATE)).isEmpty();
        assertThat(matching(violations, MISSING)).isEmpty();
    }

    @Test
    @DisplayName("U2.2 — тип фазы детали заменён на уже объявленный: дубль плюс непокрытая фаза")
    void u2_2_aReusedPhaseTypeBothDuplicatesAndUncovers() {
        CreateStrategyApiRequest request = reference();
        range(request).setMarketPhaseType("BULL_TREND");

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, DUPLICATE + " BULL_TREND")).hasSize(1);
        assertThat(matching(violations, MISSING + " RANGE")).hasSize(1);
    }

    @Test
    @DisplayName("U2.3 — деталь неизвестной фазы удалена: одна непокрытая фаза и ни одного дубля")
    void u2_3_aRemovedDetailLeavesItsPhaseUncovered() {
        CreateStrategyApiRequest request = reference();
        request.getDetails().remove(unknownPhase(request));

        List<String> violations = violations(request);

        assertThat(violations).singleElement().asString().contains(MISSING + " UNKNOWN");
        assertThat(matching(violations, DUPLICATE)).isEmpty();
    }

    @Test
    @DisplayName("U2.4 — тип фазы детали опущен: значение вне перечня плюс непокрытая фаза")
    void u2_4_anAbsentPhaseTypeIsNeitherParsedNorCounted() {
        CreateStrategyApiRequest request = reference();
        range(request).setMarketPhaseType(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, "details[2].marketPhaseType: unknown value null")).hasSize(1);
        assertThat(matching(violations, MISSING + " RANGE")).hasSize(1);
        assertThat(matching(violations, DUPLICATE))
                .as("пустота дублем не считается: в множество увиденных она не попадает")
                .isEmpty();
    }

    @Test
    @DisplayName("U2.5 — тип фазы детали — неизвестная строка: тот же исход, что у пустого")
    void u2_5_anUnknownPhaseTypeIsIndistinguishableFromAnAbsentOne() {
        CreateStrategyApiRequest request = reference();
        range(request).setMarketPhaseType("SIDEWAYS");

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, "details[2].marketPhaseType: unknown value SIDEWAYS")).hasSize(1);
        assertThat(matching(violations, MISSING + " RANGE")).hasSize(1);
    }

    @Test
    @DisplayName("U2.6 — пятая деталь с уже объявленным типом: только дубль, непокрытых фаз нет")
    void u2_6_aFifthDetailOnlyDuplicates() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel extra = new StrategyDetailApiModel();
        extra.setMarketPhaseType("BULL_TREND");
        extra.setPhaseEntryPolicy("NO_TRADE");
        request.getDetails().add(extra);

        List<String> violations = violations(request);

        assertThat(violations).singleElement().asString().contains(DUPLICATE + " BULL_TREND");
    }

    @Test
    @DisplayName("U2.7 — неторгуемая фаза покрывается ДЕТАЛЬЮ, а не отсутствием")
    void u2_7_aNonTradingPhaseIsCoveredByAnExplicitDetail() {
        assertThat(matching(violations(reference()), MISSING))
                .as("обе неторгуемые детали объявлены явным отказом от торговли")
                .isEmpty();

        CreateStrategyApiRequest without = reference();
        StrategyDetailApiModel rangeDetail = range(without);
        StrategyDetailApiModel unknownDetail = unknownPhase(without);
        without.getDetails().remove(rangeDetail);
        without.getDetails().remove(unknownDetail);

        assertThat(matching(violations(without), MISSING))
                .as("сняв их, получаем ровно две непокрытые фазы — отсутствие покрытием не является")
                .hasSize(2);
    }

    @Test
    @DisplayName("U2.8 — две детали одного типа с годными политиками: дубль обхода не прерывает")
    void u2_8_bothDuplicatedDetailsAreStillTraversed() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel copy = bullDetailCopy();
        copy.setRiskPerActionPercent(null);
        request.getDetails().add(copy);

        List<String> violations = violations(request);

        assertThat(matching(violations, DUPLICATE + " BULL_TREND")).hasSize(1);
        assertThat(matching(violations, "details[4].riskPerActionPercent"))
                .as("вторая деталь проверяется дальше целиком")
                .hasSize(1);
        assertThat(matching(violations, MISSING)).isEmpty();
    }
}
