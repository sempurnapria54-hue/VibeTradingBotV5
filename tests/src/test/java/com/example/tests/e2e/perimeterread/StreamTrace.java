package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.perimeterread.Subscription.Frame;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ходы и чтения потока периметра — общие группам {@code E3}-{@code E7}
 * (.claude/tests/cases/e2e-perimeter-read.md).
 *
 * <p><b>Факт без названного класса — удаление черновика определения у
 * владельца</b>: ход повторяем, идёт тем же проводом и копии у ядра не
 * двигает — удаление ни разу не активированного определения ядро только
 * отмечает прочитанным.
 */
final class StreamTrace {

    static final String CONTEXT = "/api/v1/bff/context";

    static final String TICKETS = "/api/v1/bff/stream-tickets";

    static final String JOURNAL = "/api/v1/audit/journal/records";

    static final String STRATEGY_DELETED = "STRATEGY_DELETED";

    private StreamTrace() {
    }

    /** Кэш членств прогрет выводом контекста, следы до хода кейса забыты. */
    static void warmTheCache(Trail trail, String token) {
        Answer context = trail.callWith(token, Party.BFF, "GET", CONTEXT, null, null);
        assertThat(context.status()).as("контекст для прогрева кэша — " + context.body()).isEqualTo(200);
        trail.forgetTraces();
    }

    /** Билет подписки, выданный браузерному токену. */
    static String ticket(Trail trail, String token) {
        Answer answer = trail.callWith(token, Party.BFF, "POST", TICKETS, null, "");
        assertThat(answer.status()).as("предусловие — билет выдан: " + answer.body()).isEqualTo(200);
        return Json.tree(answer.body()).path("ticket").asString();
    }

    /**
     * Факт производителя определений, опубликованный тиком реле.
     *
     * @return идентичность события удаления черновика
     */
    static String ownerFact(Trail trail) {
        String fact = ownerFactWritten(trail);
        trail.relayOwner();
        return fact;
    }

    /** То же без тика реле: строка outbox легла, в тему не ушла. */
    static String ownerFactWritten(Trail trail) {
        String draft = trail.createDefinition();
        Answer deleted = trail.moveDefinition(draft, "DELETED");
        assertThat(deleted.status()).as("удаление черновика — " + deleted.body()).isEqualTo(200);
        return String.valueOf(trail.database(Party.STRATEGIES).query(
                "select event_id from outbox_events where event_type = ? and payload ->> 'strategyInternalId' = ?",
                STRATEGY_DELETED, draft).getFirst().get("event_id"));
    }

    /** Строка журнала события — после ожидания приёма. */
    static Map<String, Object> awaitJournal(Trail trail, String eventId) {
        Database audit = trail.database(Party.AUDIT);
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return audit.query("select * from audit_records where event_id = ?", eventId).getFirst();
    }

    /** Момент записи потока. */
    static Instant momentOf(Frame frame) {
        return OffsetDateTime.parse(frame.record().path("occurredAt").asString()).toInstant();
    }

    /** Момент колонки базы — драйвер отдаёт его без смещения. */
    static Instant instantOf(Object column) {
        return column instanceof Timestamp moment ? moment.toInstant() : ((OffsetDateTime) column).toInstant();
    }

    /** Значение заголовка записи темы либо пусто. */
    static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return nonNull(header) ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
