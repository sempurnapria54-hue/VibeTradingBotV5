package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketdata.MarketDataApplication;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Такт и выключатель каждого тика — клетка {@code B9.5} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Число тиков мерится НЕГОДНЫМ выражением такта, а не чтением
 * кода.</b> Ключей такта пять; подставив в каждый по очереди выражение,
 * которого не существует, кейс получает пять независимых падений
 * подъёма — то есть пять разных методов расписания, каждый из которых
 * берёт своё выражение из конфигурации. Литерал в коде такого падения не
 * дал бы ни одного.
 *
 * <p><b>Выключателей тоже пять, и они наблюдаются молчанием.</b>
 * Контекст класса поднят со всеми пятью выключателями в положении
 * «выключено»: ни один ручной тик не производит ни строки и ни одного
 * обращения к соседу.
 */
class ScheduleInputBoxTest extends MarketDataBox {

    /** Ключи такта: по одному на тик. */
    private static final List<String> CRON_KEYS = List.of(
            "instrument-sync.cron",
            "candle-loading.cron",
            "market-data.indicator.cron",
            "market-data.structure.cron",
            "snapshot-collection.cron");

    /** Ключи выключателей: по одному на тот же тик. */
    private static final List<String> SWITCH_KEYS = List.of(
            "instrument-sync.enabled",
            "candle-loading.enabled",
            "market-data.indicator.enabled",
            "market-data.structure.enabled",
            "snapshot-collection.enabled");

    /** Выражение, которого не существует: им роняется разбор такта. */
    private static final String IMPOSSIBLE_CRON = "не-выражение";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        SWITCH_KEYS.forEach(key -> axes.put(key, "false"));
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B9.5 — такт каждого тика приезжает конфигурацией")
    void b9_5_theBeatOfEveryTickArrivesByConfiguration() {
        connector.answers(ConnectorStub.INSTRUMENTS,
                Feed.array(Feed.instrument(INSTRUMENT, "BTC", "USDT")));

        tick(Tick.INSTRUMENT_SYNC);
        tick(Tick.CANDLES);
        tick(Tick.INDICATORS);
        tick(Tick.MARKET_STRUCTURES);
        tick(Tick.SNAPSHOTS);

        assertThat(CRON_KEYS).hasSameSizeAs(SWITCH_KEYS);
        assertThat(rows.count("instruments")).isEqualTo(0L);
        assertThat(connector.count()).isEqualTo(0);
        CRON_KEYS.forEach(key -> assertThatThrownBy(() -> startWithBrokenCron(key))
                .describedAs("такт %s не взят из конфигурации", key)
                .isInstanceOf(Exception.class));
    }

    private void startWithBrokenCron(String cronKey) {
        Map<String, String> properties = new LinkedHashMap<>(MarketDataSubstrate.defaults());
        properties.put(cronKey, IMPOSSIBLE_CRON);
        properties.put("server.port", "0");
        String[] arguments = properties.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);
        new SpringApplicationBuilder(MarketDataApplication.class).run(arguments).close();
    }
}
