package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Чтения следа сделки у сторон тропы — общие группам {@code E3}-{@code E8}
 * (.claude/tests/cases/e2e-strategy-to-deal.md).
 *
 * <p><b>След отбирается идентичностью события либо сделки, а не счётом
 * строк</b>: журнал, статистика и темы общие на тропу, и кейс, пришедший
 * после соседа, иначе читал бы чужой след как свой.
 */
final class DealTrace {

    static final String DEAL_OPENED = "DEAL_OPENED";

    static final String ORDER_DECIDED = "ORDER_DECIDED";

    static final String MARKET_TIME = "/api/v1/market/time";

    private DealTrace() {
    }

    /** Сделка поверхностью ядра — единственный её читатель вне ядра. */
    static JsonNode dealRead(Trail trail, String dealId) {
        Trail.Answer answer = trail.call(Party.TRADING_CORE, "GET", Trail.CORE + "/deals/" + dealId, Trail.TENANT,
                null);
        assertThat(answer.status()).as("чтение сделки " + dealId + " — " + answer.body()).isEqualTo(200);
        return Json.tree(answer.body());
    }

    /** Строка outbox ядра названного класса по сделке. */
    static Map<String, Object> coreOutbox(Trail trail, String dealId, String eventType) {
        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE).query(
                "select * from outbox_events where event_type = ? and payload ->> 'dealInternalId' = ?",
                eventType, dealId);
        assertThat(rows).as("строка outbox ядра класса " + eventType + " сделки " + dealId).hasSize(1);
        return rows.getFirst();
    }

    /** Записи темы ядра с названной идентичностью события. */
    static List<ConsumerRecord<String, String>> coreRecordsOf(Trail trail, String eventId) {
        return trail.records(Substrate.CORE_TOPIC).stream()
                .filter(record -> Objects.equals(eventId, eventIdOf(record)))
                .toList();
    }

    /** Строка журнала события — после ожидания приёма. */
    static Map<String, Object> awaitJournal(Trail trail, String eventId) {
        Database audit = trail.database(Party.AUDIT);
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return audit.query("select * from audit_records where event_id = ?", eventId).getFirst();
    }

    /** Факт происшествия события у статистики — после ожидания приёма. */
    static Map<String, Object> awaitIncident(Trail trail, String eventId) {
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("факт происшествия события " + eventId, () -> statistics
                .query("select event_id from incident_facts where event_id = ?", eventId).size() == 1);
        return statistics.query("select * from incident_facts where event_id = ?", eventId).getFirst();
    }

    /** Обращения к стабу «метод путь» без запроса — в порядке прихода. */
    static List<String> paths(List<LoggedRequest> requests) {
        return requests.stream()
                .map(request -> request.getMethod() + " " + request.getUrl().split("\\?")[0])
                .toList();
    }

    /** Выборка непуста. */
    static Boolean present(List<?> rows) {
        return isFalse(rows.isEmpty());
    }

    private static String eventIdOf(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("eventId");
        return nonNull(header) ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
