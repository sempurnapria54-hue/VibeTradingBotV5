package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.exitandclose.ExitTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.exitandclose.ExitTrail.HOLD_RAISED;
import static com.example.tests.e2e.exitandclose.ExitTrail.coreOutbox;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.filledSize;
import static com.example.tests.e2e.exitandclose.ExitTrail.journalOf;
import static com.example.tests.e2e.exitandclose.ExitTrail.passUntilLeaves;
import static com.example.tests.e2e.exitandclose.ExitTrail.raiseHalt;
import static com.example.tests.e2e.exitandclose.ExitTrail.standAtExposure;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToFilledEntry;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E1} тропы выхода: экспозиция в зеркале и инициатива выхода
 * (.claude/tests/cases/e2e-exit-and-close.md §«E1 — Экспозиция в зеркале и
 * инициатива выхода»).
 *
 * <p><b>Ветви одного состояния разводятся свежим развёртыванием ядра:</b>
 * {@code E1.2}, {@code E1.3} и {@code E1.5} стоят на состоянии {@code E1.1}, и
 * каждая уводит сделку необратимо, поэтому {@code E1.3} и {@code E1.5}
 * проходят пролог и {@code E1.1} заново на ядре, поднятом на пустой базе;
 * {@code E1.4} продолжает сделку {@code E1.3}.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E1 — Экспозиция в зеркале и инициатива выхода")
class ExitInitiativePathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private static Trail trail;

    private static String deal;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("x1");
        trail.factSeriesStartedYesterday();
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E1.1 — Наблюдение позиции у стаба даёт зеркалу эпизод, а траншу — экспозицию")
    void e1_1_theObservedPositionGivesTheMirrorAnEpisodeAndTheTrancheExposure() {
        deal = walkToFilledEntry(trail);
        Database core = trail.database(Party.TRADING_CORE);
        assertThat(core.count("positions")).as("предусловие E1.1: экспозиции в зеркале нет").isZero();
        Long outbox = core.count("outbox_events");
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");
        String size = filledSize(trail);
        trail.forgetTraces();

        standAtExposure(trail, deal);
        trail.relayCore();

        List<LoggedRequest> reads = trail.exchange().requests(Trail.EXCHANGE_POSITIONS);
        assertThat(reads).as("E1.1: к стабу площадки ушло чтение позиции инструмента").isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.getMethod().getName()).isEqualTo("GET");
                    assertThat(request.queryParameter("instId").firstValue()).isEqualTo(Trail.EXTERNAL_INSTRUMENT);
                    assertThat(request.getHeader("OK-ACCESS-KEY")).as("E1.1: ключ счёта коннектор взял сам")
                            .isNotBlank();
                });
        assertThat(trail.accesses(Party.CONNECTOR)).as("E1.1: чтение позиции ушло с идентификатором счёта")
                .extracting(Side.Access::path)
                .contains("/api/v1/accounts/" + trail.account() + "/positions/instrument");
        List<Map<String, Object>> episodes = core.query(
                "select status, direction, external_size from positions");
        assertThat(episodes).as("E1.1: в зеркале появился один эпизод позиции").hasSize(1);
        assertThat(episodes.getFirst().get("status")).as("E1.1: эпизод живой").isEqualTo("ACTIVE");
        assertThat(plain(episodes.getFirst().get("external_size"))).as("E1.1: размер — из ответа стаба")
                .isEqualTo(size);
        assertThat(episodes.getFirst().get("direction")).as("E1.1: сторона — из ответа стаба").isNotNull();
        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("status").asString()).as("E1.1: сделка активна").isEqualTo("ACTIVE");
        assertThat(plain(read.path("tranches").get(0).path("exposure").decimalValue()))
                .as("E1.1: экспозиция транша равна налившемуся объёму входной ноги").isEqualTo(size);
        assertThat(core.count("outbox_events")).as("E1.1: наблюдение факта класса события не имеет")
                .isEqualTo(outbox);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E1.1: у журнала следа нет")
                .isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E1.1: у статистики следа нет")
                .isEqualTo(facts);
        assertThat(trail.accesses(Party.STRATEGIES)).as("E1.1: к владельцу определений обращений нет").isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("E1.2 — Шаг EXIT уровня сделки уводит в координированный выход, и события остановки на этом ребре нет")
    void e1_2_theDealLevelExitStepLeadsToCoordinatedExitWithoutAShutdownEvent() {
        Integer journal = journalOf(trail, deal).size();
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");
        trail.marketPhaseIs("BEAR_TREND");

        passUntilLeaves(trail, deal, "ACTIVE");
        List<String> commands = commands();
        trail.relayCore();

        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("status").asString()).as("E1.2: сделка — в координированном выходе")
                .isEqualTo("EXIT_PENDING");
        assertThat(read.path("shutdownReason").isNull() || read.path("shutdownReason").isMissingNode())
                .as("E1.2: причина выхода пуста — ребро шага EXIT её не присваивает").isTrue();
        assertThat(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal))
                .as("E1.2: строки outbox класса остановки сделки нет").isEmpty();
        assertThat(journalOf(trail, deal)).as("E1.2: новых строк журнала о сделке нет").hasSize(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E1.2: фактов у статистики нет")
                .isEqualTo(facts);
        assertThat(commands).as("E1.2: на проходе смены статуса команд площадке ещё нет").isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("E1.3 — Удаление определения у владельца уводит сделку в выход с причиной, и событие остановки доезжает до журнала")
    void e1_3_deletingTheDefinitionLeadsToExitWithAReasonAndTheEventReachesTheJournal() {
        trail.marketFavoursEntry();
        deal = walkToFilledEntry(trail);
        standAtExposure(trail, deal);
        String definition = String.valueOf(trail.database(Party.STRATEGIES)
                .query("select internal_id from strategies where status = 'ACTIVE'").getFirst().get("internal_id"));
        Long dealFacts = trail.database(Party.STATISTICS).count("deal_facts");

        Answer deleted = trail.moveDefinition(definition, "DELETED");
        trail.relayOwner();
        Trail.await("E1.3: копия определения у ядра удалена", () -> isFalse(trail.database(Party.TRADING_CORE)
                .query("select id from strategies where internal_id = ? and status = 'DELETED'", definition)
                .isEmpty()));
        passUntilLeaves(trail, deal, "ACTIVE");
        List<String> commands = commands();
        trail.relayCore();

        assertThat(deleted.status()).as("E1.3: удаление принято владельцем — " + deleted.body()).isEqualTo(200);
        assertThat(trail.database(Party.STRATEGIES).query("select status from strategies where internal_id = ?",
                definition).getFirst().get("status")).as("E1.3: определение удалено").isEqualTo("DELETED");
        assertThat(trail.database(Party.STRATEGIES).query("select published_at from outbox_events "
                        + "where event_type = 'STRATEGY_DELETED' and payload ->> 'strategyInternalId' = ?", definition))
                .as("E1.3: строка outbox удаления опубликована").singleElement()
                .satisfies(row -> assertThat(row.get("published_at")).isNotNull());
        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("status").asString()).as("E1.3: сделка — в координированном выходе")
                .isEqualTo("EXIT_PENDING");
        assertThat(read.path("shutdownReason").asString()).as("E1.3: причина — стратегия удалена")
                .isEqualTo("STRATEGY_DELETED");
        List<Map<String, Object>> shutdown = coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal);
        assertThat(shutdown).as("E1.3: строка outbox остановки сделки одна").hasSize(1);
        JsonNode content = Json.tree(String.valueOf(shutdown.getFirst().get("payload")));
        assertThat(content.path("dealInternalId").asString()).isEqualTo(deal);
        assertThat(content.path("exchangeAccountInternalId").asString()).as("E1.3: в содержимом — счёт")
                .isEqualTo(trail.account());
        assertThat(content.path("instrumentInternalId").asString()).as("E1.3: инструмент")
                .isEqualTo(Trail.INSTRUMENT);
        assertThat(content.path("strategyInternalId").asString()).as("E1.3: определение").isEqualTo(definition);
        assertThat(content.toString()).as("E1.3: и причина").contains("STRATEGY_DELETED");
        String event = String.valueOf(shutdown.getFirst().get("event_id"));
        Map<String, Object> row = awaitJournalRow(event);
        assertThat(row.get("content").toString()).as("E1.3: строка журнала с той же причиной")
                .contains("STRATEGY_DELETED");
        assertThat(instant(row.get("occurred_at"))).as("E1.3: и тем же моментом происшествия")
                .isEqualTo(instant(shutdown.getFirst().get("occurred_at")));
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E1.3: сделочного факта нет")
                .isEqualTo(dealFacts);
        assertThat(trail.database(Party.STATISTICS).query("select event_id from incident_facts where event_id = ?",
                event)).as("E1.3: факта происшествия нет — класс остановки не несом").isEmpty();
        assertThat(commands).as("E1.3: команд площадке на проходе смены статуса нет").isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("E1.4 — Перезапись причины на ребре в ошибочное состояние даёт вторую строку журнала о той же сделке")
    void e1_4_rewritingTheReasonOnTheErrorEdgeGivesASecondJournalRow() {
        Long dealFacts = trail.database(Party.STATISTICS).count("deal_facts");

        Answer raised = raiseHalt(trail, "FULL");
        Trail.await("E1.4: жёсткая ступень счёта поднята", () -> isFalse(trail.database(Party.TRADING_CORE)
                .query("select event_id from outbox_events where event_type = ?", HOLD_RAISED).isEmpty()));
        trail.orchestrate();
        trail.relayCore();

        assertThat(raised.status()).as("E1.4: подъём жёсткой ступени принят — " + raised.body()).isEqualTo(202);
        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("status").asString()).as("E1.4: сделка — в ошибочном состоянии").isEqualTo("ERROR");
        assertThat(read.path("shutdownReason").asString()).as("E1.4: причина переписана на биржевую")
                .isEqualTo("EXCHANGE_HOLD");
        List<Map<String, Object>> shutdown = coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal);
        assertThat(shutdown).as("E1.4: строк outbox остановки по сделке две").hasSize(2);
        assertThat(shutdown.get(0).get("payload").toString()).contains("STRATEGY_DELETED");
        assertThat(shutdown.get(1).get("payload").toString()).as("E1.4: и причины у них разные")
                .contains("EXCHANGE_HOLD");
        for (Map<String, Object> event : shutdown) {
            awaitJournalRow(String.valueOf(event.get("event_id")));
        }
        List<Map<String, Object>> journal = journalOf(trail, deal).stream()
                .filter(row -> Objects.equals(DEAL_SHUTDOWN_INITIATED, row.get("event_type")))
                .toList();
        assertThat(journal).as("E1.4: две строки журнала об одной сделке").hasSize(2);
        assertThat(journal.get(0).get("occurred_at")).as("E1.4: каждая со своим моментом")
                .isNotEqualTo(journal.get(1).get("occurred_at"));
        assertThat(trail.database(Party.STATISTICS).query("select event_id from incident_facts where event_id in "
                        + "(?, ?)", shutdown.get(0).get("event_id"), shutdown.get(1).get("event_id")))
                .as("E1.4: счётчиков по классу остановки нет").isEmpty();
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E1.4: число сделок им не двигается")
                .isEqualTo(dealFacts);
    }

    @Test
    @Order(5)
    @DisplayName("E1.5 — Мягкая ступень счёта живой сделки не трогает")
    void e1_5_aSoftAccountRungLeavesTheLiveDealAlone() {
        deal = walkToFilledEntry(trail);
        standAtExposure(trail, deal);
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Map<String, Object> before = awaitIncidentRow(null);
        trail.forgetTraces();

        Answer raised = raiseHalt(trail, "FREEZE");
        trail.orchestrate();
        trail.relayCore();

        assertThat(raised.status()).as("E1.5: мягкая ступень применена — " + raised.body()).isEqualTo(204);
        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("status").asString()).as("E1.5: сделка активна").isEqualTo("ACTIVE");
        assertThat(read.path("shutdownReason").isNull() || read.path("shutdownReason").isMissingNode())
                .as("E1.5: причина выхода пуста").isTrue();
        assertThat(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal)).as("E1.5: строки outbox остановки нет")
                .isEmpty();
        assertThat(commands()).as("E1.5: ни одной команды площадке").isEmpty();
        String hold = String.valueOf(trail.database(Party.TRADING_CORE)
                .query("select event_id from outbox_events where event_type = ?", HOLD_RAISED).getFirst()
                .get("event_id"));
        Map<String, Object> row = awaitJournalRow(hold);
        assertThat(row.get("deal_internal_id")).as("E1.5: строка журнала — о ступени, не о сделке").isNull();
        Integer raisedBefore = ((Number) before.get("raised_holds")).intValue();
        Trail.await("E1.5: поднятых ступеней на одну больше", () -> ((Number) awaitIncidentRow(null)
                .get("raised_holds")).intValue() == raisedBefore + 1);
        Map<String, Object> after = awaitIncidentRow(null);
        trail.statisticsRecomputes(NEVER);
        assertThat(after.get("hard_raised_holds")).as("E1.5: жёстких — столько же")
                .isEqualTo(before.get("hard_raised_holds"));
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Команды площадке после последнего забывания: всё, что не чтение. */
    private static List<String> commands() {
        return trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(LoggedRequest::getUrl)
                .toList();
    }

    private static Map<String, Object> awaitJournalRow(String eventId) {
        Database audit = trail.database(Party.AUDIT);
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return audit.query("select * from audit_records where event_id = ?", eventId).getFirst();
    }

    /** Строка суток тенанта тропы, сложенная тактом позже названного момента сборки. */
    private static Map<String, Object> awaitIncidentRow(Object assembledBefore) {
        Database statistics = trail.database(Party.STATISTICS);
        String sql = "select * from incident_aggregates where tenant_id = ? and bucket_date = ?";
        Trail.await("такт пересчёта сложил сутки", () -> {
            List<Map<String, Object>> rows = statistics.query(sql, trail.tenant(), LocalDate.now(ZoneOffset.UTC));
            return isFalse(rows.isEmpty())
                    && isFalse(Objects.equals(assembledBefore, rows.getFirst().get("assembled_at")));
        });
        return statistics.query(sql, trail.tenant(), LocalDate.now(ZoneOffset.UTC)).getFirst();
    }

    private static Object instant(Object column) {
        return column instanceof java.sql.Timestamp moment ? moment.toInstant()
                : ((java.time.OffsetDateTime) column).toInstant();
    }

    private static String plain(Object number) {
        return ((BigDecimal) number).stripTrailingZeros().toPlainString();
    }
}
