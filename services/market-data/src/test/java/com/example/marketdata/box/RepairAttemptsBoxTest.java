package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Исчерпание попыток докачки — клетка {@code B3.8} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст, потому что число попыток есть ось
 * конфигурации.</b> Умолчание в пять попыток стоило бы кейсу вдвое
 * большего числа тиков и ничего бы к нему не добавило: предмет — что
 * после исчерпания приходит {@code ERROR}, а не сколько именно попыток
 * назначено.
 *
 * <p><b>Реестра известных пропусков у цикла нет</b>
 * (docs/lifecycles/CandleGroup.md §«Докачка дыр»): дыра, которой у
 * площадки действительно нет, отличается от временного отказа только
 * числом попыток, и потому исход у неё терминальный.
 */
class RepairAttemptsBoxTest extends MarketDataBox {

    /** Потолок попыток: столько же, сколько объявлено конфигурацией класса. */
    private static final Integer MAX_ATTEMPTS = 2;

    /** Потолок тиков: каждая попытка стои́т пары «починка — проверка». */
    private static final Integer TICK_BUDGET = 10;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("candle-loading.max-repair-attempts", String.valueOf(MAX_ATTEMPTS));
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B3.8 — неустранимая дыра исчерпывает попытки и даёт ERROR")
    void b3_8_anUnrepairableHoleExhaustsTheAttemptsAndYieldsError() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.candles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, 100, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        rows.put("delete from candles where open_timestamp between ? and ?",
                barsAgo(HOUR_MILLIS, 350), barsAgo(HOUR_MILLIS, 348));
        // Площадка действительно не имеет этих баров: окно докачки
        // приходит пустым сколько угодно раз.
        connector.answers(ConnectorStub.HISTORY_CANDLES, Feed.empty());

        for (int index = 0; index < TICK_BUDGET && !"ERROR".equals(status()); index++) {
            tick(Tick.CANDLES);
        }

        assertThat(status()).isEqualTo("ERROR");
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("ERROR");
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isEqualTo(0);
        assertThat(connector.count(ConnectorStub.CANDLES)).isEqualTo(0);
    }

    private String status() {
        return String.valueOf(rows.all("candle_groups").getFirst().get("status"));
    }
}
