package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.loneSpikeUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.monotoneUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.structureParams;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Структура — уровни выхода и их типы: группа `U13` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/MarketStructure.md §«MarketPriceLevel
 * (раздел)» и §«Семантика классификации (как считается)»).
 *
 * <p><b>Базовая сборка:</b> та же, что у группы `U12`; наблюдается
 * <b>состав уровней</b> результата.
 *
 * <p><b>Четыре клетки этой группы красны по построению, и это не дефект
 * теста.</b> Их ожидание взято из дома, а дерево кода несёт иначе; красный
 * прогон и есть предъявление находок `M-3` (подтверждение уровня касаниями
 * к выдаваемым уровням не применяется) и `M-4` (момент подтверждения равен
 * концу окна всегда). Такие клетки помечены меткой {@code debt} и в
 * умолчание прогона не входят.
 *
 * <p><b>Окно одинокого потолка выведено под находку `M-3`:</b> кластер из
 * двух пивотов стои́т на {@code 100} — два касания, то есть ровно
 * требуемое, — а крайний пивот {@code 108} одинок и подтверждения не
 * имеет.
 */
class StructureLevelsTest {

    private final MarketStructureResolver resolver = new MarketStructureResolver();

    /** У диапазона выдаются его границы, и свинги при этом сохранены. */
    @Test
    @DisplayName("U13.1 — классификация дала RANGE: среди уровней есть верх 110 и низ 90, свинги сохранены")
    void u13_1_aRangeYieldsItsBoundariesAndKeepsTheSwings() {
        MarketStructure structure = resolve(rangeWindow(), "0.1", structureParams());

        assertThat(structure.getType()).isEqualTo(MarketStructure.Type.RANGE);
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_HIGH)).isEqualByComparingTo("110");
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_LOW)).isEqualByComparingTo("90");
        assertThat(countOf(structure, MarketPriceLevel.Type.SWING_HIGH)).isEqualTo(4);
        assertThat(countOf(structure, MarketPriceLevel.Type.SWING_LOW)).isEqualTo(3);
    }

    /** У тренда выдаются сопротивление и поддержка, и свинги сохранены. */
    @Test
    @DisplayName("U13.2 — классификация дала тренд: среди уровней есть сопротивление 126 и поддержка 85")
    void u13_2_aTrendYieldsResistanceAndSupportAndKeepsTheSwings() {
        MarketStructure structure = resolve(monotoneUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UPTREND);
        assertThat(priceOf(structure, MarketPriceLevel.Type.RESISTANCE)).isEqualByComparingTo("126");
        assertThat(priceOf(structure, MarketPriceLevel.Type.SUPPORT)).isEqualByComparingTo("85");
        assertThat(countOf(structure, MarketPriceLevel.Type.SWING_HIGH)).isEqualTo(4);
        assertThat(countOf(structure, MarketPriceLevel.Type.SWING_LOW)).isEqualTo(3);
    }

    /** У консервативного исхода граничные уровни не названы домом вовсе. */
    @Test
    @DisplayName("U13.3 — классификация дала UNKNOWN: граничных уровней нет, в составе только свинги")
    void u13_3_aConservativeOutcomeYieldsNoBoundaryLevelsAtAll() {
        MarketStructure structure = resolve(monotoneUptrendWindow(), "0.1", withThreshold("0.3"));

        assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
        assertThat(structure.getLevels()).isNotEmpty().allSatisfy(level ->
                assertThat(level.getType()).isIn(MarketPriceLevel.Type.SWING_HIGH, MarketPriceLevel.Type.SWING_LOW));
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет границу ценой <b>подтверждённого</b> уровня — кластера с
     * числом касаний не меньше требуемого, — а код берёт крайний пивот
     * окна. Красный прогон предъявляет находку `M-3`
     * (`.claude/work/backlog.md` §«Подтверждение уровня касаниями к
     * выдаваемым уровням структуры не применяется»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U13.4 — кластер из двух пивотов на 100, одинокий потолок 108: граница равна 100 (дом), код даёт 108")
    void u13_4_theBoundaryPriceIsTheConfirmedClusterAndNotTheExtremePivot() {
        MarketStructure structure = resolve(loneSpikeUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(priceOf(structure, MarketPriceLevel.Type.RESISTANCE))
                .as("крайний пивот окна ценой границы сам по себе не становится")
                .isEqualByComparingTo("100");
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом разводит
     * момент обнаружения (бар пивота), момент подтверждения (бар требуемого
     * касания) и конец окна; код ставит оба момента равными концу окна.
     * Красный прогон предъявляет находку `M-4`
     * (`.claude/work/backlog.md` §«Момент подтверждения структуры равен
     * концу окна всегда»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U13.5 — граничный уровень тренда: моменты обнаружения и подтверждения не равны концу окна")
    void u13_5_theBoundaryLevelMomentsAreNotIdenticalToTheWindowEnd() {
        MarketStructure structure = resolve(monotoneUptrendWindow(), "0.9", withThreshold("0.3"));
        MarketPriceLevel resistance = structure.findLevel(MarketPriceLevel.Type.RESISTANCE);

        assertThat(resistance.getDetectedAt())
                .as("момент обнаружения — бар пивота, образовавшего уровень")
                .isNotEqualTo(structure.getWindowEndAt());
        assertThat(resistance.getConfirmedAt())
                .as("момент подтверждения — бар требуемого касания")
                .isNotEqualTo(structure.getWindowEndAt());
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет поддержку и сопротивление тренда <b>подтверждёнными</b>
     * уровнями; у потолка {@code 108} касание одно при требуемых двух,
     * значит границы среди уровней быть не должно. Красный прогон
     * предъявляет ту же находку `M-3`.
     */
    @Test
    @Tag("debt")
    @DisplayName("U13.6 — у границы тренда касаний 1 при требуемых 2: границы среди уровней нет")
    void u13_6_anUnconfirmedBoundaryDoesNotEnterTheLevels() {
        MarketStructure structure = resolve(loneSpikeUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.findLevel(MarketPriceLevel.Type.RESISTANCE))
                .as("подтверждения у потолка нет, и выдавать его нечем")
                .isNull();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет группировку пивотов в ценовые уровни; код отдаёт сырые
     * пивоты, и два пивота на {@code 100} приезжают двумя уровнями. Красный
     * прогон предъявляет ту же находку `M-3`.
     */
    @Test
    @Tag("debt")
    @DisplayName("U13.7 — два пивота в пределах толеранса друг друга: они собраны в один уровень, а не в два")
    void u13_7_pivotsWithinToleranceAreClusteredIntoASingleLevel() {
        MarketStructure structure = resolve(loneSpikeUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.getLevels().stream()
                .filter(level -> level.getPrice().compareTo(new BigDecimal("100")) == 0)
                .count())
                .as("кластеризация пивотов в уровень объявлена домом")
                .isEqualTo(1L);
    }

    /** Идентичности на уровнях ставит джоба: сам резолвер не персистит. */
    @Test
    @DisplayName("U13.8 — базовая сборка: ни на одном уровне нет ни идентификатора, ни audit-полей")
    void u13_8_noLevelCarriesAnIdentityOfItsOwn() {
        MarketStructure structure = resolve(monotoneUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.getLevels()).isNotEmpty().allSatisfy(level -> {
            assertThat(level.getId()).isNull();
            assertThat(level.getCreatedAt()).isNull();
            assertThat(level.getCreatedBy()).isNull();
            assertThat(level.getModifiedAt()).isNull();
            assertThat(level.getModifiedBy()).isNull();
        });
    }

    // --- базовая сборка и отклонения ---------------------------------------

    private MarketStructure resolve(List<Candle> window, String efficiencyRatio, MarketStructureParams params) {
        return resolver.resolve(window, new BigDecimal(efficiencyRatio), null, params);
    }

    private static MarketStructureParams withThreshold(String threshold) {
        MarketStructureParams params = structureParams();
        params.setTrendEfficiencyThreshold(new BigDecimal(threshold));
        return params;
    }

    private static BigDecimal priceOf(MarketStructure structure, MarketPriceLevel.Type type) {
        MarketPriceLevel level = structure.findLevel(type);
        assertThat(level).as("уровень типа %s", type).isNotNull();
        return level.getPrice();
    }

    private static int countOf(MarketStructure structure, MarketPriceLevel.Type type) {
        return (int) structure.getLevels().stream()
                .filter(level -> Objects.equals(type, level.getType()))
                .count();
    }
}
