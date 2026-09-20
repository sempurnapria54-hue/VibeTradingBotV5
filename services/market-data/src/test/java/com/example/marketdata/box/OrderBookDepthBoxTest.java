package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Глубина книги заявок — клетка {@code B5.13} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст, потому что глубина есть ось конфигурации и её
 * умолчание клетке не годится:</b> при умолчании кейс не отличил бы
 * «глубина уехала в запрос» от «площадка отдала столько сама».
 *
 * <p>Величина провизорна, и направление ошибки названо домом: реже и
 * мельче, чем чаще и глубже
 * (docs/architecture/market-data-collection.md §«Невосполнимые срезы»).
 */
class OrderBookDepthBoxTest extends MarketDataBox {

    private static final Long EXCHANGE_MOMENT = 1_758_000_000_000L;

    private static final Integer DEPTH = 5;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("snapshot-collection.order-book-depth", String.valueOf(DEPTH));
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B5.13 — глубина книги — величина конфигурации и уезжает в запрос")
    void b5_13_theBookDepthIsAConfigurationValueAndTravelsIntoTheRequest() {
        provisionInstruments(INSTRUMENT);
        connector.answers(ConnectorStub.TICKERS, Feed.emptyMap());
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, DEPTH));

        tick(Tick.SNAPSHOTS);

        assertThat(connector.single(ConnectorStub.orderBookOf(INSTRUMENT)).getUrl())
                .contains("depth=" + DEPTH);
        Map<String, Object> snapshot = rows.all("order_book_snapshots").getFirst();
        assertThat(levels(snapshot, "bids")).isLessThanOrEqualTo(DEPTH);
        assertThat(levels(snapshot, "asks")).isLessThanOrEqualTo(DEPTH);
    }

    private Integer levels(Map<String, Object> snapshot, String side) {
        String written = String.valueOf(snapshot.get(side));
        return written.split("\"price\"", -1).length - 1;
    }
}
