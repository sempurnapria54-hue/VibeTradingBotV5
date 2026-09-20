package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newTranche;
import static com.example.strategies.unit.validation.ValidationFixture.range;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Объявления траншей: наличие, запрет у неторгуемой, уникальность ключа
 * — группа {@code U5} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/models/domain/aggregate/Strategy.md §StrategyTranche).
 *
 * <p><b>Второе объявление двигает оба неравенства объёма</b> — веер
 * детали и объявленный нотинал считаются суммой по объявлениям, — и
 * поэтому утверждения группы ограничены её собственным предметом:
 * нарушения неравенств предъявляют группы {@code U11} и {@code U12}.
 */
class TrancheDeclarationTest {

    private static final String DUPLICATE_KEY = "duplicate tranche key";

    @Test
    @DisplayName("U5.1 — базовая сборка: нарушений по объявлениям нет")
    void u5_1_theReferenceDeclaresOneWellFormedTranchePerTradableDetail() {
        List<String> violations = violations(reference());

        assertThat(matching(violations, DUPLICATE_KEY)).isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_NOT_DECLARED")).isEmpty();
    }

    @Test
    @DisplayName("U5.2 — объявления торгуемой детали удалены: обходить нечего")
    void u5_2_removedTranchesLeaveOneViolationOnly() {
        CreateStrategyApiRequest request = reference();
        bull(request).setTranches(null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("details[0].tranches STRATEGY_TRANCHE_NOT_DECLARED");
        assertThat(matching(violations, "STRATEGY_TRANCHE_LEVEL")).isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_REOPEN_NOT_DECLARED")).isEmpty();
    }

    @Test
    @DisplayName("U5.3 — список объявлений пуст: пустой и отсутствующий не разведены")
    void u5_3_anEmptyTrancheListIsIndistinguishableFromAnAbsentOne() {
        CreateStrategyApiRequest request = reference();
        bull(request).setTranches(new ArrayList<>());

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches STRATEGY_TRANCHE_NOT_DECLARED");
    }

    @Test
    @DisplayName("U5.4 — второе объявление несёт ключ первого: дубль с названным ключом")
    void u5_4_aReusedTrancheKeyIsRejected() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(newTranche("bull_main", 1, false));

        assertThat(matching(violations(request), DUPLICATE_KEY + " bull_main"))
                .singleElement()
                .asString()
                .contains("details[0].tranches[1]");
    }

    @Test
    @DisplayName("U5.5 — ключ второго объявления опущен: пустые ключи между собой не сверяются")
    void u5_5_absentTrancheKeysAreNotComparedToEachOther() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(newTranche(null, 1, false));

        assertThat(matching(violations(request), DUPLICATE_KEY))
                .as("достижимость исключена непустотой ключа у поверхности")
                .isEmpty();
    }

    @Test
    @DisplayName("U5.6 — объявление у неторгуемой детали: ни сетка, ни переоткрытие, ни вход")
    void u5_6_aTrancheOnANonTradingDetailIsOnlyForbidden() {
        CreateStrategyApiRequest request = reference();
        tranches(range(request)).add(newTranche("range_main", null, null));

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains("STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL");
        assertThat(matching(violations, "STRATEGY_ENTRY_DECLARATION_MISSING")).isEmpty();
    }

    @Test
    @DisplayName("U5.7 — два годных объявления с разными ключами: по объявлениям нарушений нет")
    void u5_7_twoDistinctlyKeyedTranchesAreBothLegal() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(newTranche("bull_second", 1, false));

        List<String> violations = violations(request);

        assertThat(matching(violations, DUPLICATE_KEY)).isEmpty();
        assertThat(matching(violations, "STRATEGY_TRANCHE_NOT_DECLARED")).isEmpty();
        assertThat(matching(violations, "STRATEGY_ENTRY_DECLARATION_MISSING"))
                .as("вход первого объявления требование детали выполняет")
                .isEmpty();
        assertThat(matching(violations, "STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE"))
                .as("оба объявления входят в веер — предмет группы U11")
                .hasSize(1);
    }
}
