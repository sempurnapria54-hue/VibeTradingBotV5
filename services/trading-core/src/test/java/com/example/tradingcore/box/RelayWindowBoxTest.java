package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окно чтения реле — клетка {@code B9.2}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> предусловие
 * требует строк БОЛЬШЕ окна, а окно приезжает конфигурацией. Ставить его
 * общему ящику значило бы менять предмет всем его соседкам, а копить
 * двести строк постановками — платить временем за то, что задаётся одной
 * осью.
 *
 * <p><b>Тенантов ДВА, и они вперемешку.</b> Ключ партиции — тенант, и
 * утверждение «внутри каждого тенанта порядок публикации совпал с порядком
 * записи» без второго тенанта предмета не имеет: с одним тенантом оно
 * вырождается в порядок всей темы.
 *
 * <p><b>Своя группа потребителя</b> — довод у шапки
 * {@link TradingCoreSubstrate}.
 */
class RelayWindowBoxTest extends TradingCoreBox {

    /** Окно чтения неопубликованных строк: строк у клетки будет больше. */
    private static final String WINDOW = "4";

    /** Третий счёт: им порядок записи становится вперемешку по тенантам. */
    private static final String THIRD_ACCOUNT = "A3";

    /** Второй тенант: он и делает порядок внутри тенанта наблюдаемым. */
    private static final String SECOND_TENANT = "T2";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-relay-window";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.RELAY_WINDOW_KEY, WINDOW));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B9.2 — реле публикует окном и в порядке записи")
    void theRelayPublishesOneWindowPerTickInWriteOrder() {
        Map<String, String> byTenant = new LinkedHashMap<>();
        byTenant.put(ACCOUNT, TENANT);
        byTenant.put(SECOND_ACCOUNT, SECOND_TENANT);
        byTenant.put(THIRD_ACCOUNT, TENANT);
        provisionAccountsOfTenants(byTenant);
        // Постановка пишет две строки, и три постановки дают шесть — больше
        // окна в четыре строки.
        freeze(ACCOUNT);
        freeze(SECOND_ACCOUNT);
        freeze(THIRD_ACCOUNT);
        List<String> written = rowEventIds();
        assertThat(written).hasSize(6);

        Wire.Mark first = Wire.mark();
        tick(Tick.OUTBOX_RELAY);
        List<Wire.Published> firstTick = Wire.publishedSince(first);
        Wire.Mark second = Wire.mark();
        tick(Tick.OUTBOX_RELAY);
        List<Wire.Published> secondTick = Wire.publishedSince(second);

        // Первый тик опубликовал РОВНО окно, второй — остаток: упор в окно
        // неполнотой прохода не считается.
        assertThat(firstTick).hasSize(4);
        assertThat(secondTick).hasSize(2);
        // Внутри каждого тенанта порядок публикации совпал с порядком записи.
        List<Wire.Published> all = concat(firstTick, secondTick);
        assertThat(eventIdsOfTenant(all, TENANT)).isEqualTo(rowEventIdsOfTenant(TENANT));
        assertThat(eventIdsOfTenant(all, SECOND_TENANT)).isEqualTo(rowEventIdsOfTenant(SECOND_TENANT));
    }

    /** Опубликованное двумя тиками подряд, в порядке публикации. */
    private List<Wire.Published> concat(List<Wire.Published> first, List<Wire.Published> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    /** Идентичности опубликованных записей названного тенанта. */
    private List<String> eventIdsOfTenant(List<Wire.Published> published, String tenantInternalId) {
        return published.stream()
                .filter(record -> tenantInternalId.equals(record.key()))
                .map(Wire.Published::eventId)
                .toList();
    }

    /** Идентичности событий всех строк outbox в порядке записи. */
    private List<String> rowEventIds() {
        return rows.all("outbox_events").stream().map(row -> String.valueOf(row.get("event_id"))).toList();
    }

    /** Идентичности событий строк названного тенанта в порядке записи. */
    private List<String> rowEventIdsOfTenant(String tenantInternalId) {
        return rows.rowsWhere("outbox_events", "tenant_id", tenantInternalId).stream()
                .map(row -> String.valueOf(row.get("event_id")))
                .toList();
    }
}
