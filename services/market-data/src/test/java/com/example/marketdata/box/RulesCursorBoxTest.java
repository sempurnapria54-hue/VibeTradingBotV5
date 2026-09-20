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
 * Окно обхода правил — клетка {@code B2.6} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст по двум причинам сразу.</b> Размер окна — ось
 * конфигурации, а позиция курсора — состояние процесса: обход идёт ЗА
 * курсором, и клетка о круге обхода требует, чтобы круг начинался с
 * начала. Контекст, доставшийся от соседки, начинал бы его с середины.
 */
class RulesCursorBoxTest extends MarketDataBox {

    private static final List<String> LISTING = List.of(
            "AAA-USDT-SWAP", "BBB-USDT-SWAP", "CCC-USDT-SWAP", "DDD-USDT-SWAP", "EEE-USDT-SWAP");

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("instrument-sync.rules-batch-size", "2");
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B2.6 — правила обходятся окном за курсором и по кругу")
    void b2_6_rulesAreWalkedByAWindowBehindTheCursorAndInACircle() {
        String[] listed = LISTING.stream()
                .map(externalId -> Feed.instrument(externalId, "BASE", "USDT"))
                .toArray(String[]::new);
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(listed));
        LISTING.forEach(externalId ->
                connector.answers(ConnectorStub.rulesOf(externalId), Feed.rules(externalId)));

        List<String> first = walked();
        List<String> second = walked();
        List<String> third = walked();
        List<String> fourth = walked();
        List<String> fifth = walked();

        assertThat(first).containsExactly(LISTING.get(0), LISTING.get(1));
        assertThat(second).containsExactly(LISTING.get(2), LISTING.get(3));
        assertThat(third).containsExactly(LISTING.get(4));
        // Пустое окно означает, что круг пройден: курсор возвращается в
        // начало и НИКОГО не обходит — иначе четвёртый тик обошёл бы
        // первое окно второй раз.
        assertThat(fourth).isEmpty();
        assertThat(fifth).containsExactly(LISTING.get(0), LISTING.get(1));
    }

    private List<String> walked() {
        connector.forgetRequests();
        tick(Tick.INSTRUMENT_SYNC);
        return LISTING.stream()
                .filter(externalId -> connector.count(ConnectorStub.rulesOf(externalId)) > 0)
                .toList();
    }
}
