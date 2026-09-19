package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.edgeSpikeWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.monotoneUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.structureParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Структура — окно, свинг-пивоты и их моменты: группа `U11` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/MarketStructure.md §«Семантика классификации
 * (как считается)» — пункты о пивотах и о моменте подтверждения, §Структура
 * — поля каркаса).
 *
 * <p><b>Базовая сборка:</b> ряд закрытых свечей окна по возрастанию
 * открытия; параметры структуры со <b>всеми</b> объявленными числами;
 * готовые скаляры входов не поданы (их ветвь мерит `U12`).
 * Коллабораторов нет.
 *
 * <p><b>Окно базовой сборки</b> даёт свинг-максимумы {@code 120,122,124,126}
 * и свинг-минимумы {@code 85,87,89} при глубине поиска {@code 1}; краевые
 * бары пивотами не становятся по построению.
 *
 * <p>Клетки `U11.7` (равные максимумы соседей — дом о строгости сравнения
 * молчит) и `U11.8` (нулевая глубина поиска — параметр не проверен никем,
 * M-7) кода не получили.
 */
class StructureSwingPivotsTest {

    private final MarketStructureResolver resolver = new MarketStructureResolver();

    /** Границы окна выводятся из первого и последнего бара, оба в UTC. */
    @Test
    @DisplayName("U11.1 — базовая сборка: начало окна — бар 0, конец — бар 8, оба в UTC")
    void u11_1_theWindowBoundsComeFromTheFirstAndLastBar() {
        MarketStructure structure = resolve(monotoneUptrendWindow());

        assertThat(structure.getWindowStartAt()).isEqualTo(barAt(0));
        assertThat(structure.getWindowEndAt()).isEqualTo(barAt(8));
        assertThat(structure.getWindowStartAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(structure.getWindowEndAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    /** Бар, чей максимум выше соседей в пределах глубины с обеих сторон, — свинг-максимум. */
    @Test
    @DisplayName("U11.2 — максимум бара 7 выше соседей: в уровнях есть свинг-максимум с ценой 126")
    void u11_2_aLocalHighBecomesASwingHigh() {
        assertThat(pricesOf(resolve(monotoneUptrendWindow()), MarketPriceLevel.Type.SWING_HIGH))
                .contains(new BigDecimal("126"));
    }

    /** Бар, чей минимум ниже соседей в той же окрестности, — свинг-минимум. */
    @Test
    @DisplayName("U11.3 — минимум бара 2 ниже соседей: в уровнях есть свинг-минимум с ценой 85")
    void u11_3_aLocalLowBecomesASwingLow() {
        assertThat(pricesOf(resolve(monotoneUptrendWindow()), MarketPriceLevel.Type.SWING_LOW))
                .contains(new BigDecimal("85"));
    }

    /** Свинг подтверждается барами с КАЖДОЙ стороны — у краевого бара их нет. */
    @Test
    @DisplayName("U11.4 — максимум 130 несут краевые бары 0 и 4: пивотом с этой ценой не стал ни один")
    void u11_4_aBarTooCloseToTheEdgeNeverBecomesAPivot() {
        MarketStructure structure = resolve(edgeSpikeWindow());

        assertThat(structure.getLevels())
                .as("подтверждающих баров с одной стороны у краевого бара нет")
                .extracting(MarketPriceLevel::getPrice)
                .doesNotContain(new BigDecimal("130"));
    }

    /**
     * Момент обнаружения — бар пивота, момент подтверждения — бар, отстоящий
     * вперёд на глубину поиска: это и есть гейт использования без
     * заглядывания вперёд.
     */
    @Test
    @DisplayName("U11.5 — пивот 122 в середине окна: обнаружен на баре 3, подтверждён на баре 4")
    void u11_5_aPivotIsConfirmedOnlyOnceItsLookaheadBarsHaveClosed() {
        MarketStructure structure = resolve(monotoneUptrendWindow());

        MarketPriceLevel pivot = structure.getLevels().stream()
                .filter(level -> level.getPrice().compareTo(new BigDecimal("122")) == 0)
                .findFirst()
                .orElseThrow();

        assertThat(pivot.getDetectedAt()).isEqualTo(barAt(3));
        assertThat(pivot.getConfirmedAt())
                .as("вперёд ровно на глубину поиска, равную единице")
                .isEqualTo(barAt(4));
    }

    /** Окно короче удвоенной глубины поиска — пивотов нет вовсе, и это консервативный дефолт. */
    @Test
    @DisplayName("U11.6 — окно из 5 баров при глубине 3: пивотов нет, тип «неизвестно», отказа нет")
    void u11_6_aWindowShorterThanTwiceTheLookbackHasNoPivotsAtAll() {
        MarketStructureParams params = structureParams();
        params.setSwingLookbackBars(3);

        assertThatCode(() -> {
            MarketStructure structure = resolver.resolve(monotoneUptrendWindow().subList(0, 5), null, null, params);

            assertThat(structure.getLevels()).isEmpty();
            assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
            assertThat(structure.getBreakoutEvent()).isNull();
        }).as("отказа нет").doesNotThrowAnyException();
    }

    // --- базовая сборка ----------------------------------------------------

    private MarketStructure resolve(List<Candle> window) {
        return resolver.resolve(window, null, null, structureParams());
    }

    private static List<BigDecimal> pricesOf(MarketStructure structure, MarketPriceLevel.Type type) {
        return structure.getLevels().stream()
                .filter(level -> Objects.equals(type, level.getType()))
                .map(MarketPriceLevel::getPrice)
                .toList();
    }
}
