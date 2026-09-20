package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Независимость выключателей производных — клетка {@code B4.12}
 * документа `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Выключателей у сервиса пять, и каждый свой.</b> Клетка мерит не
 * то, что выключатель работает (это мерит {@code B2.8}), а то, что
 * выключение ОДНОГО тика не гасит соседний: один флаг на два тика
 * означал бы, что остановить расчёт индикаторов нельзя, не остановив
 * структуру.
 */
class IndicatorDisabledBoxTest extends MarketDataBox {

    private static final Integer PAGE_SIZE = 100;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("market-data.indicator.enabled", "false");
        axes.put("market-data.structure.enabled", "true");
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B4.12 — выключатели тиков производных независимы")
    void b4_12_theSwitchesOfDerivativeTicksAreIndependent() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.candles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, PAGE_SIZE, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        requireIndicator("ATR", HOUR, "{\"period\": 14}");
        requireStructure(HOUR, Bodies.structureParams(50), null, null);

        Answer indicators = tick(Tick.INDICATORS);
        Answer structures = tick(Tick.MARKET_STRUCTURES);

        assertThat(indicators.status()).isEqualTo(202);
        assertThat(structures.status()).isEqualTo(202);
        assertThat(rows.count("indicator_values")).isEqualTo(0L);
        assertThat(rows.count("market_structures")).isEqualTo(1L);
    }
}
