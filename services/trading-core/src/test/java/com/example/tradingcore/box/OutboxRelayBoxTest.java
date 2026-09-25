package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B9} — outbox и реле.
 *
 * <p><b>Строки outbox ставятся ТРОПОЙ ПОВЕРХНОСТИ, а не вставкой.</b>
 * Ручная постановка ступени счёта пишет две строки одной транзакцией —
 * отчёт о происшествии и факт подъёма, — и писателем у них остаётся тот
 * код, который пишет решение ({@link TradingCoreBox#freeze}). Вставленная
 * руками строка говорила бы о нашей вставке.
 *
 * <p><b>Выход реле лежит В ТЕМЕ, а его след — в базе:</b> запись читает
 * наблюдатель темы ({@link Wire}), отметку публикации — наблюдатель строк
 * ({@link Rows}). Поверхности у outbox нет вовсе, и оба ассерта прямые.
 *
 * <p><b>Отказ ЗАПИСИ ставится базой, а не подменой бина.</b> Требование
 * «решение и его событие ложатся одной транзакцией» проверяется только
 * тем, что одна из двух записей не проходит, а база лежит за границей
 * процесса — её отказ такой же вход, как отказ соседа
 * ({@link Rows#refuse}).
 *
 * <p><b>Клетка {@code B9.9} здесь не живёт.</b> Она требует прогона по
 * тропам ВСЕХ классов ядра — решения о заявке, открытия и закрытия сделки,
 * — то есть сборки живой сделки, чей контекст — ветвь
 * {@link SharedLiveDealBox}; её класс — {@link PublishedClassesBoxTest}.
 */
class OutboxRelayBoxTest extends SharedTradingCoreBox {

    /** Класс события подъёма ступени. */
    private static final String HOLD_RAISED = "HOLD_RAISED";

    /** Таблица строк outbox. */
    private static final String OUTBOX = "outbox_events";

    /**
     * Сколько строк пишет одна ручная постановка мягкой ступени счёта:
     * отчёт о происшествии и факт подъёма, одной транзакцией.
     */
    private static final Long ROWS_PER_HALT = 2L;

    @Test
    @DisplayName("B9.1 — решение и его событие ложатся одной транзакцией")
    void aDecisionAndItsEventAreWrittenInOneTransaction() {
        provisionAccounts(ACCOUNT);
        rows.refuse(OUTBOX, "insert");

        Answer refused;
        try {
            refused = freeze(ACCOUNT);
        } finally {
            rows.allow(OUTBOX, "insert");
        }

        // Ход не состоялся, и в базе НЕТ НИ ОДНОЙ из двух записей: решение
        // без факта и факт без решения одинаково недостижимы.
        assertThat(refused.status()).isNotEqualTo(204);
        assertThat(rows.count(OUTBOX)).isZero();
        assertThat(rows.count("anomaly_reports")).isZero();
        assertThat(rungOf(ACCOUNT)).isEqualTo("ACTIVE");

        // Следующий ход применяет ребро снова — ничего не потеряно.
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        assertThat(rows.count(OUTBOX)).isEqualTo(ROWS_PER_HALT);
        assertThat(rows.count("anomaly_reports")).isEqualTo(1L);
        assertThat(rungOf(ACCOUNT)).isEqualTo("HOLD");
    }

    @Test
    @DisplayName("B9.3 — ключ сообщения — тенант, конверт едет заголовками")
    void theMessageKeyIsTheTenantAndTheEnvelopeTravelsInHeaders() {
        provisionAccounts(ACCOUNT);
        Wire.Mark mark = Wire.mark();
        freeze(ACCOUNT);

        tick(Tick.OUTBOX_RELAY);

        List<Wire.Published> published = Wire.publishedSince(mark);
        assertThat(published).hasSize(2);
        Wire.Published raised = classOf(published, HOLD_RAISED);
        // Ключ — идентичность тенанта: ею держится порядок внутри тенанта.
        assertThat(raised.key()).isEqualTo(TENANT);
        // Конверт — заголовками, и все четыре его поля на месте.
        assertThat(raised.headers()).containsKeys("eventId", "eventType", "version", "occurredAt");
        // Пустой контекст трассировки заголовка НЕ занимает: его отсутствие
        // и означает «трассировки не было».
        assertThat(raised.headers()).doesNotContainKey("traceContext");
        // Телом — содержимое: идентичности радиуса читаются в нём.
        assertThat(new Answer(200, raised.payload()).asObject())
                .containsEntry("exchangeAccountInternalId", ACCOUNT);
        // Идентичность заголовка — та же, что у строки базы.
        assertThat(eventIdsOf(published)).containsExactlyInAnyOrderElementsOf(rowEventIds());
    }

    @Test
    @DisplayName("B9.4 — отметка публикации ставится после подтверждения брокером")
    void theRowIsMarkedOnlyAfterTheBrokerConfirmed() {
        provisionAccounts(ACCOUNT);
        Wire.Mark mark = Wire.mark();
        freeze(ACCOUNT);

        // До тика отметок нет ни у одной строки: реле начинается там, где
        // транзакция решения закончилась.
        assertThat(markedEventIds()).isEmpty();

        tick(Tick.OUTBOX_RELAY);

        // Отметка подтверждения не опережает: у КАЖДОЙ помеченной строки
        // есть своя запись в теме, и наоборот.
        assertThat(markedEventIds()).containsExactlyInAnyOrderElementsOf(
                eventIdsOf(Wire.publishedSince(mark)));
        assertThat(markedEventIds()).hasSize(2);
    }

    @Test
    @DisplayName("B9.6 — опубликованная, но непомеченная строка публикуется повторно")
    void aPublishedButUnmarkedRowGoesOutAgainWithTheSameIdentity() {
        provisionAccounts(ACCOUNT);
        Wire.Mark first = Wire.mark();
        freeze(ACCOUNT);
        Integer logMark = AppLog.mark();
        rows.refuse(OUTBOX, "update");

        try {
            tick(Tick.OUTBOX_RELAY);
        } finally {
            rows.allow(OUTBOX, "update");
        }

        // Публикация прошла, отметка упала — и проход этим не порван.
        List<Wire.Published> once = Wire.publishedSince(first);
        assertThat(once).hasSize(2);
        assertThat(markedEventIds()).isEmpty();
        assertThat(AppLog.since(logMark)).contains("Outbox row is published but not marked");

        Wire.Mark second = Wire.mark();
        tick(Tick.OUTBOX_RELAY);

        // Запись уходит второй раз с ТОЙ ЖЕ идентичностью события: дубль
        // снимает потребитель своей отметкой обработанного, а не реле.
        List<Wire.Published> twice = Wire.publishedSince(second);
        assertThat(eventIdsOf(twice)).containsExactlyInAnyOrderElementsOf(eventIdsOf(once));
        assertThat(markedEventIds()).hasSize(2);
    }

    @Test
    @DisplayName("B9.7 — повторная запись того же события дублем не становится")
    void aSecondRowWithTheSameEventIdentityDoesNotAppear() {
        provisionAccounts(ACCOUNT);
        freeze(ACCOUNT);
        String eventId = rowEventIds().getFirst();

        // Повторная запись той же идентичности: инвариант держит база.
        assertThatThrownBy(() -> rows.insert("""
                insert into outbox_events (event_id, tenant_id, event_type, version, occurred_at,
                                           topic, payload)
                values (?, ?, ?, 2, now(), 'trading-core.facts', '{}'::jsonb) returning id
                """, eventId, TENANT, HOLD_RAISED))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows.countWhere(OUTBOX, "event_id", eventId)).isEqualTo(1L);
        // Отказ тропу решения не уронил: ступень стои́т, и публикация идёт.
        assertThat(rungOf(ACCOUNT)).isEqualTo("HOLD");
        tick(Tick.OUTBOX_RELAY);
        assertThat(markedEventIds()).hasSize(2);
    }

    @Test
    @DisplayName("B9.8 — реле строк не удаляет и тем не заводит")
    void theRelayNeitherRemovesRowsNorAddsThem() {
        provisionAccounts(ACCOUNT, SECOND_ACCOUNT);
        freeze(ACCOUNT);
        freeze(SECOND_ACCOUNT);
        Long written = rows.count(OUTBOX);

        ticks(Tick.OUTBOX_RELAY, 3);

        // Помеченные строки остаются в базе: накопление ограничения сверху
        // не имеет, и это названное ограничение — гасит его чистка
        // опубликованных, а не реле.
        assertThat(rows.count(OUTBOX)).isEqualTo(written);
        assertThat(markedEventIds()).hasSize(written.intValue());
    }

    @Test
    @DisplayName("B9.10 — поглощённая реакция факта подъёма не производит")
    void anAbsorbedReactionProducesNoRaisedFact() {
        provisionAccounts(ACCOUNT);
        fullHalt(ACCOUNT);
        tick(Tick.OUTBOX_RELAY);
        assertThat(rungOf(ACCOUNT)).isEqualTo("TRADE_BLOCKED");

        Wire.Mark mark = Wire.mark();
        fullHalt(ACCOUNT);
        tick(Tick.OUTBOX_RELAY);

        // Второй строки подъёма в теме нет: событие лежит в транзакции
        // перестановки ступени, а перестановки не было.
        assertThat(Wire.publishedSince(mark))
                .noneMatch(record -> HOLD_RAISED.equals(record.eventType()));
    }

    /** Ступень лестницы, стоящая на счёте. */
    private String rungOf(String internalId) {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", internalId).get("safety_rung"));
    }

    /** Единственная запись названного класса среди опубликованных. */
    private Wire.Published classOf(List<Wire.Published> published, String eventType) {
        return published.stream()
                .filter(record -> eventType.equals(record.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Записи класса " + eventType + " в теме нет: "
                        + eventIdsOf(published)));
    }

    /** Идентичности событий опубликованных записей. */
    private List<String> eventIdsOf(List<Wire.Published> published) {
        return published.stream().map(Wire.Published::eventId).toList();
    }

    /** Идентичности событий всех строк outbox в порядке записи. */
    private List<String> rowEventIds() {
        return rows.all(OUTBOX).stream().map(row -> String.valueOf(row.get("event_id"))).toList();
    }

    /** Идентичности событий строк, у которых отметка публикации стои́т. */
    private List<String> markedEventIds() {
        return rows.select("""
                        select event_id from outbox_events where published_at is not null order by id asc
                        """).stream()
                .map(row -> String.valueOf(row.get("event_id")))
                .toList();
    }
}
