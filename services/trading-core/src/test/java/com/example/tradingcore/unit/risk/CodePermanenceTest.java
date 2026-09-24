package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.blockedVerdict;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.risk.DealRiskNumbers;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Бессрочность кода: признак рядом со значением — группа {@code U25}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/models/RiskCheckResult.md §«Бессрочность отказа»).
 *
 * <p><b>Клейм отсутствия второго носителя читается РЕФЛЕКСИЕЙ, а не
 * текстом:</b> «признак читается только у значения» есть утверждение о
 * множестве полей и методов, и счёт их из числа вхождений слова не
 * выводится.
 */
class CodePermanenceTest {

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("U25.1 — перечень обойдён целиком: признак объявлен у каждого значения")
    void u25_1_everyCodeDeclaresItsPermanence() {
        assertThat(Arrays.stream(RiskCheckCode.values()).map(RiskCheckCode::isPermanent))
                .as("умолчания нет ни у одного: конструктор перечня требует признак")
                .doesNotContainNull()
                .hasSize(RiskCheckCode.values().length);
    }

    @Test
    @DisplayName("U25.2 — превышение поактного потолка: бессрочный")
    void u25_2_thePerActionBreachIsPermanent() {
        assertPermanent(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U25.3 — неделимый лот: бессрочный")
    void u25_3_theIndivisibleLotCodeIsPermanent() {
        assertPermanent(RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET);
    }

    @Test
    @DisplayName("U25.4 — три агрегатных потолка и катастрофический нотинал: временны́е")
    void u25_4_theAggregateCeilingsAreTemporary() {
        assertTemporary(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED,
                RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U25.5 — незаданные числа риск-аппетита: временны́е")
    void u25_5_theUnassignedAppetiteNumbersAreTemporary() {
        assertTemporary(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U25.6 — стоящая ступень радиуса: временный")
    void u25_6_theStandingRungIsTemporary() {
        assertTemporary(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    @Test
    @DisplayName("U25.7 — набор риска под сворачиванием: временный")
    void u25_7_theCollapseWindowCodeIsTemporary() {
        assertTemporary(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE);
    }

    @Test
    @DisplayName("U25.8 — сокращённое покрытие: временный")
    void u25_8_theReducedCoverageCodeIsTemporary() {
        assertTemporary(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U25.9 — сторона уровня, размер и режим маржи: бессрочные")
    void u25_9_theLevelSideSizeAndMarginCodesArePermanent() {
        assertPermanent(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
        assertPermanent(RiskCheckCode.TAKE_PROFIT_INVALID_SIDE);
        assertPermanent(RiskCheckCode.SIZE_BELOW_MIN);
        assertPermanent(RiskCheckCode.SIZE_ABOVE_LIMIT);
        assertPermanent(RiskCheckCode.SIZE_LOT_STEP_INVALID);
        assertPermanent(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED);
    }

    @Test
    @DisplayName("U25.10 — неполнота графа: временный, и на реакцию это не влияет")
    void u25_10_theIncompleteGraphCodeIsTemporaryYetKeepsItsRow() {
        assertTemporary(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);

        assertThat(resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blockedVerdict(RiskCheckCode.DEAL_GRAPH_INCOMPLETE)).getType())
                .as("строка стои́т до разбора бессрочности")
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U25.12 — незаданное плечо пары и перенос до прохода цены: временные")
    void u25_12_theUnassignedLeverageAndTheDeferredTransferAreTemporary() {
        assertTemporary(RiskCheckCode.LEVERAGE_NOT_CONFIGURED,
                RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U25.11 — второго носителя признака нет: он читается только у значения")
    void u25_11_thePermanenceFlagHasNoSecondCarrier() {
        assertThat(nonStaticFieldNames(RiskCheckCode.class))
                .as("нестатическое поле у перечня ровно одно")
                .containsExactly("permanent");
        List<Class<?>> neighbours = List.of(RiskCheckResult.class, RiskValidationResult.class,
                RiskBlockAction.class, DealRiskNumbers.class);
        for (Class<?> neighbour : neighbours) {
            assertThat(namesOf(neighbour))
                    .as("сосед %s признака не объявляет", neighbour.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("permanent"));
        }
    }

    private static void assertPermanent(RiskCheckCode code) {
        assertThat(code.isPermanent()).as("код %s", code).isTrue();
    }

    private static void assertTemporary(RiskCheckCode... codes) {
        for (RiskCheckCode code : codes) {
            assertThat(code.isPermanent()).as("код %s", code).isFalse();
        }
    }

    /** Имена нестатических полей типа, объявленных им самим. */
    private static List<String> nonStaticFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .toList();
    }

    /** Имена полей и методов типа: предмет клейма об отсутствии второго носителя. */
    private static List<String> namesOf(Class<?> type) {
        return java.util.stream.Stream.concat(
                        Arrays.stream(type.getDeclaredFields()).map(Field::getName),
                        Arrays.stream(type.getDeclaredMethods()).map(Method::getName))
                .toList();
    }
}
