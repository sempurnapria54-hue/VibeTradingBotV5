package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B7} документа кейсов в части штатного положения осей:
 * тик как вход, публикация как выход
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Выход реле лежит В ТЕМЕ, а его след — в базе.</b> Запись читает
 * наблюдатель темы ({@link Wire}), отметку публикации — наблюдатель строк
 * ({@link Rows}); поверхности у outbox нет вовсе, и оба ассерта прямые
 * (.claude/tests/cases/strategies.md §«Чем достаются выходы»).
 *
 * <p><b>Строки outbox ставятся ТРОПОЙ ЯЩИКА — переходом статуса.</b>
 * Писатель строки есть тот код, который пишет решение, и той же
 * транзакцией; вставленная руками строка говорила бы о нашей вставке, а
 * не о сервисе.
 *
 * <p><b>Клетки со СВОИМ положением осей здесь не живут.</b> Окно тика
 * ({@code B7.6}), выключатель реле ({@code B7.8}) и перекрытая тропа к
 * брокеру ({@code B7.4}, {@code B7.5}) — входы конфигурации, и каждый
 * платит своим подъёмом контекста: {@link RelayWindowBoxTest},
 * {@link DisabledRelayBoxTest}, {@link UnavailableBrokerBoxTest}.
 */
class OutboxRelayBoxTest extends SharedStrategiesBox {

    /** Колонка отметки публикации: её пустота и есть «не опубликовано». */
    private static final String PUBLISHED_AT = "published_at";

    /** Запись журнала об упавшей отметке опубликованной строки. */
    private static final String PUBLISHED_NOT_MARKED = "Outbox row is published but not marked";

    /**
     * На сколько секунд база затягивает отметку публикации в клетке о
     * перекрывающем запуске: окна должно хватить на второй вызов.
     */
    private static final Integer OVERLAP_DELAY_SECONDS = 10;

    /** Потолок ожидания записи в теме, когда отметка затянута базой. */
    private static final Duration DELAYED_WAIT = Duration.ofSeconds(30);

    @Test
    @DisplayName("B7.1 — Тик публикует неопубликованную строку и помечает её")
    void b7_1_theTickPublishesAnUnpublishedRowAndMarksIt() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        Wire.Mark mark = Wire.mark();
        assertThat(marked())
                .as("до тика отметок нет: реле начинается там, где транзакция решения кончилась")
                .isEmpty();

        Answer answer = relayPass();

        assertThat(answer.status()).as("фасад отвечает за запуск, а не за работу").isEqualTo(202);
        awaitPublished(1);
        assertThat(Wire.publishedSince(mark))
                .as("в теме ровно одна запись — та, что лежала строкой")
                .hasSize(1);
        assertThat(rows.count(OUTBOX_TABLE))
                .as("строк реле не удаляет: накопление гасит чистка опубликованных, а не оно")
                .isEqualTo(1L);
        assertThat(event(ACTIVATED).get(PUBLISHED_AT)).isNotNull();
        assertThat(statusOf(internalId, TENANT))
                .as("статусов сущностей реле не двигает")
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B7.2 — Ключ записи — тенант, конверт едет заголовками")
    void b7_2_theRecordKeyIsTheTenantAndTheEnvelopeTravelsInHeaders() {
        peerResolvesEverything();
        givenActive(TENANT);
        Wire.Mark mark = Wire.mark();
        Map<String, Object> row = event(ACTIVATED);

        relayPass();
        awaitPublished(1);

        Wire.Published published = Wire.publishedSince(mark).getFirst();
        assertThat(published.key())
                .as("ключ партиции — тенант: порядок событий тенанта держится ровно им")
                .isEqualTo(TENANT);
        assertThat(published.headers())
                .as("поля конверта едут заголовками по своим именам")
                .containsEntry("eventId", String.valueOf(row.get("event_id")))
                .containsEntry("eventType", ACTIVATED)
                .containsEntry("version", String.valueOf(row.get("version")))
                .containsEntry("occurredAt", String.valueOf(row.get("occurred_at")));
        assertThat(published.headers())
                .as("тенант заголовка не занимает: он ключ записи")
                .doesNotContainKeys("tenantId", "tenant_id", "tenant");
        assertThat(published.payload())
                .as("телом едет содержимое, а не конверт")
                .isEqualTo(contentTextOf(row));
    }

    /**
     * Порядок наблюдается ОТКАЗОМ второго хода, а не задержкой первого.
     *
     * <p>Пара «опубликовать, пометить» в одной транзакции не лежит и
     * лежать не может — брокер в транзакцию базы не входит, — и порядок
     * внутри пары виден ровно тогда, когда второй ход не проходит:
     * запись в теме есть, отметка пуста. Обратный порядок дал бы обратную
     * картину — помеченную строку без записи, — и она здесь недостижима.
     */
    @Test
    @DisplayName("B7.3 — Порядок «сперва опубликовать, потом пометить»")
    void b7_3_theRowIsPublishedFirstAndMarkedAfterwards() {
        peerResolvesEverything();
        givenActive(TENANT);
        Wire.Mark mark = Wire.mark();
        Integer logMark = AppLog.mark();
        rows.refuse(OUTBOX_TABLE, "update");

        try {
            relayPass();
            Awaitility.await()
                    .atMost(RELAY_WAIT)
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> AppLog.since(logMark).contains(PUBLISHED_NOT_MARKED));
        } finally {
            rows.allow(OUTBOX_TABLE, "update");
        }

        assertThat(Wire.publishedSince(mark))
                .as("публикация состоялась")
                .hasSize(1);
        assertThat(marked())
                .as("а отметка — нет: значит она идёт ПОСЛЕ, а не до")
                .isEmpty();
        assertThat(event(ACTIVATED).get(PUBLISHED_AT)).isNull();
    }

    @Test
    @DisplayName("B7.7 — Пустой проход выходов не производит")
    void b7_7_anEmptyPassProducesNoOutputs() {
        peerResolvesEverything();
        givenActive(TENANT);
        relayPass();
        awaitPublished(1);
        Object publishedAt = event(ACTIVATED).get(PUBLISHED_AT);
        Long written = rows.count(OUTBOX_TABLE);
        Wire.Mark mark = Wire.mark();

        Answer answer = relayPass();

        assertThat(answer.status()).isEqualTo(202);
        assertThat(Wire.publishedSinceOrNone(mark))
                .as("в тему не ушло ничего: неопубликованных строк не было")
                .isEmpty();
        assertThat(event(ACTIVATED).get(PUBLISHED_AT))
                .as("отметка не переписана: повтор сдвигал бы момент публикации назад")
                .isEqualTo(publishedAt);
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(written);
    }

    /**
     * Первая публикация здесь — ПРЕДУСЛОВИЕ наблюдателя, и довод у неё
     * свой: тему заводит публикация, а клетка ждёт появления записи в
     * теме как признака того, что проход уже внутри. Без заведённой темы
     * наблюдатель не отличил бы «записи ещё нет» от «темы ещё нет».
     *
     * <p><b>Окно перекрытия ставит БАЗА, а не тест.</b> Отметка
     * публикации затягивается на заданное число секунд; второй вызов
     * приходит, когда первый проход уже опубликовал и ещё не пометил.
     * Пройди он — строка ушла бы в тему ВТОРОЙ раз, потому что отметка
     * первого прохода ещё не закоммичена; ровно это число записей клетка
     * и мерит.
     */
    @Test
    @DisplayName("B7.9 — Перекрывающий запуск гасится молча")
    void b7_9_anOverlappingRunIsSkippedSilently() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        relayPass();
        awaitPublished(1);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        Wire.Mark mark = Wire.mark();
        rows.delay(OUTBOX_TABLE, "update", OVERLAP_DELAY_SECONDS);

        Answer first;
        Answer second;
        try {
            first = tickRelay();
            Awaitility.await()
                    .atMost(DELAYED_WAIT)
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> Wire.publishedSince(mark).size() == 1);
            second = tickRelay();
            awaitPublished(2);
        } finally {
            rows.undelay(OUTBOX_TABLE, "update");
        }

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status())
                .as("ни на один вызов не приходит 409: ответ уходит раньше, чем замок берётся")
                .isEqualTo(202);
        assertThat(Wire.publishedSince(mark))
                .as("строка опубликована ОДИН раз: перекрывающий проход пропущен")
                .hasSize(1);
        assertThat(marked()).hasSize(2);
    }

    @Test
    @DisplayName("B7.10 — Тик реле актора не пишет и событий не заводит")
    void b7_10_theRelayNeitherRewritesTheActorNorAddsEvents() {
        peerResolvesEverything();
        givenActive(TENANT);
        String content = contentTextOf(event(ACTIVATED));
        Long written = rows.count(OUTBOX_TABLE);

        relayPass();
        awaitPublished(1);
        relayPass();

        assertThat(contentTextOf(event(ACTIVATED)))
                .as("содержимое записано писателем решения, и реле его не трогает")
                .isEqualTo(content);
        assertThat(contentOf(event(ACTIVATED)))
                .containsEntry("actor", IdentityStub.PRINCIPAL);
        assertThat(rows.count(OUTBOX_TABLE))
                .as("своих строк реле не заводит ни одной")
                .isEqualTo(written);
        assertThat(eventTypes())
                .as("классов сверх записанного переходом не появилось")
                .containsExactly(ACTIVATED);
    }

    /** Классы событий всех строк outbox в порядке записи. */
    private List<String> eventTypes() {
        return events().stream()
                .map(row -> String.valueOf(row.get("event_type")))
                .toList();
    }
}
