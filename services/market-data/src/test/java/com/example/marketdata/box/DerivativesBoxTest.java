package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Производные — группа {@code B4} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Предмет клеток — ОРКЕСТРАЦИЯ расчёта, а не его математика.</b>
 * Что по чему считается, что записывается и что пропускается, — здесь;
 * таблицы «вход → выход» калькуляторов и резолверов живут уровнем ниже
 * (предмет {@code market-data-indicators}), потому что ввода-вывода у них
 * нет и через ящик они стоили бы дороже, давая меньше.
 *
 * <p><b>Идентичность называет, ЧТО считать, а инструменты приносит
 * сбор.</b> Одна заказанная «ATR(14) на 1H» покрывает весь листинг, у
 * которого этот таймфрейм собирается
 * (docs/components/IndicatorJob.md §«Инструменты приносит сбор, а не
 * заказ»), — и это ровно то, что мерит {@code B4.1}.
 */
class DerivativesBoxTest extends SharedMarketDataBox {

    private static final String FOURTH_INSTRUMENT = "XRP-USDT-SWAP";

    private static final Integer PAGE_SIZE = 100;

    private static final String ATR_14 = "{\"period\": 14}";

    @Test
    @DisplayName("B4.1 — идентичность считается по всем группам своего таймфрейма")
    void b4_1_anIdentityIsComputedOverAllUnitsOfItsTimeframe() {
        List<String> instruments = provisionInstruments(
                INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT, FOURTH_INSTRUMENT);
        requireCandles(instruments.get(0), HOUR, 300L);
        requireCandles(instruments.get(1), HOUR, 300L);
        requireCandles(instruments.get(2), HOUR, 300L);
        requireCandles(instruments.get(3), "FIFTEEN_MINUTES", 300L);
        loadSeries();
        requireIndicator("ATR", HOUR, ATR_14);
        connector.forgetRequests();

        tick(Tick.INDICATORS);

        assertThat(valuesOf(INSTRUMENT)).isGreaterThan(0L);
        assertThat(valuesOf(SECOND_INSTRUMENT)).isGreaterThan(0L);
        assertThat(valuesOf(THIRD_INSTRUMENT)).isGreaterThan(0L);
        assertThat(valuesOf(FOURTH_INSTRUMENT)).isEqualTo(0L);
        assertThat(connector.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B4.2 — повторный тик вторых значений не пишет")
    void b4_2_aRepeatedTickWritesNoSecondValues() {
        singleSeriesWithIndicator();
        Long afterFirst = rows.count("indicator_values");

        tick(Tick.INDICATORS);

        assertThat(afterFirst).isGreaterThan(0L);
        assertThat(rows.count("indicator_values")).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("B4.3 — зона разгона наружу не выходит")
    void b4_3_theWarmupZoneDoesNotTravelOut() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        // Горизонт мельче ряда: бэкфилл достигает его первой же страницей и
        // уводит группу в проверку, а из BACKFILL расчёт группу не берёт.
        requireCandles(instrument, HOUR, 5L);
        // Ровно столько баров, сколько нужно на разгон плюс три: у RSI с
        // периодом 14 разгон по умолчанию — два периода.
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.candles(barsAgo(HOUR_MILLIS, 40), HOUR_MILLIS, 31, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        requireIndicator("RSI", HOUR, "{\"period\": 14}");

        tick(Tick.INDICATORS);

        assertThat(rows.count("indicator_values")).isEqualTo(3L);
        assertThat(rows.all("indicator_values").getFirst()).doesNotContainKey("warmup");
        List<Map<String, Object>> values = rows.allOrderedBy("indicator_values", "candle_timestamp");
        assertThat(values).hasSize(3);
    }

    @Test
    @DisplayName("B4.4 — недостаточная история не даёт значений")
    void b4_4_insufficientHistoryYieldsNoValues() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 5L);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.candles(barsAgo(HOUR_MILLIS, 20), HOUR_MILLIS, 10, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        String deep = requireIndicator("ATR", HOUR, "{\"period\": 14}");
        String shallow = requireIndicator("ATR", HOUR, "{\"period\": 5, \"warmup\": 5}");

        tick(Tick.INDICATORS);

        assertThat(valuesOfConfig(deep)).isEqualTo(0L);
        assertThat(valuesOfConfig(shallow)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("B4.6 — объявленный, но не готовый вход даёт UNKNOWN")
    void b4_6_aDeclaredButUnreadyInputYieldsUnknown() {
        singleSeries();
        String atr = requireIndicator("ATR", HOUR, ATR_14);
        requireStructure(HOUR, Bodies.structureParams(50), null, atr);

        tick(Tick.MARKET_STRUCTURES);

        assertThat(rows.count("market_structures")).isEqualTo(1L);
        Map<String, Object> structure = rows.all("market_structures").getFirst();
        assertThat(structure.get("type")).isEqualTo("UNKNOWN");
        assertThat(structure.get("window_start_at")).isNotNull();
        assertThat(structure.get("window_end_at")).isNotNull();
        assertThat(rows.count("market_price_levels")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B4.7 — необъявленный вход отсутствием не является")
    void b4_7_anUndeclaredInputIsNotAnAbsentOne() {
        singleSeries();
        requireStructure(HOUR, Bodies.structureParams(50), null, null);

        tick(Tick.MARKET_STRUCTURES);

        assertThat(rows.count("market_structures")).isEqualTo(1L);
        assertThat(rows.all("market_structures").getFirst().get("type")).isNotEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("B4.8 — производитель свежесть входа не гейтит")
    void b4_8_theProducerDoesNotGateInputFreshness() {
        singleSeries();
        String atr = requireIndicator("ATR", HOUR, ATR_14);
        tick(Tick.INDICATORS);
        assertThat(rows.count("indicator_values")).isGreaterThan(0L);
        // Метка значения — открытие бара, а бары ряда лежат сотнями часов
        // назад: вход заведомо старше любого разумного срока читателя.
        requireStructure(HOUR, Bodies.structureParams(50), null, atr);

        tick(Tick.MARKET_STRUCTURES);

        assertThat(rows.count("market_structures")).isEqualTo(1L);
        assertThat(rows.all("market_structures").getFirst().get("type")).isNotEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("B4.9 — идентичность структуры без окна пропускается с записью в журнал")
    void b4_9_aStructureIdentityWithoutAWindowIsSkippedWithALogRecord() {
        singleSeries();
        String broken = requireStructure(HOUR, Bodies.structureParamsWithoutWindow(), null, null);
        requireStructure(HOUR, Bodies.structureParams(50), null, null);
        Integer mark = AppLog.mark();

        tick(Tick.MARKET_STRUCTURES);

        assertThat(rows.count("market_structures")).isEqualTo(1L);
        assertThat(structuresOfConfig(broken)).isEqualTo(0L);
        assertThat(AppLog.since(mark)).contains("has no lookback window");
    }

    @Test
    @DisplayName("B4.10 — повторный тик структуры вторых строк не пишет")
    void b4_10_aRepeatedStructureTickWritesNoSecondRows() {
        singleSeries();
        requireStructure(HOUR, Bodies.structureParams(50), null, null);
        tick(Tick.MARKET_STRUCTURES);
        Long afterFirst = rows.count("market_structures");

        tick(Tick.MARKET_STRUCTURES);

        assertThat(afterFirst).isEqualTo(1L);
        assertThat(rows.count("market_structures")).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("B4.11 — фазы ни один тик не считает и не хранит")
    void b4_11_noTickComputesOrStoresPhases() {
        singleSeries();
        requireIndicator("ATR", HOUR, ATR_14);
        requireStructure(HOUR, Bodies.structureParams(50), null, null);
        connector.answers(ConnectorStub.TICKERS, Feed.emptyMap());
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(1L, 3));

        tick(Tick.INSTRUMENT_SYNC);
        tick(Tick.CANDLES);
        tick(Tick.INDICATORS);
        tick(Tick.MARKET_STRUCTURES);
        tick(Tick.SNAPSHOTS);

        assertThat(rows.tableNames()).noneSatisfy(table ->
                assertThat(table.toLowerCase()).contains("phase"));
    }

    /** Ряд одной страницы у одного инструмента, доведённый до проверки. */
    private void singleSeries() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        loadSeries();
    }

    /** Тот же ряд плюс посчитанная по нему идентичность индикатора. */
    private void singleSeriesWithIndicator() {
        singleSeries();
        requireIndicator("ATR", HOUR, ATR_14);
        tick(Tick.INDICATORS);
    }

    private void loadSeries() {
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.trendingCandles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, PAGE_SIZE, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
    }

    private Long valuesOf(String externalId) {
        return rows.countWhere("indicator_values", "instrument_id", instrumentId(externalId));
    }

    private Long valuesOfConfig(String configInternalId) {
        return rows.countWhere("indicator_values", "indicator_config_id", configId(configInternalId));
    }

    private Long structuresOfConfig(String configInternalId) {
        return rows.countWhere("market_structures", "market_structure_config_id",
                structureConfigId(configInternalId));
    }

    private Long configId(String internalId) {
        return ((Number) rows.row("indicator_configs", "internal_id", internalId).get("id")).longValue();
    }

    private Long structureConfigId(String internalId) {
        return ((Number) rows.row("market_structure_configs", "internal_id", internalId)
                .get("id")).longValue();
    }
}
