package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Незаданный адрес коннектора — клетка {@code B9.2} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Граница между «не поднимается» и «отказывает на вызове» —
 * предмет клетки.</b> Контур доступа ненастроенным не поднимает процесс
 * вовсе ({@code B9.1}), а ненастроенный адрес соседа процесс поднимает:
 * тропы, соседа не зовущие, работают, а зовущие отказывают на вызове.
 *
 * <p><b>Класса у этого отказа не объявляет ни один дом</b> (находка
 * {@code F-8}): {@code application.yaml} говорит, что незаданное
 * означает отказ, но каким классом отказывает чтение, не сказано нигде.
 * Поэтому клетка мерит то, что дом объявляет однозначно, — что запрос не
 * уходит ни на какой адрес и что тик каталога не меняет, — а класс
 * записывает в «Факт» как наблюдение.
 */
class UnconfiguredConnectorAddressBoxTest extends MarketDataBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("connector.base-url", "");
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B9.2 — незаданный адрес коннектора отказывает на вызове, а не на подъёме")
    void b9_2_anUnsetConnectorAddressFailsOnTheCallNotOnStartup() {
        rows.put("insert into instruments (internal_id, exchange_code, external_id, external_type, status) "
                + "values ('MD-B9-2', ?, ?, 'SWAP', 'ACTIVE')",
                MarketDataSubstrate.EXCHANGE_CODE, INSTRUMENT);
        Map<String, Long> before = rows.countsByTable();
        Integer mark = AppLog.mark();

        Answer prices = get(INSTRUMENTS + "/MD-B9-2/prices");
        tick(Tick.INSTRUMENT_SYNC);

        // Процесс поднялся: тропа, соседа не зовущая, отвечает штатно.
        assertThat(get(INSTRUMENTS).status()).isEqualTo(200);
        assertThat(prices.carriesErrorDto()).isTrue();
        assertThat(rows.countsByTable()).isEqualTo(before);
        assertThat(AppLog.since(mark)).contains("Instrument listing sync failed");
        // Ни один запрос не ушёл на случайный адрес: стаб коннектора
        // поднят и отвечает, но его адрес сервису не задан.
        assertThat(connector.count()).isEqualTo(0);
        assertThat(neighbours).allSatisfy(neighbour -> assertThat(neighbour.count()).isEqualTo(0));
    }
}
