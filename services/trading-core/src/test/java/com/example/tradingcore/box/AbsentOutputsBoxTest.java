package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B14} — отсутствие выходов.
 *
 * <p><b>Клейм отсутствия читается по ОБЪЯВЛЕННОЙ поверхности, а не по
 * тому, что кейс не догадался позвать.</b> Перечень точек сервис печатает
 * сам — описанием OpenAPI, — и утверждение «точки, правящей определение,
 * нет» проверяется по нему целиком, а не перебором догадок. У перечня
 * стои́т базовый гейт непустоты: пустое описание сделало бы всякое
 * отрицание верным на пустом месте.
 *
 * <p><b>Вторая половина отрицаний — записи стабов.</b> Адресов, на которые
 * ядро вправе ходить, ровно четыре — три соседа по ярусу и точки
 * провайдера идентичности; всё, что ушло бы мимо них, стабами не
 * ловится, и это НАЗВАННОЕ ограничение наблюдателя: что исходящих адресов
 * у процесса ровно столько, читается конфигурацией.
 */
class AbsentOutputsBoxTest extends SharedTradingCoreBox {

    /** Описание объявленной поверхности: его печатает сам сервис. */
    private static final String API_DOCS = "/v3/api-docs";

    /** Основа идентичности определения, принимаемого от его владельца. */
    private static final String DEFINITION = "S-ABSENT";

    /** Класс события подъёма ступени: им мерится, что публикация работает. */
    private static final String HOLD_RAISED = "HOLD_RAISED";

    /** Имя класса о СНЯТИИ ступени: перечень классов ядра его не несёт. */
    private static final String HOLD_RELEASED = "HOLD_RELEASED";

    /** Машинный код журнальной строки ручного снятия. */
    private static final String MANUAL_HALT_CLEARED = "MANUAL_HALT_CLEARED";

    /**
     * Перечень классов события ядра целиком
     * ({@code CoreEventType}) — он закрыт, и опубликованное из него не
     * выходит.
     */
    private static final List<String> CORE_EVENT_TYPES = List.of("ORDER_DECIDED", "DEAL_OPENED",
            "DEAL_SHUTDOWN_INITIATED", "DEAL_CLOSED", HOLD_RAISED, "ANOMALY_REPORTED");

    @Test
    @DisplayName("B14.1 — ядро не говорит с площадкой напрямую")
    void theCoreNeverTalksToTheVenueDirectly() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        connector.answersAnything(Feed.emptyArray());
        marketData.answersAnything(Feed.emptyArray());

        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.ANOMALY_DETECTION);
        tick(Tick.TRADE_FEE_RATES);

        // Всё, что ушло наружу, ушло на адреса соседей: своей подписи ядро
        // не строит, ключей биржевого счёта не читает — хранилища секретов
        // в субстрате нет вовсе, и весь набор троп прошёл.
        assertThat(connector.paths()).allMatch(path -> path.startsWith("/api/v1/"));
        assertThat(AppLog.text()).doesNotContain("OK-ACCESS-SIGN");
    }

    @Test
    @DisplayName("B14.2 — определением стратегии ядро не владеет")
    void theCoreOwnsNoStrategyDefinition() {
        String declared = surface();

        // Точки, правящей определение, на поверхности нет ни одной:
        // статус копии двигает только приём события.
        assertThat(declared).doesNotContain("/strategies");
        assertThat(declared).doesNotContain("strategy-definitions");
        assertThat(rows.count("strategies")).isZero();
    }

    @Test
    @DisplayName("B14.3 — рыночных данных ядро не считает")
    void theCoreComputesNoMarketData() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.answersAnything(Feed.emptyArray());

        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.ENTRY_SCANNER);

        // Своих рядов у ядра нет ни одной таблицей: посчитанное оно читает
        // у владельца по месту.
        assertThat(rows.tableNames())
                .doesNotContain("candles", "candle_groups", "indicator_values", "market_structures");
    }

    @Test
    @DisplayName("B14.4 — журнал истории ядро не читает")
    void theCoreNeverReadsTheHistoryJournal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        connector.answersAnything(Feed.emptyArray());
        marketData.answersAnything(Feed.emptyArray());

        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.ENTRY_SCANNER);
        tick(Tick.ANOMALY_DETECTION);

        // Адресов сервисов истории у ядра не объявлено ни одной осью
        // конфигурации: журнал есть след, а не источник состояния.
        assertThat(surface()).doesNotContain("/audit").doesNotContain("/statistics");
        assertThat(AppLog.text()).doesNotContain("neighbours.audit").doesNotContain("neighbours.statistics");
    }

    @Test
    @DisplayName("B14.5 — в чужие базы ядро не ходит")
    void theCoreConnectsToItsOwnDatabaseOnly() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));

        // Проекции наполнены ВЫЗОВОМ к владельцу, а не чтением его базы:
        // строки появились ровно после обращения к его поверхности.
        assertThat(rows.count("exchange_accounts")).isEqualTo(1L);
        assertThat(rows.count("instruments")).isEqualTo(1L);
        // Схема у процесса одна — своя: чужих таблиц в ней нет.
        assertThat(rows.tableNames()).doesNotContain("memberships", "tenants", "audit_records");
    }

    @Test
    @DisplayName("B14.6 — ручного перевода сделки в терминал мимо FSM нет")
    void noSurfacePointSetsADealStatus() {
        String declared = surface();

        // Поверхность объявляет по сделкам только ЧТЕНИЯ: точки, ставящей
        // статус, среди них нет.
        assertThat(declared).contains("/api/v1/trading-core/deals");
        assertThat(declared).doesNotContain("/deals/{internalId}/status");
        assertThat(declared).doesNotContain("/deals/{internalId}/closures");
    }

    @Test
    @DisplayName("B14.7 — событие, которого ядро не публикует")
    void theCorePublishesNeitherAHoldReleaseNorAForeignDefinitionFact() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Wire.Mark mark = Wire.mark();
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        assertThat(post(HALT_CLEARANCES, Bodies.halt("FREEZE", ACCOUNT)).status()).isEqualTo(204);
        activate(Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT));

        tick(Tick.OUTBOX_RELAY);

        // Базовый гейт: публикация у ядра РАБОТАЕТ — подъём ступени в теме
        // есть. Без него отрицания ниже были бы верны на пустом месте.
        List<String> published = Wire.publishedSince(mark).stream()
                .map(Wire.Published::eventType)
                .toList();
        assertThat(published).contains(HOLD_RAISED);
        // Снятия ступени в теме нет ни одной строкой: класса `HoldReleased`
        // у ядра не существует, и писателя у него нет (долг —
        // .claude/work/backlog.md §«Класс события `HoldReleased` и его
        // ручная тропа»). След снятия остаётся ОТЧЁТОМ, а не фактом ребра.
        assertThat(published).doesNotContain(HOLD_RELEASED);
        assertThat(rows.row("anomaly_reports", "code", MANUAL_HALT_CLEARED)).isNotEmpty();
        // Чужого определения ядро не переопубликовывает: потребление
        // следствия в теме не порождает — копия заведена, а классов
        // владельца определений в теме ядра нет.
        assertThat(rows.count("strategies")).isEqualTo(1L);
        assertThat(published).doesNotContain(STRATEGY_ACTIVATED, STRATEGY_DEACTIVATED,
                STRATEGY_DELETED);
        // Перечень классов ядра закрыт, и опубликованное из него не
        // выходит: отрицание поимённых классов выше не зависит от того,
        // какое имя придумал бы писатель.
        assertThat(CORE_EVENT_TYPES).containsAll(published);
    }

    /**
     * Объявленная поверхность сервиса дословно.
     *
     * <p><b>Базовый гейт непустоты:</b> описание, которого сервис не отдал,
     * сделало бы всякое отрицание группы верным на пустом месте.
     */
    private String surface() {
        Answer answer = get(API_DOCS);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).contains("/api/v1/trading-core");
        return answer.body();
    }
}
