package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B8} — приём фактов владельца определений.
 *
 * <p><b>Предмет группы — СООБЩЕНИЕ как вход, а выход его лежит в базе:</b>
 * строкой копии, её статусом и отметкой обработанного в inbox. Тика здесь
 * нет вовсе — ход начинается с записи в теме владельца
 * (.claude/tests/cases/trading-core.md §«Чем достаются выходы»).
 *
 * <p><b>Отрицания группы наблюдаются БАРЬЕРОМ, а не паузой</b>
 * ({@link TradingCoreBox#barrier()}): «второй копии не появилось» и
 * «дерево не переписано» суть утверждения о состоянии ПОСЛЕ обработки, а
 * пауза сказала бы только о том, что мы не успели посмотреть.
 *
 * <p><b>Идентичность события назначает кейс.</b> По ней идёт дедуп у
 * читателя, и повторная доставка — предмет отдельной клетки — выражается
 * ровно тем, что вторая запись несёт ТУ ЖЕ идентичность.
 *
 * <p><b>Пропуск отравленной записи наблюдается СТРОКОЙ СЛЕДА</b>
 * ({@code reception_skips}), а не журналом приложения: запись без конверта и
 * неразбираемое содержимое оставляют строку с координатами и первопричиной
 * (docs/rules/durable-consumer-reception.md §«След пропуска — таблица
 * `reception_skips`»). Журналом приложения ({@link AppLog}) несома только
 * ветвь без строки по построению — пропуск неизвестного класса, — и
 * повторы восстановления, которые строки не оставляют.
 */
class DefinitionIntakeBoxTest extends SharedTradingCoreBox {

    /** Таблица следа пропуска отравленной записи. */
    private static final String SKIPS = "reception_skips";

    /** Штатное имя группы ядра на теме определений — из конфигурации сервиса. */
    private static final String SHARED_GROUP = "trading-core.strategy-facts";

    /** Строка журнала о несостоявшейся записи следа: запись уходит в повтор. */
    private static final String TRAIL_REFUSED = "Strategy fact skip trail is not written, the record is retried";

    /** Строка журнала о состоявшемся пропуске. */
    private static final String SKIPPED = "Strategy fact is poison and is skipped";

    /** Первопричина следа, поставленного клеткой как след прежнего пропуска. */
    private static final String EARLIER_SKIP = "след прежнего пропуска той же записи";

    /**
     * Потолок ожидания второй доставки записи, чей след не лёг: между
     * доставками стоит пауза отложенного применения.
     */
    private static final Duration TRAIL_RETRY_TIMEOUT = Duration.ofSeconds(60);

    /** Идентичность определения, которым ходит большинство клеток. */
    private static final String DEFINITION = "S1";

    /** Второе определение: им наблюдается продолжение обработки. */
    private static final String SECOND_DEFINITION = "S2";

    /** Класс события вне перечня: производитель вправе завести его раньше читателя. */
    private static final String UNKNOWN_EVENT_TYPE = "STRATEGY_REHEARSED";

    /** Идентичность активации, чьё применение откладывается. */
    private static final String DEFERRED_EVENT = "ev-deferred-activation";

    /**
     * Потолок ожидания первой отложенной попытки: применение пробуется
     * сразу по приёму, и строка отложения ложится первой же неудачей.
     */
    private static final Duration DEFERRAL_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @DisplayName("B8.1 — активация заводит копию дерева и делает её активной")
    void anActivationCreatesTheWholeTreeAndMarksTheEventConsumed() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withComputations(DEFINITION, ACCOUNT, INSTRUMENT);

        String eventId = activate(definition);

        assertThat(rows.count("strategies")).isEqualTo(1L);
        // Дерево заведено целиком: оба индикаторных объявления и структурное.
        assertThat(rows.count("strategy_indicator_settings")).isEqualTo(2L);
        assertThat(rows.count("strategy_market_structure_settings")).isEqualTo(1L);
        // Отметка обработанного легла ТОЙ ЖЕ транзакцией: копия и отметка
        // существуют вместе, и порознь их не бывает.
        assertThat(rows.countWhere("inbox_events", "event_id", eventId)).isEqualTo(1L);
        assertThat(rows.row("inbox_events", "event_id", eventId).get("event_type"))
                .isEqualTo(STRATEGY_ACTIVATED);
        List<Object> nodes = indicatorNodeIds();

        // Повторная доставка того же сообщения: дедуп по идентичности события.
        redeliverActivation(eventId, definition);
        barrier();

        assertThat(rows.count("strategies")).isEqualTo(1L);
        // Дерево не переписано — иначе закрепление детали у живой сделки
        // оборвалось бы (docs/models/domain/aggregate/Strategy.md).
        assertThat(indicatorNodeIds()).isEqualTo(nodes);
        assertThat(rows.countWhere("inbox_events", "event_id", eventId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.2 — повторная активация двигает только статус")
    void aSecondActivationMovesTheStatusOnlyAndKeepsThePinnedDetail() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        activate(definition);
        Long detail = detailId();
        moveDefinition(STRATEGY_DEACTIVATED, definition, Strategy.Status.INACTIVE.name());
        Long deal = pinnedDeal(detail);

        activate(definition);

        assertThat(statusOf(DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());
        // Узел дерева тот же: закрепление детали у живой сделки цело.
        assertThat(rows.count("strategy_details")).isEqualTo(1L);
        assertThat(detailId()).isEqualTo(detail);
        assertThat(rows.row("deals", "id", deal).get("strategy_detail_id")).isEqualTo(detail);
    }

    @Test
    @DisplayName("B8.3 — деактивация и удаление двигают статус, строку не удаляя")
    void deactivationAndDeletionMoveTheStatusWithoutRemovingTheRow() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        activate(definition);
        Long detail = detailId();
        Long deal = pinnedDeal(detail);

        moveDefinition(STRATEGY_DEACTIVATED, definition, Strategy.Status.INACTIVE.name());
        moveDefinition(STRATEGY_DELETED, definition, Strategy.Status.DELETED.name());

        // Строка на месте: деталь нужна сделке до самого терминала, в том
        // числе на выходе, который удаление и запускает.
        assertThat(rows.count("strategies")).isEqualTo(1L);
        assertThat(rows.count("strategy_details")).isEqualTo(1L);
        assertThat(rows.row("deals", "id", deal).get("strategy_detail_id")).isEqualTo(detail);
    }

    @Test
    @DisplayName("B8.4 — событие о определении без копии — штатный исход")
    void anEventAboutAnAbsentCopyIsConsumedWithoutEffectAndDoesNotBlockTheNext() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy absent = Definitions.withDetail("S-absent", ACCOUNT, INSTRUMENT);
        Strategy other = Definitions.withDetail(SECOND_DEFINITION, ACCOUNT, INSTRUMENT);

        String absentEvent = moveDefinitionWithoutWaiting(STRATEGY_DELETED, absent);
        String activation = activate(other);

        // Первое отмечено обработанным без следствия: отказ на нём занял бы
        // партию и остановил применение следующих.
        assertThat(rows.countWhere("inbox_events", "event_id", absentEvent)).isEqualTo(1L);
        assertThat(rows.countWhere("strategies", "internal_id", "S-absent")).isZero();
        // Второе применено.
        assertThat(rows.countWhere("inbox_events", "event_id", activation)).isEqualTo(1L);
        assertThat(statusOf(SECOND_DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());
    }

    @Test
    @DisplayName("B8.5 — сообщение без конверта пропускается с записью")
    void aRecordWithoutEnvelopeHeadersIsSkippedWithATrailRow() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);

        // Без заголовка идентичности события.
        Wire.publishStrategyFact(strategyTopic(), Map.of("eventType", STRATEGY_ACTIVATED),
                Definitions.activated(definition));
        // Без заголовка класса события.
        Wire.publishStrategyFact(strategyTopic(), Map.of("eventId", "ev-headerless"),
                Definitions.activated(definition));
        barrier();

        assertThat(rows.count("strategies")).isZero();
        // Неполный конверт идёт тропой отравленной записи: на каждую запись —
        // строка следа с группой, темой, партицией и смещением; колонка
        // отсутствующего поля конверта пуста, первопричина называет его.
        List<Map<String, Object>> trail = rows.allOrderedBy(SKIPS, "record_offset");
        assertThat(trail).hasSize(2);
        trail.forEach(row -> {
            assertThat(row.get("consumer_group")).isEqualTo(SHARED_GROUP);
            assertThat(row.get("topic")).isEqualTo(strategyTopic());
            assertThat(row.get("record_partition")).isNotNull();
            assertThat(row.get("tenant_id")).isEqualTo(TENANT);
            assertThat(row.get("skipped_at")).isNotNull();
        });
        Map<String, Object> withoutIdentity = trail.getFirst();
        Map<String, Object> withoutClass = trail.getLast();
        assertThat(((Number) withoutClass.get("record_offset")).longValue())
                .isEqualTo(((Number) withoutIdentity.get("record_offset")).longValue() + 1L);
        assertThat(withoutIdentity.get("event_id")).isNull();
        assertThat(withoutIdentity.get("event_type")).isEqualTo(STRATEGY_ACTIVATED);
        assertThat(String.valueOf(withoutIdentity.get("cause")))
                .contains("PoisonStrategyFactException: Strategy fact without envelope headers eventId=null");
        assertThat(withoutClass.get("event_id")).isEqualTo("ev-headerless");
        assertThat(withoutClass.get("event_type")).isNull();
        assertThat(String.valueOf(withoutClass.get("cause")))
                .contains("Strategy fact without envelope headers eventId=ev-headerless eventType=null");
        // Отметки обработки у пропущенных нет, а обработка следующих записей
        // продолжается: барьер её и предъявляет.
        assertThat(rows.count("inbox_events")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.12 — заголовок конверта без значения — та же тропа отравленной записи, что и без заголовка")
    void aValuelessEnvelopeHeaderIsSkippedWithATrailRowLikeAMissingOne() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);

        // Заголовок идентичности события есть, значения у него нет.
        Wire.publishStrategyFact(strategyTopic(), Map.of("eventType", STRATEGY_ACTIVATED), "eventId",
                Definitions.activated(definition));
        // Заголовок класса события есть, значения у него нет.
        Wire.publishStrategyFact(strategyTopic(), Map.of("eventId", "ev-valueless-class"), "eventType",
                Definitions.activated(definition));
        // Барьер прошёл — обе записи обработку миновали, а не откладываются
        // без конца, останавливая партию.
        barrier();

        assertThat(rows.count("strategies")).isZero();
        // Заголовок без значения читается как отсутствующий: на каждую
        // запись — строка следа, колонка поля без значения пуста, а
        // первопричина — та же, что у записи без заголовка (B8.5).
        List<Map<String, Object>> trail = rows.allOrderedBy(SKIPS, "record_offset");
        assertThat(trail).hasSize(2);
        trail.forEach(row -> {
            assertThat(row.get("consumer_group")).isEqualTo(SHARED_GROUP);
            assertThat(row.get("topic")).isEqualTo(strategyTopic());
            assertThat(row.get("record_partition")).isNotNull();
            assertThat(row.get("tenant_id")).isEqualTo(TENANT);
        });
        Map<String, Object> valuelessIdentity = trail.getFirst();
        Map<String, Object> valuelessClass = trail.getLast();
        assertThat(valuelessIdentity.get("event_id")).isNull();
        assertThat(valuelessIdentity.get("event_type")).isEqualTo(STRATEGY_ACTIVATED);
        assertThat(String.valueOf(valuelessIdentity.get("cause")))
                .contains("PoisonStrategyFactException: Strategy fact without envelope headers eventId=null");
        assertThat(valuelessClass.get("event_id")).isEqualTo("ev-valueless-class");
        assertThat(valuelessClass.get("event_type")).isNull();
        assertThat(String.valueOf(valuelessClass.get("cause")))
                .contains("Strategy fact without envelope headers eventId=ev-valueless-class eventType=null");
        // Отметку несёт только барьер.
        assertThat(rows.count("inbox_events")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.6 — неизвестный класс события пропускается, а неразбираемое содержимое — нет")
    void anUnknownEventClassIsSkippedWhileAnUnreadablePayloadBreaksTheProcessing() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        activate(definition);
        Integer mark = AppLog.mark();

        Wire.publishStrategyFact(strategyTopic(), "ev-unknown-class", UNKNOWN_EVENT_TYPE,
                Definitions.activated(definition), TENANT);
        barrier();

        // Первая пропущена с записью: производитель вправе завести класс
        // раньше читателя.
        assertThat(AppLog.since(mark)).contains("Strategy fact of an unknown class is skipped eventType="
                + UNKNOWN_EVENT_TYPE);
        assertThat(rows.countWhere("inbox_events", "event_id", "ev-unknown-class")).isZero();
        // Неизвестный класс — не отравленная запись: следа пропуска у него нет.
        assertThat(rows.countWhere(SKIPS, "event_id", "ev-unknown-class")).isZero();

        Wire.publishStrategyFact(strategyTopic(), "ev-unreadable", STRATEGY_ACTIVATED,
                unreadableActivation(), TENANT);
        barrier();

        // Вторая РОНЯЕТ обработку классом отравленной записи и
        // пропускается со следом — строкой с координатами, идентичностью и
        // классом из конверта и первопричиной, а не молча; барьер за ней
        // прошёл, то есть приём не встал.
        assertThat(rows.countWhere(SKIPS, "event_id", "ev-unreadable")).isEqualTo(1L);
        Map<String, Object> skip = rows.row(SKIPS, "event_id", "ev-unreadable");
        assertThat(skip.get("consumer_group")).isEqualTo(SHARED_GROUP);
        assertThat(skip.get("topic")).isEqualTo(strategyTopic());
        assertThat(skip.get("record_partition")).isNotNull();
        assertThat(skip.get("record_offset")).isNotNull();
        assertThat(skip.get("event_type")).isEqualTo(STRATEGY_ACTIVATED);
        assertThat(skip.get("tenant_id")).isEqualTo(TENANT);
        // Первопричина несёт и звено отравленной записи, и глубинный отказ
        // разбора: обёртка каркаса слушателя в неё не входит.
        assertThat(String.valueOf(skip.get("cause")))
                .contains("PoisonStrategyFactException: Strategy fact payload is not readable as")
                .contains(" <- ")
                .doesNotContain("ListenerExecutionFailedException");
        assertThat(rows.countWhere("inbox_events", "event_id", "ev-unreadable")).isZero();
        assertThat(statusOf(DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());
    }

    @Test
    @DisplayName("B8.10 — отказ записи следа не продвигает смещение")
    void aRefusedSkipTrailKeepsThePoisonRecordInThePartition() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Integer mark = AppLog.mark();
        rows.refuse(SKIPS, "insert");
        try {
            Wire.publishStrategyFact(strategyTopic(), "ev-refused-trail", STRATEGY_ACTIVATED,
                    unreadableActivation(), TENANT);

            // Запись доставлена СНОВА и снова не легла: смещение не
            // продвинулось. Вторая доставка и есть наблюдение, а не пауза.
            Awaitility.await().atMost(TRAIL_RETRY_TIMEOUT).pollInterval(Duration.ofMillis(50))
                    .until(() -> occurrences(AppLog.since(mark), TRAIL_REFUSED + " topic=" + strategyTopic()) >= 2);
            assertThat(rows.count(SKIPS)).isZero();
            assertThat(AppLog.since(mark)).doesNotContain(SKIPPED);
        } finally {
            rows.allow(SKIPS, "insert");
        }

        // База вернулась — повтор доезжает сам, без повторной публикации:
        // след лёг ровно один, отметки у записи нет, приём продолжился.
        barrier();

        assertThat(rows.countWhere(SKIPS, "event_id", "ev-refused-trail")).isEqualTo(1L);
        assertThat(rows.countWhere("inbox_events", "event_id", "ev-refused-trail")).isZero();
    }

    @Test
    @DisplayName("B8.11 — повторная доставка пропущенной записи след не удваивает")
    void aRedeliveredPoisonRecordDoesNotDoubleItsTrail() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Wire.publishStrategyFact(strategyTopic(), "ev-first-poison", STRATEGY_ACTIVATED,
                unreadableActivation(), TENANT);
        barrier();
        Map<String, Object> first = rows.row(SKIPS, "event_id", "ev-first-poison");

        // Состояние после пропуска, чья фиксация смещения не состоялась: след
        // записи уже лежит, а сама запись доставляется снова. Ставится оно
        // прямой записью (Rows#put) — сорвать фиксацию смещения снаружи
        // ящика нечем. Координаты — те, на которые ляжет следующая запись
        // той же партиции: ключ тот же, между ними только барьер.
        Long redelivered = ((Number) first.get("record_offset")).longValue() + 2L;
        rows.put("""
                insert into reception_skips (consumer_group, topic, record_partition, record_offset,
                                             event_id, event_type, tenant_id, cause, skipped_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """, first.get("consumer_group"), first.get("topic"), first.get("record_partition"),
                redelivered, "ev-redelivered", STRATEGY_ACTIVATED, TENANT, EARLIER_SKIP);

        Wire.publishStrategyFact(strategyTopic(), "ev-redelivered", STRATEGY_ACTIVATED,
                unreadableActivation(), TENANT);
        // Барьер прошёл — конфликт по ключу координат поглощён, а не уронил
        // восстановление: иначе запись стояла бы в повторе.
        barrier();

        assertThat(rows.countWhere(SKIPS, "record_offset", redelivered)).isEqualTo(1L);
        assertThat(rows.countWhere(SKIPS, "event_id", "ev-redelivered")).isEqualTo(1L);
        // Строка та же, что лежала: вторая доставка её не переписала.
        assertThat(rows.row(SKIPS, "record_offset", redelivered).get("cause")).isEqualTo(EARLIER_SKIP);
        assertThat(rows.count(SKIPS)).isEqualTo(2L);
    }

    /** Сколько раз фраза встречается в тексте журнала. */
    private Integer occurrences(String text, String phrase) {
        int count = 0;
        int from = text.indexOf(phrase);
        while (from >= 0) {
            count++;
            from = text.indexOf(phrase, from + phrase.length());
        }
        return count;
    }

    @Test
    @DisplayName("B8.9 — активация, чьих проекций у ядра ещё нет, откладывается, а не теряется")
    void anActivationWithoutProjectionsIsDeferredUntilTheProjectionsArrive() {
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        Integer mark = AppLog.mark();

        // Проекций счёта и инструмента нет: тик синка ещё не отработал.
        Wire.publishStrategyFact(strategyTopic(), DEFERRED_EVENT, STRATEGY_ACTIVATED,
                Definitions.activated(definition), TENANT);
        Awaitility.await().atMost(DEFERRAL_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(() -> AppLog.since(mark).contains("Strategy fact application is deferred"));

        // Отложено, а не пропущено: ни копии, ни отметки — смещение стоит
        // на записи, и она повторяется.
        assertThat(AppLog.since(mark)).contains("ExchangeAccount not found: " + ACCOUNT);
        assertThat(AppLog.since(mark)).doesNotContain("Strategy fact is poison and is skipped");
        assertThat(rows.countWhere("strategies", "internal_id", DEFINITION)).isZero();
        assertThat(rows.countWhere("inbox_events", "event_id", DEFERRED_EVENT)).isZero();

        // Проекции пришли своим проходом — отложенное применение доезжает
        // само, без повторной публикации.
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        awaitStrategyStatus(DEFINITION, Strategy.Status.ACTIVE.name());

        assertThat(rows.countWhere("inbox_events", "event_id", DEFERRED_EVENT)).isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.8 — тропа приёма и тропа публикации поднимаются обе")
    void bothSidesOfTheEventPathRiseWithTheContext() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);

        // Подписка установлена: копия заведена ПРИНЯТЫМ сообщением, а не
        // нашей вставкой.
        activate(definition);
        assertThat(statusOf(DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());

        // Публикатор получил свой клиент: строка outbox доехала до темы.
        Wire.Mark mark = Wire.mark();
        freeze(ACCOUNT);
        tick(Tick.OUTBOX_RELAY);

        assertThat(Wire.publishedSince(mark)).isNotEmpty();
    }

    /** Статус копии определения. */
    private String statusOf(String internalId) {
        return String.valueOf(rows.row("strategies", "internal_id", internalId).get("status"));
    }

    /** Числовые ключи индикаторных объявлений копии в порядке заведения. */
    private List<Object> indicatorNodeIds() {
        return rows.all("strategy_indicator_settings").stream().map(row -> row.get("id")).toList();
    }

    /** Числовой ключ единственной детали копии. */
    private Long detailId() {
        return ((Number) rows.all("strategy_details").getFirst().get("id")).longValue();
    }

    /**
     * Кладёт событие жизненного цикла и НЕ ждёт следствия: клетка о
     * событии без копии утверждает ровно то, что следствия нет.
     */
    private String moveDefinitionWithoutWaiting(String eventType, Strategy definition) {
        String eventId = "ev-absent-" + definition.getInternalId();
        Wire.publishStrategyFact(strategyTopic(), eventId, eventType,
                Definitions.lifecycle(definition), TENANT);
        return eventId;
    }

    /**
     * Содержимое знакомого класса, которое в объявленную форму не
     * разбирается: снимок определения приехал строкой вместо дерева.
     */
    private String unreadableActivation() {
        return """
                {"strategyInternalId": "S1", "exchangeAccountInternalId": "A1",
                 "instrumentInternalId": "I1", "actor": "USER", "definition": "дерева нет"}
                """;
    }

    /**
     * Живая сделка, закрепившая деталь копии.
     *
     * <p><b>Прямая запись, и область у неё названа</b> ({@link Rows#put}):
     * тропы «сделка по объявлению» у ящика ещё нет — её строит группа
     * {@code B1}, — а предмет этой клетки есть ЦЕЛОСТНОСТЬ ссылки, не
     * заведение сделки.
     */
    private Long pinnedDeal(Long detail) {
        return rows.insert("""
                insert into deals (internal_id, exchange_account_id, instrument_id, strategy_detail_id,
                                   status, direction, entry_reason)
                values ('D1', ?, ?, ?, 'ACTIVE', 'LONG', 'STRATEGY') returning id
                """, accountId(ACCOUNT), instrumentId(INSTRUMENT), detail);
    }
}
