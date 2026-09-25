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
 * Группа {@code B13} в части схемы и конфигурации как входа:
 * {@code B13.4}-{@code B13.6} и штатная половина {@code B13.7}.
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
 *
 * <p><b>Отображение сырого типа движения читается на ТРОПЕ ВЫХОДА</b>
 * ({@code B13.6}): движения добывает только координированный выход сделки,
 * поэтому клетка стоит на живой сделке общей сборки ({@link LiveDealBox})
 * и доводит её до терминала удалением определения.
 */
class SchemaInputBoxTest extends SharedLiveDealBox {

    /** Версии миграций дерева: перечень закрыт каталогом `db/migration`. */
    private static final List<String> MIGRATIONS = List.of("1", "2", "3", "4", "5", "6", "7", "8");

    /** Движение, чей сырой тип отображение контура знает. */
    private static final String MAPPED_BILL = "bill-close-1";

    /** Движение, чьего сырого типа в отображении контура нет. */
    private static final String UNMAPPED_BILL = "bill-unmapped-1";

    /** Код отчёта расхождения сверки результата сделки. */
    static final String RECONCILIATION_MISMATCH = "PNL_RECONCILIATION_MISMATCH";

    /** Сумма движения закрытия у клеток о допуске сверки. */
    static final String BILL = "-5";

    /** Запись закрытия, расходящаяся с движением на 0.01 — внутри штатного пола. */
    static final String WITHIN_RECORD = "-5.01";

    /** Запись закрытия, расходящаяся с движением на 2 — сверх допуска при любом обороте сделки. */
    static final String BEYOND_RECORD = "-7";

    /** Код отчёта о непустой принимающей корзине движений. */
    private static final String UNCLASSIFIED_CASH_FLOW = "UNCLASSIFIED_CASH_FLOW";

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
    @DisplayName("B13.6 — отображение сырого типа движения в категорию — вход конфигурации")
    void theRawCashFlowTypeMappingIsAConfigurationInput() {
        openLiveDeal();
        standExchangeFollowingCommands("-5");
        // Два движения закрытия: тип из отображения контура и тип вне его.
        connector.answers(billsPath(ACCOUNT), Feed.array(
                Feed.bill(MAPPED_BILL, "2", null, "-5", "ex-close-1", CLOSED_AT),
                Feed.bill(UNMAPPED_BILL, "99", "7", "0.01", "ex-close-1", CLOSED_AT)));

        exitByDeletion(workingDefinition());
        passesUntilDealTerminal();

        // Тип из отображения лёг в свою категорию, тип вне его — в
        // принимающую корзину, и корзина заявила о себе журнальным
        // отчётом: разведочное состояние видно в данных.
        assertThat(rows.row("deal_cash_flows", "external_bill_id", MAPPED_BILL).get("category"))
                .isEqualTo("REALIZED_PNL");
        assertThat(rows.row("deal_cash_flows", "external_bill_id", UNMAPPED_BILL).get("category"))
                .isEqualTo("OTHER");
        Map<String, Object> report = rows.row("anomaly_reports", "code", UNCLASSIFIED_CASH_FLOW);
        assertThat(report.get("status")).isEqualTo("COMPLETED");
        assertThat(accountRung()).isEqualTo(NO_RUNG);
    }

    @Test
    @DisplayName("B13.7 — числа допуска сверки и режим — вход, а не константа")
    void theReconciliationToleranceNumbersAreAnInput() {
        // Расхождение ВНУТРИ штатного допуска: пол больше разницы.
        closeLiveDealWith("S-TOLERANCE-1", WITHIN_RECORD, BILL);
        assertThat(dealRow().get("reconciliation_status")).isEqualTo("MATCHED");
        assertThat(codesOfReports()).doesNotContain(RECONCILIATION_MISMATCH);

        // Расхождение СВЕРХ допуска: признак и отчёт. Вторая половина кейса
        // — тот же вход при иных числах допуска — живёт своим контекстом
        // (NarrowToleranceBoxTest).
        closeLiveDealWith("S-TOLERANCE-2", BEYOND_RECORD, BILL);
        assertThat(dealRow().get("reconciliation_status")).isEqualTo("MISMATCHED");
        // Режим штатно разведочный: отчёт есть, ступени нет
        // (docs/rules/pnl-reconciliation.md §«Реакция на расхождение»).
        assertThat(codesOfReports()).contains(RECONCILIATION_MISMATCH);
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
