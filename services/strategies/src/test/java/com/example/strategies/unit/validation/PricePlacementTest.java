package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newPlacement;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyPricePlacementApiModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Размещение цены: база, структурный ключ, источник — группа {@code U22}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/models/domain/aggregate/Strategy.md §«StrategyPricePlacement —
 * правило расчёта цены размещения»).
 *
 * <p><b>Требование выводится из БАЗЫ:</b> структурная база требует ключ
 * структуры, рыночная — источник цены, а неразобранная база выключает
 * обе проверки. Клетка называет молчание выключенных: иначе она была бы
 * зелена у валидатора, требующего ключ всегда.
 */
class PricePlacementTest {

    @Test
    @DisplayName("U22.1 — базовая сборка: блок размещения не обязателен")
    void u22_1_aPlacementBlockIsOptional() {
        CreateStrategyApiRequest request = reference();

        assertThat(entryAction(bull(request)).getPlacement()).isNull();
        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U22.2 — база «цена входа»: ни ключ структуры, ни источник цены не требуются")
    void u22_2_theEntryPriceBaseRequiresNothingElse() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPlacement(newPlacement("ENTRY_PRICE"));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U22.3 — рыночная база с объявленным источником цены: нарушений нет")
    void u22_3_aMarketPriceBaseWithItsSourceIsLegal() {
        CreateStrategyApiRequest request = reference();
        StrategyPricePlacementApiModel placement = newPlacement("MARKET_PRICE");
        placement.setPriceSource("LAST_PRICE");
        entryAction(bull(request)).setPlacement(placement);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U22.4 — структурная база без ключа структуры: ссылка обязательна")
    void u22_4_aStructuralBaseRequiresItsStructureKey() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPlacement(newPlacement("RANGE_LOW"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".placement.structureKey is required and must reference a market structure setting");
    }

    @Test
    @DisplayName("U22.5 — структурная база с несуществующим ключом структуры")
    void u22_5_anUnknownStructureKeyInAPlacementDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        StrategyPricePlacementApiModel placement = newPlacement("SWING_HIGH");
        placement.setStructureKey("no_such_structure");
        entryAction(bull(request)).setPlacement(placement);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("references unknown market structure setting key no_such_structure");
    }

    @Test
    @DisplayName("U22.6 — рыночная база без источника цены: он обязателен для неё")
    void u22_6_aMarketPriceBaseRequiresItsPriceSource() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPlacement(newPlacement("MARKET_PRICE"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".placement.priceSource is required for MARKET_PRICE base");
    }

    @Test
    @DisplayName("U22.7 — база размещения — неизвестная строка: обе выводимые проверки молчат")
    void u22_7_anUnknownBaseTypeSilencesBothDerivedChecks() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPlacement(newPlacement("PIVOT"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".placement.baseType: unknown value PIVOT");
    }

    @Test
    @DisplayName("U22.8 — источник цены — марк-цена: отказ кодом недоступного источника")
    void u22_8_anUnavailablePriceSourceIsRejectedByTheHome() {
        CreateStrategyApiRequest request = reference();
        StrategyPricePlacementApiModel placement = newPlacement("MARKET_PRICE");
        placement.setPriceSource("MARK_PRICE");
        entryAction(bull(request)).setPlacement(placement);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".placement.priceSource STRATEGY_PRICE_SOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("U22.11 — источник цены — индексная цена: тот же отказ")
    void u22_11_theIndexPriceSourceIsRejectedAlike() {
        CreateStrategyApiRequest request = reference();
        StrategyPricePlacementApiModel placement = newPlacement("MARKET_PRICE");
        placement.setPriceSource("INDEX_PRICE");
        entryAction(bull(request)).setPlacement(placement);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("STRATEGY_PRICE_SOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("U22.9 — сторона смещения — неизвестная строка: отказ перечня")
    void u22_9_anUnknownOffsetSideIsRejected() {
        CreateStrategyApiRequest request = reference();
        StrategyPricePlacementApiModel placement = newPlacement("ENTRY_PRICE");
        placement.setOffsetSide("SIDEWAYS");
        entryAction(bull(request)).setPlacement(placement);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".placement.offsetSide: unknown value SIDEWAYS");
    }

    @Test
    @DisplayName("U22.10 — сторона смещения опущена: сверка идёт только при непустоте")
    void u22_10_anAbsentOffsetSideIsGuarded() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPlacement(newPlacement("ENTRY_PRICE"));

        assertThat(matching(violations(request), "offsetSide")).isEmpty();
    }
}
