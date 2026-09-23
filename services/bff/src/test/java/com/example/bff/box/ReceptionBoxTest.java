package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B6} документа кейсов: приём события в запись потока
 * (.claude/tests/cases/bff.md §«B6 — Приём события в запись потока»).
 *
 * <p><b>Всякий пропуск предъявляется ВТОРОЙ, заведомо доезжающей
 * записью.</b> Пропущенное событие у клиента неотличимо от «событий не
 * было», и ожидание пропуска без барьера было бы зелено и у мёртвой
 * раздачи (.claude/tests/cases/bff.md §«Новая ось формы»). Барьер кладётся
 * в ту же тему следом: слушатель читает партицию по порядку, и доехавший
 * барьер означает, что позиция группы прошла и пропущенную запись.
 *
 * <p><b>Строка журнала опознаёт СВОЮ запись</b> — смещением либо классом,
 * которого нет у соседей: журнал общий у всех живых контекстов, и каждый
 * из них пишет свою строку об одной и той же записи ({@link AppLog}).
 *
 * <p>Клетки, чей предмет — старт реплики (позиция группы при подъёме,
 * перечень тем подписки), живут с поднимающими свою реплику
 * ({@link ReplicaStartBoxTest}); {@code B6.7} не прогоняется.
 */
class ReceptionBoxTest extends SharedBffBox {

    private static final String FACT = "DEAL_OPENED";

    /** Момент происшествия, назначенный клеткой: равенство не держится часами. */
    private static final String OCCURRED_AT = "2026-09-20T11:00:00Z";

    @Test
    @DisplayName("B6.1 — Конверт читается заголовками, тенант — ключом, содержимое — телом")
    void b6_1_theEnvelopeIsReadFromHeadersTheTenantFromTheKey() {
        String tenant = "TE1";
        String decoy = "TE1X";
        String trace = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String ownTicket = ticketOf(tenant);
        authAnswers(Bodies.memberships(decoy, ROLE));
        String decoyTicket = issuedTicketWith(identity.tokenFor(secondSubject));

        try (Subscription own = openedStreamOf(tenant, ownTicket, "e-b6-1-open");
             Subscription decoyStream = openedStreamOf(decoy, decoyTicket, "e-b6-1-decoy-open")) {
            Map<String, String> headers = envelope("e-b6-1", FACT, OCCURRED_AT);
            headers.put("version", "7");
            headers.put("traceContext", trace);
            // Тенант-приманка назван и заголовком, и телом: радиус обязан
            // взяться из КЛЮЧА записи.
            headers.put("tenantId", decoy);
            wire.publish(Wire.CORE_TOPIC, tenant, headers,
                    Bodies.dealOpened("D-e-b6-1").replaceFirst("\\{", "{\"tenantId\": \"" + decoy + "\", "));
            own.awaitFrames(2);
            publishDealOpened(decoy, "e-b6-1-decoy-barrier");
            decoyStream.awaitFrames(2);

            Subscription.Frame frame = own.frames().get(1);
            assertThat(frame.id()).isEqualTo("e-b6-1");
            assertThat(frame.type()).isEqualTo(FACT);
            assertThat(OffsetDateTime.parse(String.valueOf(frame.content().get("occurredAt"))).toInstant())
                    .isEqualTo(OffsetDateTime.parse(OCCURRED_AT).toInstant());
            assertThat(decoyStream.ids()).containsExactly("e-b6-1-decoy-open", "e-b6-1-decoy-barrier");
            // Служебные поля конверта наружу не уходят ни в одном поле.
            assertThat(frame.data()).doesNotContain("version", "traceContext", trace, decoy);
        }
    }

    @Test
    @DisplayName("B6.2 — Неполный конверт пропускается, а раздача продолжается")
    void b6_2_anIncompleteEnvelopeIsSkippedAndDeliveryGoesOn() {
        String tenant = "TE2";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b6-2-open")) {
            Integer mark = AppLog.mark();
            Long withoutKey = wire.publish(Wire.CORE_TOPIC, null,
                    envelope("e-b6-2-no-key", FACT, OCCURRED_AT), Bodies.dealOpened("D-no-key"));
            Map<String, String> noId = envelope(null, FACT, OCCURRED_AT);
            Long withoutId = wire.publish(Wire.CORE_TOPIC, tenant, noId, Bodies.dealOpened("D-no-id"));
            Map<String, String> noType = envelope("e-b6-2-no-type", null, OCCURRED_AT);
            Long withoutType = wire.publish(Wire.CORE_TOPIC, tenant, noType, Bodies.dealOpened("D-no-type"));
            publishDealOpened(tenant, "e-b6-2-full");
            stream.awaitFrames(2);

            // Четвёртая доехала: раздача не остановлена, позиция группы
            // прошла все четыре записи.
            assertThat(stream.ids()).containsExactly("e-b6-2-open", "e-b6-2-full");
            assertThat(stream.frames()).allSatisfy(frame ->
                    assertThat(frame.data()).doesNotContain("D-no-key", "D-no-id", "D-no-type"));
            String log = AppLog.since(mark);
            assertThat(List.of(withoutKey, withoutId, withoutType)).allSatisfy(offset ->
                    assertThat(log).contains("topic=" + Wire.CORE_TOPIC + " offset=" + offset + "\n"));
        }
    }

    @Test
    @DisplayName("B6.3 — Неизвестный класс события ведёт себя как пропуск, а не как отказ")
    void b6_3_anUnknownEventClassIsSkipped() {
        String tenant = "TE3";
        String unknown = "UNHEARD_OF_B6_3";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b6-3-open")) {
            Integer mark = AppLog.mark();
            wire.publish(Wire.CORE_TOPIC, tenant, "e-b6-3-unknown", unknown, OCCURRED_AT,
                    "{\"anything\": [1, 2, 3]}");
            publishDealOpened(tenant, "e-b6-3-known");
            stream.awaitFrames(2);

            assertThat(stream.ids()).containsExactly("e-b6-3-open", "e-b6-3-known");
            assertThat(stream.types()).doesNotContain(unknown);
            assertThat(AppLog.since(mark)).contains("eventType=" + unknown);
        }
    }

    @Test
    @DisplayName("B6.4 — Неразбираемое содержимое пропускается тем же исходом")
    void b6_4_anUnreadablePayloadIsSkippedTheSameWay() {
        String tenant = "TE4";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b6-4-open")) {
            // Первые компоненты разбираются, последний — нет: полупустая
            // форма клиенту не уходит.
            wire.publish(Wire.CORE_TOPIC, tenant, "e-b6-4-broken", FACT, OCCURRED_AT, """
                    {"dealInternalId": "D-partial", "direction": "LONG",
                     "entryMarketPhase": {"not": "a string"}}""");
            publishDealOpened(tenant, "e-b6-4-whole");
            stream.awaitFrames(2);

            assertThat(stream.ids()).containsExactly("e-b6-4-open", "e-b6-4-whole");
            assertThat(stream.frames()).allSatisfy(frame ->
                    assertThat(frame.data()).doesNotContain("D-partial"));
        }
    }

    @Test
    @DisplayName("B6.5 — Момента происшествия нет — периметр показывает момент раздачи")
    void b6_5_withoutAnOccurrenceMomentThePerimeterShowsTheDeliveryMoment() {
        String tenant = "TE5";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b6-5-open")) {
            OffsetDateTime before = OffsetDateTime.now();
            wire.publish(Wire.CORE_TOPIC, tenant, envelope("e-b6-5", FACT, null), Bodies.dealOpened("D-e-b6-5"));
            stream.awaitFrames(2);
            OffsetDateTime after = OffsetDateTime.now();

            Subscription.Frame frame = stream.frames().get(1);
            assertThat(frame.id()).isEqualTo("e-b6-5");
            assertThat(OffsetDateTime.parse(String.valueOf(frame.content().get("occurredAt"))).toInstant())
                    .isBetween(before.toInstant(), after.toInstant());
        }
    }

    @Test
    @Tag("debt")
    @DisplayName("B6.6 — Неразбираемый момент происшествия раздачу НЕ останавливает")
    void b6_6_anUnreadableOccurrenceMomentDoesNotStopDelivery() {
        String tenant = "TE6";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b6-6-open")) {
            Integer mark = AppLog.mark();
            wire.publish(Wire.CORE_TOPIC, tenant, "e-b6-6-bad", FACT, "not-a-moment",
                    Bodies.dealOpened("D-e-b6-6-bad"));
            publishDealOpened(tenant, "e-b6-6-good");
            stream.awaitFrames(2);

            assertThat(stream.ids()).containsExactly("e-b6-6-open", "e-b6-6-good");
            // Пропуск, а не отказ: слушатель не бросает, и повторных
            // доставок первой записи нет. Сегодня красно: разбор момента не
            // защищён, отказ уходит из слушателя наружу, и обработчик
            // повторяет доставку, пока не откажется от записи (находка F-2
            // документа кейсов).
            assertThat(AppLog.since(mark)).doesNotContain("ListenerExecutionFailedException");
        }
    }

    @Test
    @DisplayName("B6.10 — Приём ничего не пишет и ничего не публикует")
    void b6_10_receptionWritesNothingAndPublishesNothing() {
        String barrierTenant = "TE10B";
        String barrierTicket = ticketOf(barrierTenant);
        owners.forgetRequests();

        try (Subscription barrier = openedStreamOf(barrierTenant, barrierTicket, "e-b6-10-open")) {
            for (int index = 0; index < 10; index++) {
                String type = index % 2 == 0 ? FACT : "UNHEARD_OF_B6_10";
                wire.publish(Wire.CORE_TOPIC, "TE10-" + index, "e-b6-10-" + index, type, OCCURRED_AT,
                        Bodies.dealOpened("D-e-b6-10-" + index));
            }
            publishDealOpened(barrierTenant, "e-b6-10-barrier");
            barrier.awaitFrames(2);

            // Ни одного запроса к владельцам, ни одной публикации сверх
            // положенных клеткой двенадцати записей.
            assertThat(owners.count()).isZero();
            assertThat(recordsSinceStart()).isEqualTo(12L);
        }
    }

    /** Билет субъекта клетки, чьё единственное членство — названный тенант. */
    private String ticketOf(String tenant) {
        authAnswers(Bodies.memberships(tenant, ROLE));
        return issuedTicket();
    }

    /**
     * Конверт заголовками; пустое значение — заголовка нет вовсе.
     *
     * <p>Изменяемая карта: клетка добавляет к конверту заголовки сверх
     * обязательных трёх.
     */
    private static Map<String, String> envelope(String eventId, String eventType, String occurredAt) {
        Map<String, String> headers = new HashMap<>();
        headers.put(Wire.EVENT_ID, eventId);
        headers.put(Wire.EVENT_TYPE, eventType);
        headers.put(Wire.OCCURRED_AT, occurredAt);
        return headers;
    }
}
