package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketdata.MarketDataApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Счётчик попыток докачки и рестарт — клетка {@code B3.9} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Рестарт здесь настоящий, а не его подобие.</b> Второй контекст
 * поднимается той же формой, что и первый, на ту же базу, и тик подаётся
 * ЕМУ — по HTTP, как и всякому ящику. Подмена бина цикла либо очистка его
 * карты изнутри проверяли бы нашу подмену, а не то, переживает ли
 * гарантия перезапуск процесса.
 *
 * <p><b>Потолок попыток — одна</b>, и это не упрощение: предмет клетки —
 * что бюджет, израсходованный ДО рестарта, остаётся израсходованным
 * после него; при одной попытке бюджет исчерпывается первой же починкой,
 * и всё, что делает вторая половина кейса, — предъявляет следующий тик.
 */
class RepairCounterRestartBoxTest extends MarketDataBox {

    /** Тиков на одну починку: проверка уводит в неё, починка её исполняет. */
    private static final Integer TICKS_PER_ATTEMPT = 2;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        MarketDataSubstrate.register(registry, axes());
    }

    /**
     * Ожидание взято из дома: {@code CandleGroup} объявляет счётчик
     * попыток своим полем, то есть гарантия «N попыток → {@code ERROR}»
     * переживает рестарт (docs/models/domain/other/CandleGroup.md
     * §Структура).
     *
     * <p>Сегодня счётчик лежит в памяти процесса
     * ({@code CandleLoader.repairAttempts}), и после перезапуска отсчёт
     * начинается заново — группа с неустранимой дырой не доходит до
     * терминала никогда. Долг — `.claude/work/backlog.md` §«Дефекты
     * донора, воспроизведённые портом».
     */
    @Test
    @Tag("debt")
    @DisplayName("B3.9 — счётчик попыток не переживает рестарт")
    void b3_9_theAttemptCounterDoesNotSurviveARestart() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.candles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, 100, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        rows.put("delete from candles where open_timestamp between ? and ?",
                barsAgo(HOUR_MILLIS, 350), barsAgo(HOUR_MILLIS, 348));
        connector.answers(ConnectorStub.HISTORY_CANDLES, Feed.empty());
        // Первая половина: проверка уводит группу в починку, починка
        // расходует единственную попытку и возвращает её к проверке.
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        assertThat(status()).isEqualTo("CHECK");

        try (ConfigurableApplicationContext restarted = restart()) {
            Integer port = ((WebServerApplicationContext) restarted).getWebServer().getPort();
            // РОВНО столько тиков, сколько нужно на ОДНУ следующую починку:
            // при пережившем рестарт счётчике она вторая по счёту, то есть
            // сверх бюджета, и обязана дать терминал. Больший бюджет
            // израсходовал бы новый отсчёт с нуля и сделал бы клетку
            // зелёной, ничего о рестарте не утверждая.
            for (int index = 0; index < TICKS_PER_ATTEMPT; index++) {
                tickOn(port);
            }
        }

        assertThat(status()).isEqualTo("ERROR");
    }

    /**
     * Поднимает второй контекст той же формы на ту же базу.
     *
     * <p><b>Оси подаются АРГУМЕНТАМИ запуска, а не умолчаниями.</b>
     * Умолчания приложения стоя́т НИЖЕ его собственного
     * {@code application.yaml}, где адрес базы объявлен пустым, — поданный
     * умолчанием, он бы им и перекрылся, и контекст не поднялся бы вовсе.
     */
    private ConfigurableApplicationContext restart() {
        Map<String, String> properties = new LinkedHashMap<>(MarketDataSubstrate.defaults());
        properties.putAll(axes());
        properties.put("server.port", "0");
        String[] arguments = properties.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(MarketDataApplication.class).run(arguments);
    }

    private void tickOn(Integer port) {
        Integer mark = AppLog.mark();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + Tick.CANDLES.path()))
                .header("Authorization", "Bearer " + identity.serviceToken())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        try {
            CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new IllegalStateException("Перезапущенный ящик не ответил", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание перезапущенного ящика прервано", failure);
        }
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50))
                .until(() -> AppLog.since(mark).contains(Tick.CANDLES.finishedMark()));
    }

    private static Map<String, String> axes() {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("candle-loading.max-repair-attempts", "1");
        return axes;
    }

    private String status() {
        return String.valueOf(rows.all("candle_groups").getFirst().get("status"));
    }
}
