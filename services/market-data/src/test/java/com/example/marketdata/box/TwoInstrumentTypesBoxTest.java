package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Два типа инструментов в конфигурации — клетки {@code B2.5} и
 * {@code B9.3} документа `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст, потому что перечень типов есть ВХОД обоих обходов
 * площадки.</b> Настройка говорит, что зеркалится в каталог, а не что
 * собирается: сбор определяет требование потребителя
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»).
 */
class TwoInstrumentTypesBoxTest extends MarketDataBox {

    private static final String FUTURES_INSTRUMENT = "BTC-USDT-250926";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("connector.instrument-types", "SWAP,FUTURES");
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B2.5 — рядовой отказ чтения тик не роняет")
    void b2_5_anOrdinaryReadFailureDoesNotBreakTheTick() {
        connector.answersWhen(ConnectorStub.INSTRUMENTS, "externalInstrumentType", "SWAP",
                Feed.array(Feed.instrument(INSTRUMENT, "BTC", "USDT")));
        connector.answersWhen(ConnectorStub.INSTRUMENTS, "externalInstrumentType", "FUTURES",
                500, Feed.refusal("EXCHANGE_READ_FAILED"));
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rules(INSTRUMENT));
        Integer mark = AppLog.mark();

        tick(Tick.INSTRUMENT_SYNC);

        assertThat(rows.count("instruments")).isEqualTo(1L);
        assertThat(rows.row("instruments", "external_id", INSTRUMENT).get("external_rules")).isNotNull();
        assertThat(AppLog.since(mark)).contains("Instrument listing sync failed for instType=FUTURES");
        assertThat(connector.count(ConnectorStub.rulesOf(INSTRUMENT))).isEqualTo(1);
    }

    @Test
    @DisplayName("B9.3 — типы инструментов — вход обоих обходов площадки")
    void b9_3_instrumentTypesAreTheInputOfBothExchangeRounds() {
        connector.answersWhen(ConnectorStub.INSTRUMENTS, "externalInstrumentType", "SWAP",
                Feed.array(Feed.instrument(INSTRUMENT, "BTC", "USDT")));
        connector.answersWhen(ConnectorStub.INSTRUMENTS, "externalInstrumentType", "FUTURES",
                Feed.array(Feed.instrument(FUTURES_INSTRUMENT, "BTC", "USDT")));
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rules(INSTRUMENT));
        connector.answers(ConnectorStub.rulesOf(FUTURES_INSTRUMENT), Feed.rules(FUTURES_INSTRUMENT));
        tick(Tick.INSTRUMENT_SYNC);
        connector.answers(ConnectorStub.TICKERS, Feed.map(
                Feed.ticker(INSTRUMENT, System.currentTimeMillis(), "50000"),
                Feed.ticker(FUTURES_INSTRUMENT, System.currentTimeMillis(), "50100")));
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(1L, 3));
        connector.answers(ConnectorStub.orderBookOf(FUTURES_INSTRUMENT), Feed.orderBook(1L, 3));
        connector.forgetRequests();

        tick(Tick.SNAPSHOTS);

        assertThat(rows.count("instruments")).isEqualTo(2L);
        assertThat(connector.count(ConnectorStub.TICKERS)).isEqualTo(2);
        assertThat(connector.count(ConnectorStub.MARK_PRICES)).isEqualTo(2);
        assertThat(typesAsked(ConnectorStub.TICKERS)).containsExactlyInAnyOrder("SWAP", "FUTURES");
        assertThat(typesAsked(ConnectorStub.MARK_PRICES)).containsExactlyInAnyOrder("SWAP", "FUTURES");
    }

    private java.util.List<String> typesAsked(String path) {
        return connector.requests(path).stream()
                .map(request -> request.getUrl().replaceAll(".*externalInstrumentType=", ""))
                .toList();
    }
}
