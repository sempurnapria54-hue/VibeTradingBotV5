package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.baseAppetite;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.bear;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.indicator;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rejection;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Форма отказа создания — группа {@code U1} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Линия реза»).
 *
 * <p><b>Выход предмета — ТЕКСТ одного отказа, склеенный из всех
 * нарушений дерева.</b> Кейс, называющий только код, зелен у валидатора,
 * потерявшего путь: один и тот же код поднимается на разных узлах. Кейс,
 * называющий только путь, зелен у валидатора, сменившего код. Поэтому у
 * каждой клетки группы две несущие величины — адрес узла и текст
 * нарушения.
 *
 * <p><b>Молчание проверки — такое же ожидание, как отказ.</b> Обход
 * накапливает нарушения, но внутри узлов стоя́т локальные возвраты:
 * проверка, до которой обход не доехал, в сообщении отсутствует, и это
 * утверждение о предмете, а не пропуск кейса.
 */
class RejectionFormTest {

    @Test
    @DisplayName("U1.1 — базовая сборка: отказа нет вовсе, метод возвращается молча")
    void u1_1_theReferenceRaisesNoRejectionAtAll() {
        assertThat(violations(reference()))
                .as("иначе мутации мерили бы отказ, который стоял и без них")
                .isEmpty();
    }

    @Test
    @DisplayName("U1.2 — одна испорченная ось: ровно одно нарушение, и оно несёт путь до узла")
    void u1_2_oneBrokenAxisYieldsOneViolationCarryingTheNodePath() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setLevelStep(decimal("1"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .as("адресность отказа: индекс детали, индекс объявления, имя поля")
                .contains("details[0].tranches[0].levelStep");
    }

    @Test
    @DisplayName("U1.3 — две оси в разных деталях: оба нарушения, деталь меньшего индекса раньше")
    void u1_3_twoDetailsAreReportedInTraversalOrder() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setPositionReopenAllowed(null);
        tranche(bear(request)).setPositionReopenAllowed(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(violations.get(0)).contains("details[0].tranches[0].positionReopenAllowed");
        assertThat(violations.get(1)).contains("details[1].tranches[0].positionReopenAllowed");
    }

    @Test
    @DisplayName("U1.4 — настройка каталога и деталь: нарушение каталога стои́т раньше детального")
    void u1_4_catalogueViolationsPrecedeDetailOnes() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setExpirationDuration("два часа");
        bull(request).setRiskPerActionPercent(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(violations.get(0))
                .as("обход идёт настройки — классификация фазы — детали")
                .contains("strategy.indicatorSettings")
                .contains("invalid ISO-8601 duration");
        assertThat(violations.get(1)).contains("details[0].riskPerActionPercent");
    }

    @Test
    @DisplayName("U1.5 — список деталей пуст: четыре нарушения покрытия и ни одного о траншах")
    void u1_5_anEmptyDetailListYieldsOnlyCoverageViolations() {
        CreateStrategyApiRequest request = reference();
        request.setDetails(new java.util.ArrayList<>());

        List<String> violations = violations(request);

        assertThat(violations).hasSize(4);
        assertThat(matching(violations, "missing detail for marketPhaseType"))
                .as("по одному нарушению на тип фазы")
                .hasSize(4);
        assertThat(matching(violations, "STRATEGY_TRANCHE"))
                .as("деталей, к которым относятся транши, нет вовсе")
                .isEmpty();
    }

    @Test
    @DisplayName("U1.6 — деталей нет вовсе: валидатор молчит целиком (охрана второго рубежа)")
    void u1_6_absentDetailsSilenceTheWholeTraversal() {
        CreateStrategyApiRequest request = reference();
        request.setDetails(null);

        assertThat(violations(request))
                .as("достижимость исключена непустотой поля у поверхности")
                .isEmpty();
    }

    @Test
    @DisplayName("U1.7 — настройки классификации фазы нет: её правила молчат, детали проверяются")
    void u1_7_anAbsentPhaseSettingSilencesOnlyItsOwnRules() {
        CreateStrategyApiRequest request = reference();
        request.setMarketPhaseSetting(null);
        bull(request).setRiskPerActionPercent(null);

        List<String> violations = violations(request);

        assertThat(violations).singleElement().asString().contains("details[0].riskPerActionPercent");
        assertThat(matching(violations, "marketPhaseSetting")).isEmpty();
    }

    @Test
    @DisplayName("U1.8 — каталогов настроек нет: пустой каталог ссылку из-под проверки не выводит")
    void u1_8_anEmptyCatalogueStillResolvesReferences() {
        CreateStrategyApiRequest request = reference();
        request.setIndicatorSettings(null);
        request.setMarketStructureSettings(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, "references unknown indicator setting key"))
                .as("операнд ссылается на ключ, которого в пустой карте нет")
                .isNotEmpty();
        assertThat(matching(violations, "references unknown market structure setting key")).isNotEmpty();
    }

    @Test
    @DisplayName("U1.9 — статус отказа создания — 400: место ошибки конфигурации это создание")
    void u1_9_theRejectionStatusIsBadRequest() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setLevelStep(decimal("1"));

        assertThat(rejection(request, baseAppetite()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("U1.10 — две оси внутри одной детали: локальные возвраты деталь целиком не выключают")
    void u1_10_localReturnsDoNotSilenceTheWholeDetail() {
        CreateStrategyApiRequest request = reference();
        bull(request).setRiskPerActionPercent(null);
        tranche(bull(request)).setLevelStep(decimal("1"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(violations.get(0)).contains("STRATEGY_RISK_NUMBER_NOT_DECLARED");
        assertThat(violations.get(1)).contains("STRATEGY_TRANCHE_LEVEL_STEP_UNEXPECTED");
    }
}
