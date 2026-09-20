package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Схема и хранилище как вход — клетки {@code B9.6} и {@code B9.8}
 * документа `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Обе клетки о том, чего у сервиса НЕТ и что у него ЕСТЬ по
 * построению.</b> Схема накатывается миграциями и сверяется с
 * отображением на подъёме — то есть колонка, объявленная моделью и не
 * заведённая миграцией, роняет старт, а не обнаруживается в проде.
 * Хранилища же секретов сервису не нужно вовсе: все его чтения площадки
 * публичные, и контейнера Vault прогон не поднимает.
 */
class ConfigurationInputBoxTest extends SharedMarketDataBox {

    @Test
    @DisplayName("B9.6 — схема накатывается миграциями и сверяется с отображением")
    void b9_6_theSchemaIsAppliedByMigrationsAndValidatedAgainstTheMapping() {
        // Контекст поднят: значит, сверка отображения с накатанной схемой
        // прошла — ddl-auto: validate роняет старт на расхождении.
        assertThat(rows.appliedMigrations()).contains("1");
        assertThat(rows.tableNames()).contains("instruments", "candle_groups", "candles",
                "indicator_configs", "market_structure_configs", "indicator_values",
                "market_structures", "order_book_snapshots", "ticker_snapshots");
        assertThat(rows.hypertableNames())
                .contains("candles", "order_book_snapshots", "ticker_snapshots");
    }

    @Test
    @DisplayName("B9.8 — хранилища секретов сервису не нужно")
    void b9_8_theServiceNeedsNoSecretStore() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.trendingCandles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, 100, 50000));
        connector.answers(ConnectorStub.TICKERS, Feed.emptyMap());
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(1L, 3));
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));

        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        tick(Tick.SNAPSHOTS);
        Answer prices = get(INSTRUMENTS + "/" + instrument + "/prices");
        Answer listing = get(INSTRUMENTS);

        // Контейнера хранилища в субстрате нет вовсе, и весь набор троп
        // прошёл: ключей биржевого счёта сервис не ищет ни у кого.
        assertThat(prices.status()).isEqualTo(200);
        assertThat(listing.status()).isEqualTo(200);
        assertThat(rows.count("candles")).isGreaterThan(0L);
        assertThat(connector.paths()).allSatisfy(path ->
                assertThat(path).startsWith("/api/v1/market/"));
    }
}
