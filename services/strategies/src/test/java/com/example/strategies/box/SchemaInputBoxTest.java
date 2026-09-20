package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B9} в части схемы как входа: {@code B9.4},
 * {@code B9.5}, {@code B9.6}
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Схема наблюдается СНАРУЖИ ящика</b> — своим соединением к
 * контейнеру ({@link Rows}), а не бином сервиса: объявленного читателя
 * вне кода сервиса у этих таблиц нет ни одного, и документ кейсов
 * называет прямой ассерт по ним ценой, а не умолчанием.
 *
 * <p><b>Инвариант доказывается ОТКАЗОМ базы, а не молчанием.</b> Клетка
 * {@code B9.5} шлёт запрещённые строки прямым запросом — то есть мимо
 * всякой проверки приложения: держись гейт только кодом, запрос прошёл
 * бы, и клетка это и показала бы.
 */
class SchemaInputBoxTest extends SharedStrategiesBox {

    /** Версии миграций дерева: перечень закрыт каталогом `db/migration`. */
    private static final List<String> MIGRATIONS = List.of("1");

    /** Таблицы дерева определения плюс outbox: их заводит миграция. */
    private static final List<String> OWN_TABLES = List.of("strategies", "strategy_details",
            "strategy_tranches", "strategy_steps", "strategy_actions", "strategy_order_actions",
            "strategy_algo_order_actions", "strategy_position_actions", "strategy_indicator_settings",
            "strategy_market_structure_settings", "strategy_market_phase_settings", "outbox_events");

    /** Отказ базы приходит исключением наблюдателя с текстом ограничения. */
    private static final String REFUSED = "База субстрата отвергла запись";

    @Test
    @DisplayName("B9.4 — Схема накатывается миграциями и сверяется с отображением")
    void b9_4_theSchemaIsMigratedAndValidatedAgainstTheMapping() {
        List<String> applied = rows.appliedMigrations();

        assertThat(applied)
                .as("цепочка миграций проходит на ЧИСТОЙ базе целиком")
                .containsExactlyInAnyOrderElementsOf(MIGRATIONS);
        assertThat(rows.tableNames())
                .as("сверка отображения со схемой состоялась на старте: ddl-auto=validate роняет подъём")
                .containsAll(OWN_TABLES);
        assertThat(OWN_TABLES)
                .as("имена таблиц — во множественном числе (.claude/rules/codestyle.md §«Схема БД»)")
                .allMatch(table -> table.endsWith("s"));
    }

    @Test
    @DisplayName("B9.5 — Инварианты схемы держатся вторым носителем")
    void b9_5_theSchemaInvariantsAreHeldByTheDatabaseRatherThanByTheApplication() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        Map<String, Object> definition = rows.row(STRATEGIES_TABLE, "internal_id", internalId);

        // 1. Вторая строка с той же идентичностью определения.
        assertThatThrownBy(() -> insertStrategy(internalId, "CREATED",
                String.valueOf(definition.get("exchange_account_internal_id")), "i9-usdt-swap"))
                .hasMessageContaining(REFUSED);

        // 2. Вторая ACTIVE на той же паре «счёт, инструмент».
        assertThatThrownBy(() -> insertStrategy("S-RIVAL", "ACTIVE",
                String.valueOf(definition.get("exchange_account_internal_id")),
                String.valueOf(definition.get("instrument_internal_id"))))
                .hasMessageContaining(REFUSED);

        // 3. Транш с числом уровней меньше единицы.
        Object detailId = rows.all("strategy_details").getFirst().get("id");
        assertThatThrownBy(() -> rows.write("""
                insert into strategy_tranches (strategy_detail_id, key, level_count)
                values (?, 'box-tranche', 0)
                """, detailId))
                .hasMessageContaining(REFUSED);

        // 4. Шаг, принадлежащий сразу двум владельцам.
        Object trancheId = rows.all("strategy_tranches").getFirst().get("id");
        assertThatThrownBy(() -> rows.write("""
                insert into strategy_steps (strategy_tranche_id, tranche_status, strategy_detail_id,
                                            deal_status, step_index, step_type, condition,
                                            market_data_expired_setting)
                values (?, 'PENDING', ?, 'ACTIVE', 99, 'ENTRY', '{}'::jsonb, '{}'::jsonb)
                """, trancheId, detailId))
                .hasMessageContaining(REFUSED);

        assertThat(rows.countWhere(STRATEGIES_TABLE, "internal_id", internalId))
                .as("ни одна из запрещённых строк не прошла")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B9.6 — Числовых ключей чужих баз схема не держит")
    void b9_6_theSchemaKeepsNoNumericKeysOfForeignDatabases() {
        List<String> columns = rows.columnNames(STRATEGIES_TABLE);

        assertThat(columns)
                .as("счёт, инструмент и тенант названы ИДЕНТИЧНОСТЯМИ")
                .contains("exchange_account_internal_id", "instrument_internal_id",
                        "tenant_internal_id");
        assertThat(columns)
                .as("числовых ключей чужих реестров среди колонок нет ни одного")
                .doesNotContain("exchange_account_id", "instrument_id", "tenant_id");
        assertThat(rows.tableNames())
                .as("и самих чужих реестров схема не держит: ссылаться ей не на что")
                .doesNotContain("exchange_accounts", "instruments", "tenants");
    }

    /**
     * Прямая вставка определения мимо приложения.
     *
     * @param internalId  идентичность определения
     * @param status      статус жизненного цикла
     * @param accountId   идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     */
    private void insertStrategy(String internalId, String status, String accountId, String instrumentId) {
        rows.write("""
                insert into strategies (internal_id, tenant_internal_id, exchange_account_internal_id,
                                        instrument_internal_id, name, status)
                values (?, ?, ?, ?, 'box', ?)
                """, internalId, TENANT, accountId, instrumentId, status);
    }
}
