package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B13} в части схемы как входа: {@code B13.4} и
 * {@code B13.5}.
 *
 * <p><b>Схема наблюдается СНАРУЖИ ящика</b> — своим соединением к
 * контейнеру ({@link Rows}), а не бином сервиса: читателя вне кода ядра у
 * этих таблиц нет ни одного, и документ кейсов называет прямой ассерт их
 * ценой, а не умолчанием.
 *
 * <p><b>Инвариант доказывается ОТКАЗОМ базы, а не молчанием.</b> Клетка
 * {@code B13.5} шлёт вторую строку прямым запросом — то есть мимо всякой
 * проверки приложения: если бы гейт держался кодом, запрос прошёл бы, и
 * клетка это и показала бы.
 */
class SchemaInputBoxTest extends SharedTradingCoreBox {

    /** Версии миграций дерева: перечень закрыт каталогом `db/migration`. */
    private static final List<String> MIGRATIONS = List.of("1", "2", "3", "4", "5", "6", "7", "8");

    @Test
    @DisplayName("B13.4 — схема накатывается миграциями и сверяется с отображением")
    void theSchemaIsMigratedAndValidatedAgainstTheMapping() {
        List<String> applied = rows.appliedMigrations();

        // Все миграции применены — то есть цепочка проходит на ЧИСТОЙ базе.
        // До этого прогона она не проходила вовсе: V4 заводила таблицу,
        // уже заведённую V1, и падала на любой пустой схеме.
        assertThat(applied).containsExactlyInAnyOrderElementsOf(MIGRATIONS);
        // Сверка отображения со схемой состоялась на старте: контекст
        // поднят, а `ddl-auto: validate` роняет подъём на расхождении.
        assertThat(rows.tableNames()).contains("deals", "deal_tranches", "outbox_events", "inbox_events");
    }

    @Test
    @DisplayName("B13.5 — ключевые инварианты схемы стоя́т и отказывают")
    void theKeyInvariantsAreHeldByTheDatabaseRatherThanByTheApplication() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Long account = accountId(ACCOUNT);
        Long instrument = instrumentId(INSTRUMENT);

        insertDeal(account, instrument, "D1");

        // 1. Вторая активная сделка по той же паре.
        assertThatThrownBy(() -> insertDeal(account, instrument, "D2"))
                .hasMessageContaining("База субстрата не ответила");

        // 2. Вторая строка outbox с той же идентичностью события.
        insertOutbox("E1");
        assertThatThrownBy(() -> insertOutbox("E1"))
                .hasMessageContaining("База субстрата не ответила");

        // 3. Вторая строка чисел риск-аппетита того же тенанта.
        assertThatThrownBy(() -> rows.insert(
                "insert into tenant_risk_appetites (tenant_internal_id) values (?) returning id", TENANT))
                .hasMessageContaining("База субстрата не ответила");
    }

    /**
     * Сделка без объявления — то есть восстановительная: инвариант схемы
     * связывает пустую деталь с причиной входа {@code RECOVERY}, и иная
     * пара отвергается ещё до ключа, о котором клетка утверждает.
     */
    private void insertDeal(Long account, Long instrument, String internalId) {
        rows.insert("""
                insert into deals (internal_id, exchange_account_id, instrument_id, status, direction,
                                   entry_reason)
                values (?, ?, ?, 'ACTIVE', 'LONG', 'RECOVERY') returning id
                """, internalId, account, instrument);
    }

    private void insertOutbox(String eventId) {
        rows.insert("""
                insert into outbox_events (event_id, event_type, version, occurred_at, tenant_id, topic,
                                           payload)
                values (?, 'DEAL_OPENED', 1, ?, ?, 'trading-core.facts', '{}'::jsonb) returning id
                """, eventId, OffsetDateTime.now(ZoneOffset.UTC), TENANT);
    }
}
