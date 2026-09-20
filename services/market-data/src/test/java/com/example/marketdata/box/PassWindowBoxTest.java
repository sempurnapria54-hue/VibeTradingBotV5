package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окно прохода сбора — клетка {@code B5.10} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст, потому что потолок прохода есть ось
 * конфигурации.</b> Предмет клетки — не то, что листинг усечён (это
 * следствие потолка), а то, что усечён он СТАБИЛЬНО: два прохода подряд
 * берут одни и те же инструменты в одном и том же порядке. Случайный
 * хвост означал бы, что часть листинга не снимается никогда, и узнать
 * об этом было бы неоткуда.
 */
class PassWindowBoxTest extends MarketDataBox {

    private static final Long EXCHANGE_MOMENT = 1_758_000_000_000L;

    private static final List<String> LISTING = List.of(
            "AAA-USDT-SWAP", "BBB-USDT-SWAP", "CCC-USDT-SWAP", "DDD-USDT-SWAP", "EEE-USDT-SWAP");

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("snapshot-collection.pass-limit", "3");
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B5.10 — порядок обхода стабилен, а окно прохода ограничено")
    void b5_10_theRoundOrderIsStableAndThePassWindowIsBounded() {
        provisionInstruments(LISTING.toArray(String[]::new));
        connector.answers(ConnectorStub.TICKERS, Feed.emptyMap());
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        LISTING.forEach(externalId -> connector.answers(
                ConnectorStub.orderBookOf(externalId), Feed.orderBook(EXCHANGE_MOMENT, 3)));

        tick(Tick.SNAPSHOTS);
        List<String> first = walkedInstruments();
        connector.forgetRequests();
        tick(Tick.SNAPSHOTS);
        List<String> second = walkedInstruments();

        assertThat(first).hasSize(3);
        assertThat(first).containsExactly(LISTING.get(0), LISTING.get(1), LISTING.get(2));
        assertThat(second).isEqualTo(first);
    }

    private List<String> walkedInstruments() {
        return connector.paths().stream()
                .filter(path -> path.startsWith(ConnectorStub.ORDER_BOOK))
                .map(path -> path.substring(ConnectorStub.ORDER_BOOK.length() + 1))
                .toList();
    }
}
