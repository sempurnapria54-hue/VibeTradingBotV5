package com.example.marketdata.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Предел попыток докачки — клетки {@code B3.8} и {@code B3.18} документа
 * `.claude/tests/cases/market-data.md`: неустранимая дыра его исчерпывает,
 * широкая устранимая — нет.
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

    /** Размер страницы загрузки: величина конфигурации сервиса (умолчание). */
    private static final Integer PAGE_SIZE = 100;

    /** Ряд площадки у клетки широкого хвоста: от самого старого бара до последнего закрытого. */
    private static final Integer SERIES_BARS = 900;

    /**
     * Потолок тиков догонки широкого хвоста. Проход починки докачивает не
     * больше страницы и стои́т пары тиков; дыре в семь страниц хватает
     * меньше тридцати, потолок взят вдвое — он страхует от вечного цикла,
     * а не мерит скорость.
     */
    private static final Integer TAIL_TICK_BUDGET = 60;

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

    /**
     * Хвост, отросший за долгий бэкфилл, шире того, что докачивают
     * {@code 1 + MAX_ATTEMPTS} страниц: докачка хвоста берёт последнюю
     * страницу, а дыру между ней и рядом латает починка — постранично.
     *
     * <p><b>Площадка здесь — РЯД, а не заготовка</b>
     * ({@link ConnectorStub#servesHistory}): курсор окна починки выводит
     * бинарный поиск предмета, и кейсу он заранее не известен.
     *
     * <p><b>Предпосылка «шире предела» предъявлена, а не подразумевается:</b>
     * запросов истории после догонки больше, чем разрешено попыток, — будь
     * их не больше, клетка была бы зелёной и при бюджете, который прогресс
     * не возвращает.
     */
    @Test
    @DisplayName("B3.18 — хвост шире предела попыток догоняется, а не уводит в ERROR")
    void b3_18_aTailWiderThanTheAttemptLimitIsCaughtUpRatherThanErrored() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        // Бэкфилл кончился давно: верхняя граница ряда — 801 бар назад.
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.candles(barsAgo(HOUR_MILLIS, SERIES_BARS), HOUR_MILLIS, PAGE_SIZE, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        assertThat(status()).isEqualTo("ACTIVE");
        connector.reset();
        connector.answers(ConnectorStub.CANDLES, Feed.candles(barsAgo(HOUR_MILLIS, PAGE_SIZE), HOUR_MILLIS,
                PAGE_SIZE, 50000 + SERIES_BARS - PAGE_SIZE));
        connector.servesHistory(barsAgo(HOUR_MILLIS, SERIES_BARS), HOUR_MILLIS, SERIES_BARS);

        tick(Tick.CANDLES);

        // Ряд с дырой в семь страниц — настоящая потеря готовности.
        assertThat(instrumentStatus()).isEqualTo("CANDLES_LOADING");

        for (int index = 0; index < TAIL_TICK_BUDGET && isFalse(isSettled()); index++) {
            tick(Tick.CANDLES);
        }

        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(rows.count("candles")).isEqualTo(Long.valueOf(SERIES_BARS));
        assertThat(rows.all("candle_groups").getFirst().get("repair_attempts")).isEqualTo(0);
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isGreaterThan(MAX_ATTEMPTS + 1);
        assertThat(instrumentStatus()).isEqualTo("ACTIVE");
    }

    /** Цикл группы пришёл к исходу: готовность либо терминал. */
    private Boolean isSettled() {
        return "ACTIVE".equals(status()) || "ERROR".equals(status());
    }

    private String instrumentStatus() {
        return String.valueOf(rows.row("instruments", "external_id", INSTRUMENT).get("status"));
    }

    private String status() {
        return String.valueOf(rows.all("candle_groups").getFirst().get("status"));
    }
}
