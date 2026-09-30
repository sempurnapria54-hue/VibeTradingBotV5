package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.atrParams;
import static com.example.marketdata.unit.calculation.CalcFixture.bollingerParams;
import static com.example.marketdata.unit.calculation.CalcFixture.efficiencyParams;
import static com.example.marketdata.unit.calculation.CalcFixture.emaParams;
import static com.example.marketdata.unit.calculation.CalcFixture.macdParams;
import static com.example.marketdata.unit.calculation.CalcFixture.obvParams;
import static com.example.marketdata.unit.calculation.CalcFixture.rsiParams;
import static com.example.marketdata.unit.calculation.CalcFixture.stochasticParams;
import static com.example.marketdata.unit.calculation.CalcFixture.structureParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.marketdata.domain.model.IndicatorConfig;
import com.example.marketdata.domain.model.MarketStructureConfig;
import com.example.marketdata.domain.service.MarketDataDemandService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверка параметров требования вычисления на входе сервиса: клетки
 * `U1.12`, `U1.13`, `U6.7`, `U6.8`, `U7.11` и `U11.8` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/architecture/market-data-collection.md §«Как потребность доходит до
 * сбора»).
 *
 * <p><b>Прежде эти клетки не прогонялись:</b> негодный параметр доезжал до
 * вычислителя, и дом исхода на нём не называл. Дом назван — проверка на
 * входе, — и клетки стоя́т на ней, а не на вычислителе: до вычислителя
 * такой параметр больше не доезжает.
 *
 * <p><b>Каждая клетка мерит два состояния</b> — годный параметр принят,
 * негодный отвергнут, — иначе проверка, отвергающая всё подряд, проходила
 * бы её молча.
 *
 * <p><b>Состояние собирается настоящими полями</b> параметров и идентичности
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»); мокаются только
 * коллабораторы сервиса требований — персистентность.
 */
class RequirementParamsCheckTest {

    private final ComputationConfigDataService configDataService = mock(ComputationConfigDataService.class);

    private final MarketDataDemandService demandService = new MarketDataDemandService(
            mock(InstrumentDataService.class), mock(CandleGroupDataService.class), configDataService);

    /**
     * Период пуст либо не положителен — у каждого типа, который период
     * несёт. Отказ приходит отказом создания идентичности, и до реестра
     * требование не доходит.
     */
    @Test
    @DisplayName("U1.12 — период пуст либо равен нулю: требование отвергается, идентичность не заводится")
    void u1_12_anEmptyOrNonPositivePeriodIsRefusedAtTheInput() {
        List<IndicatorParams> valid = List.of(atrParams(14, null), emaParams(14, null), rsiParams(14, null),
                bollingerParams(20, "2", null), efficiencyParams(10, null), stochasticParams(14, 3, 3, null),
                macdParams(12, 26, 9, null), obvParams(null));
        List<IndicatorParams> emptyPeriod = List.of(atrParams(null, null), emaParams(null, null),
                rsiParams(null, null), bollingerParams(null, "2", null), efficiencyParams(null, null),
                stochasticParams(14, null, 3, null), macdParams(12, 26, null, null));
        List<IndicatorParams> zeroPeriod = List.of(atrParams(0, null), emaParams(0, null), rsiParams(0, null),
                bollingerParams(0, "2", null), efficiencyParams(-1, null), stochasticParams(0, 3, 3, null),
                macdParams(12, 26, 0, null));

        assertThat(valid).as("годные параметры проверку проходят").allSatisfy(params ->
                assertThat(indicator(params).parameterDefects()).isEmpty());
        assertThat(emptyPeriod).as("пустой период — дефект").allSatisfy(params ->
                assertThat(indicator(params).parameterDefects()).isNotEmpty());
        assertThat(zeroPeriod).as("неположительный период — дефект").allSatisfy(params ->
                assertThat(indicator(params).parameterDefects()).isNotEmpty());

        assertThatThrownBy(() -> demandService.requireIndicator(indicator(atrParams(null, null))))
                .as("отказ — классом негодного входа")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
        verify(configDataService, never()).ensureIndicatorConfig(any());
    }

    /**
     * Прогрев объявлен отрицательным — у любого типа, включая тип без
     * периода: вычислитель начал бы ряд с индекса до его начала. Пустой и
     * нулевой прогрев законны — «выводится реализацией» и «разгонной зоны
     * нет» (U1.2, U1.11).
     */
    @Test
    @DisplayName("U1.13 — прогрев отрицателен: требование отвергается; пустой и нулевой — принимаются")
    void u1_13_aNegativeWarmupIsRefusedAtTheInput() {
        List<IndicatorParams> accepted = List.of(emaParams(14, null), emaParams(14, 0), emaParams(14, 50),
                obvParams(0), bollingerParams(20, "2", 0));
        List<IndicatorParams> negative = List.of(emaParams(14, -1), atrParams(14, -1), rsiParams(14, -1),
                bollingerParams(20, "2", -1), efficiencyParams(10, -1), stochasticParams(14, 3, 3, -1),
                macdParams(12, 26, 9, -1), obvParams(-1));

        assertThat(accepted).as("пустой, нулевой и положительный прогрев проверку проходят").allSatisfy(params ->
                assertThat(indicator(params).parameterDefects()).isEmpty());
        assertThat(negative).as("отрицательный прогрев — дефект у каждого типа").allSatisfy(params ->
                assertThat(indicator(params).parameterDefects()).singleElement().asString()
                        .contains("warmup"));

        assertThatThrownBy(() -> demandService.requireIndicator(indicator(obvParams(-1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warmup");
        verify(configDataService, never()).ensureIndicatorConfig(any());
    }

    /**
     * Множитель отклонения полос пуст либо отрицателен — ширина полосы не
     * определена либо верхняя граница встала ниже нижней. Нулевой множитель
     * законен: полосы схлопываются в середину, исход объявлен (U7.7).
     */
    @Test
    @DisplayName("U7.11 — множитель отклонения пуст либо отрицателен: требование отвергается; ноль — принимается")
    void u7_11_anEmptyOrNegativeDeviationMultiplierIsRefused() {
        BollingerBandsParams emptyMultiplier = new BollingerBandsParams(20, null);
        emptyMultiplier.setTimeframe(TimeFrame.ONE_HOUR);

        assertThat(indicator(bollingerParams(20, "2", null)).parameterDefects()).isEmpty();
        assertThat(indicator(bollingerParams(20, "0", null)).parameterDefects())
                .as("нулевой множитель — вырожденные, но определённые полосы").isEmpty();
        assertThat(indicator(emptyMultiplier).parameterDefects()).singleElement().asString()
                .contains("deviationMultiplier");
        assertThat(indicator(bollingerParams(20, "-1", null)).parameterDefects()).singleElement().asString()
                .contains("deviationMultiplier");

        assertThatThrownBy(() -> demandService.requireIndicator(indicator(emptyMultiplier)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviationMultiplier");
        verify(configDataService, never()).ensureIndicatorConfig(any());
    }

    /** Быстрый период больше медленного — MACD не определён. */
    @Test
    @DisplayName("U6.7 — быстрый период 26 при медленном 12: требование отвергается, 12 при 26 — принимается")
    void u6_7_aFastPeriodAboveTheSlowOneIsRefused() {
        assertThat(indicator(macdParams(12, 26, 9, null)).parameterDefects()).isEmpty();
        assertThat(indicator(macdParams(26, 12, 9, null)).parameterDefects())
                .singleElement().asString().contains("fastPeriod");

        assertThatThrownBy(() -> demandService.requireIndicator(indicator(macdParams(26, 12, 9, null))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(configDataService, never()).ensureIndicatorConfig(any());
    }

    /** Быстрый период равен медленному — линия тождественно нулевая, и идентичность вырождена. */
    @Test
    @DisplayName("U6.8 — быстрый период равен медленному: требование отвергается")
    void u6_8_aFastPeriodEqualToTheSlowOneIsRefused() {
        assertThat(indicator(macdParams(12, 13, 9, null)).parameterDefects()).isEmpty();
        assertThat(indicator(macdParams(12, 12, 9, null)).parameterDefects())
                .singleElement().asString().contains("fastPeriod");
    }

    /**
     * Глубина поиска свингов равна нулю — пивотом стал бы каждый бар. Той же
     * проверкой охраняется глубина окна; пустые числа структуры проверкой
     * не охраняются, у них объявленный консервативный исход.
     */
    @Test
    @DisplayName("U11.8 — глубина поиска свингов 0 либо окна 0: требование отвергается; пустые глубины — нет")
    void u11_8_aNonPositiveStructureDepthIsRefused() {
        MarketStructureParams zeroSwing = structureParams();
        zeroSwing.setSwingLookbackBars(0);
        MarketStructureParams zeroWindow = structureParams();
        zeroWindow.setLookbackBars(0);
        MarketStructureParams emptyDepths = structureParams();
        emptyDepths.setSwingLookbackBars(null);
        emptyDepths.setLookbackBars(null);

        assertThat(structure(structureParams()).parameterDefects()).isEmpty();
        assertThat(structure(zeroSwing).parameterDefects()).singleElement().asString()
                .contains("swingLookbackBars");
        assertThat(structure(zeroWindow).parameterDefects()).singleElement().asString()
                .contains("lookbackBars");
        assertThat(structure(emptyDepths).parameterDefects())
                .as("пустая глубина — объявленный консервативный исход, а не дефект входа")
                .isEmpty();

        assertThatThrownBy(() -> demandService.requireMarketStructure(structure(zeroSwing)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(configDataService, never()).ensureMarketStructureConfig(any());
    }

    // --- базовая сборка ----------------------------------------------------

    /**
     * Идентичность индикатора с этими параметрами. Тип индикатора не
     * проставлен намеренно: проверка читает подтип параметров, а тип строки
     * нужен разбору тела, который до неё уже прошёл.
     */
    private static IndicatorConfig indicator(IndicatorParams params) {
        IndicatorConfig config = new IndicatorConfig();
        config.setTimeframe(TimeFrame.ONE_HOUR);
        config.setParams(params);
        return config;
    }

    private static MarketStructureConfig structure(MarketStructureParams params) {
        MarketStructureConfig config = new MarketStructureConfig();
        config.setTimeframe(TimeFrame.ONE_HOUR);
        config.setParams(params);
        return config;
    }
}
