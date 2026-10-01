package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.doubleBottomUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.loneSpikeUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.monotoneUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.spreadClusterWindow;
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
 * <p><b>Граничный уровень строится от крайнего пивота стороны и выдаётся
 * только подтверждённым.</b> Поэтому окна тренда здесь несут одно из двух
 * положений стороны: крайний пивот с касаниями (двойное дно) либо одинокий
 * крайний пивот — над внутренним кластером или без него.
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

    /**
     * У тренда выдаётся подтверждённая сторона и не выдаётся одинокая:
     * крайний пивот пола {@code 85} несёт второе касание {@code 85.2}, а
     * потолок {@code 130} одинок.
     */
    @Test
    @DisplayName("U13.2 — тренд с двойным дном 85/85.2 и одиноким потолком 130: поддержка 85 есть, сопротивления нет")
    void u13_2_aTrendYieldsItsConfirmedSideOnlyAndKeepsTheSwings() {
        MarketStructure structure = resolve(doubleBottomUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UPTREND);
        assertThat(priceOf(structure, MarketPriceLevel.Type.SUPPORT))
                .as("цена уровня — цена крайнего пивота пола, а не его касания")
                .isEqualByComparingTo("85");
        assertThat(structure.findLevel(MarketPriceLevel.Type.SUPPORT).getConfirmedAt())
                .as("второе касание — пивот бара 4 — известно на баре 5")
                .isEqualTo(barAt(5));
        assertThat(structure.findLevel(MarketPriceLevel.Type.RESISTANCE))
                .as("у потолка касание одно при требуемых двух")
                .isNull();
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
     * Внутренний кластер под одиноким крайним пивотом границей не
     * становится: у кластера {@code 100} касаний два, у крайнего пивота
     * {@code 108} — одно, и граница строится от крайнего.
     */
    @Test
    @DisplayName("U13.4 — кластер из двух пивотов на 100, одинокий потолок 108: потолка-границы нет вовсе")
    void u13_4_anInnerClusterUnderALoneExtremePivotIsNotABoundary() {
        MarketStructure structure = resolve(loneSpikeUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.getType()).as("предпосылка кейса — тренд").isEqualTo(MarketStructure.Type.UPTREND);
        assertThat(structure.findLevel(MarketPriceLevel.Type.RESISTANCE))
                .as("окно уже торговало выше кластера — ценой границы он не становится")
                .isNull();
    }

    /**
     * Дом разводит момент обнаружения (бар пивота), момент подтверждения
     * (касание номер {@code minTouches}) и конец окна.
     *
     * <p><b>Окно — диапазон, а не тренд, и это несущее.</b> У тренда базовой
     * сборки касаний у крайних пивотов меньше требуемого, и граничных уровней
     * нет вовсе — мерить «не равен концу окна» было бы не на чем. У
     * диапазона обе границы подтверждены, и моменты читаются числом. Пивоты
     * потолка стоя́т на барах 1, 3, 5, 7, пола — на 2, 4, 6; глубина поиска
     * равна единице, поэтому касание известно на следующем баре, и второе
     * касание потолка известно на баре 4, пола — на баре 5.
     */
    @Test
    @DisplayName("U13.5 — границы диапазона: найдены на барах 1 и 2, подтверждены на барах 4 и 5, а не на конце окна 8")
    void u13_5_theBoundaryLevelMomentsAreNotIdenticalToTheWindowEnd() {
        MarketStructure structure = resolve(rangeWindow(), "0.1", structureParams());
        MarketPriceLevel ceiling = structure.findLevel(MarketPriceLevel.Type.RANGE_HIGH);
        MarketPriceLevel floor = structure.findLevel(MarketPriceLevel.Type.RANGE_LOW);

        assertThat(structure.getType()).as("предпосылка кейса — тип RANGE").isEqualTo(MarketStructure.Type.RANGE);
        assertThat(ceiling.getDetectedAt())
                .as("момент обнаружения — бар самого раннего пивота уровня")
                .isEqualTo(barAt(1));
        assertThat(ceiling.getConfirmedAt())
                .as("момент подтверждения — второе касание, известное на баре после своего пивота")
                .isEqualTo(barAt(4));
        assertThat(floor.getDetectedAt()).isEqualTo(barAt(2));
        assertThat(floor.getConfirmedAt()).isEqualTo(barAt(5));
        assertThat(List.of(ceiling.getDetectedAt(), ceiling.getConfirmedAt(),
                floor.getDetectedAt(), floor.getConfirmedAt()))
                .as("концу окна тождественно не равен ни один из моментов")
                .doesNotContain(structure.getWindowEndAt());
    }

    /**
     * Требуемое число касаний — величина параметров, а не константа: то же
     * двойное дно, что у `U13.2`, при требуемых трёх касаниях поддержку не
     * выдаёт — касаний у крайнего пивота пола два.
     */
    @Test
    @DisplayName("U13.6 — двойное дно при требуемых трёх касаниях: касаний 2, поддержки среди уровней нет")
    void u13_6_anUnconfirmedBoundaryDoesNotEnterTheLevels() {
        MarketStructureParams params = withThreshold("0.3");
        params.setMinTouches(3);

        MarketStructure structure = resolve(doubleBottomUptrendWindow(), "0.9", params);

        assertThat(structure.getType()).as("предпосылка кейса — тренд").isEqualTo(MarketStructure.Type.UPTREND);
        assertThat(structure.findLevel(MarketPriceLevel.Type.SUPPORT))
                .as("касаний меньше требуемого — уровня нет вовсе")
                .isNull();
    }

    /**
     * Пивоты стороны в пределах толеранса крайнего дают <b>один</b>
     * граничный уровень с ценой крайнего, а свинги кластеризацию не
     * проходят. Окно — разнесённый кластер {@code 110} и {@code 109} при
     * толерансе от скаляра волатильности {@code 4}, то есть {@code 2}.
     */
    @Test
    @DisplayName("U13.7 — потолок диапазона из пивотов 110 и 109 в толерансе 2: граница одна по 110, свингов-максимумов 2")
    void u13_7_pivotsWithinToleranceOfTheExtremeYieldASingleBoundary() {
        MarketStructure structure = resolver.resolve(spreadClusterWindow(), new BigDecimal("0.1"),
                new BigDecimal("4"), structureParams());

        assertThat(structure.getType()).as("предпосылка кейса — тип RANGE").isEqualTo(MarketStructure.Type.RANGE);
        assertThat(countOf(structure, MarketPriceLevel.Type.RANGE_HIGH)).isEqualTo(1);
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_HIGH))
                .as("цена границы — цена крайнего пивота")
                .isEqualByComparingTo("110");
        assertThat(countOf(structure, MarketPriceLevel.Type.SWING_HIGH))
                .as("свинги выдаются каждый своим уровнем")
                .isEqualTo(2);
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

    /**
     * У монотонного тренда крайние пивоты обеих сторон одиноки: граничных
     * уровней нет вовсе, и граничного уровня с пустым моментом подтверждения
     * не выдаётся.
     */
    @Test
    @DisplayName("U13.9 — монотонный тренд, у крайних пивотов по одному касанию: в составе только свинги")
    void u13_9_aTrendWithLoneExtremePivotsYieldsNoBoundaryLevels() {
        MarketStructure structure = resolve(monotoneUptrendWindow(), "0.9", withThreshold("0.3"));

        assertThat(structure.getType()).as("предпосылка кейса — тренд").isEqualTo(MarketStructure.Type.UPTREND);
        assertThat(structure.getLevels())
                .as("четыре свинг-максимума и три свинг-минимума, ничего сверх")
                .hasSize(7)
                .allSatisfy(level -> assertThat(level.getType())
                        .isIn(MarketPriceLevel.Type.SWING_HIGH, MarketPriceLevel.Type.SWING_LOW));
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
