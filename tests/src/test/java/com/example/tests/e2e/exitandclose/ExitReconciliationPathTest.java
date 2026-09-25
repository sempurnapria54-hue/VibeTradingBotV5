package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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

import static com.example.tests.e2e.exitandclose.ExitTrail.bill;
import static com.example.tests.e2e.exitandclose.ExitTrail.closeRecord;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTerminalTranches;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E4} тропы выхода: сверка разбивки с записями закрытия
 * (.claude/tests/cases/e2e-exit-and-close.md §«E4 — Сверка разбивки с
 * записями закрытия»).
 *
 * <p><b>Режим допуска — конфигурация процесса ядра:</b> боевой ставится
 * подъёмом класса, разведочный — перед сделкой {@code E4.4}, и сторону
 * поднимает заново пролог этой сделки.
 *
 * <p><b>Время площадки на добыче движений — сутки после начала окна:</b>
 * окно сделки начинается моментом подтверждения первой входной ноги, и при
 * настоящем времени площадки оно глубже архива движений — разбивка тогда
 * «неполна по окну», а {@code E4.1} называет её предъявленной целиком.
 *
 * <p>{@code E4.5} читает сделку {@code E4.3} и потому идёт за ней;
 * {@code E4.2} кода не имеет — предусловие недостижимо у единственного
 * источника (документ кейсов, §«Кейсы, не прогоняемые сегодня»).
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E4 — Сверка разбивки с записями закрытия")
class ExitReconciliationPathTest {

    private static final String EXPLORATORY = "exchange-contour.exchanges.OKX.reconciliation-exploratory";

    private static final String MISMATCH = "PNL_RECONCILIATION_MISMATCH";

    private static final Long OPENED = 1758240000000L;

    private static final Long CLOSED = 1758240005000L;

    private static final Long REOPENED = 1758240100000L;

    private static final Long RECLOSED = 1758240200000L;

    private static final Long SOURCE_TIME = OPENED + Duration.ofDays(1).toMillis();

    private static final String LAST_BILL = "9003";

    private static Trail trail;

    private static String deal;

    private static Object dealId;

    private static Integer journalMark;

    private static Long dealFacts;

    private static Integer holdFacts;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("x4");
        trail.side(Party.TRADING_CORE).set(EXPLORATORY, "false");
        trail.renew(Party.TRADING_CORE);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E4.1 — Сошлось: признак сверки «сошлось», ступени нет")
    void e4_1_matchedMeansTheMatchedFeatureAndNoRung() {
        walkToTerminal("0.4");

        Database core = trail.database(Party.TRADING_CORE);
        Map<String, Object> row = dealRow();
        assertThat(row.get("reconciliation_status")).as("E4.1: признак сверки — «сошлось»").isEqualTo("MATCHED");
        assertThat(row.get("breakdown_incomplete")).as("E4.1: разбивка предъявлена целиком").isEqualTo("COMPLETE");
        assertThat(dealRead(trail, deal).path("status").asString()).as("E4.1: терминал штатный")
                .isEqualTo("CLOSED");
        assertThat(accountRung()).as("E4.1: ступени биржевого радиуса не поднято").isEqualTo("ACTIVE");
        assertThat(core.query("select code from anomaly_reports")).as("E4.1: отчёта о происшествии нет").isEmpty();
        assertThat(commands()).as("E4.1: новых команд площадке нет").isEmpty();
        assertThat(outbox("HOLD_RAISED")).as("E4.1: подъёма ступени ядро не публиковало").isEmpty();
        Map<String, Object> closed = single(outbox("DEAL_CLOSED"), "E4.1: терминал");
        awaitJournalRow(closed);
        assertThat(journalOf("HOLD_RAISED")).as("E4.1: строки подъёма ступени у журнала нет").isEmpty();
        awaitDealFact(closed);
        assertThat(holdFacts()).as("E4.1: счётчик поднятых ступеней не двигался — фактов подъёма нет")
                .isEqualTo(holdFacts);
    }

    @Test
    @Order(2)
    @DisplayName("E4.3 — Расхождение в боевом режиме: отчёт, мягкая ступень и оба события у потребителей")
    void e4_3_aCombatMismatchGivesAReportASoftRungAndBothEvents() {
        walkToTerminal("0.9");

        Database core = trail.database(Party.TRADING_CORE);
        Map<String, Object> row = dealRow();
        assertThat(row.get("reconciliation_status")).as("E4.3: признак сверки — «разошлось»")
                .isEqualTo("MISMATCHED");
        assertThat(dealRead(trail, deal).path("status").asString())
                .as("E4.3: терминал применён — финализация не заблокирована").isEqualTo("CLOSED");
        assertThat((BigDecimal) row.get("result_profit")).as("E4.3: число сделки суммой движений не подменено")
                .isEqualByComparingTo("1.2");
        assertThat(core.query("select code from anomaly_reports")).as("E4.3: отчёт с машинным кодом расхождения")
                .extracting(report -> report.get("code")).contains(MISMATCH);
        assertThat(accountRung()).as("E4.3: поднята мягкая ступень биржевого радиуса").isEqualTo("HOLD");
        Map<String, Object> hold = single(outbox("HOLD_RAISED"), "E4.3: подъём ступени");
        assertThat(hold.get("payload").toString()).as("E4.3: ступень — с тем же кодом").contains(MISMATCH);
        assertThat(commands()).as("E4.3: новых команд нет — ступень на площадку не ходит").isEmpty();
        Map<String, Object> closed = single(outbox("DEAL_CLOSED"), "E4.3: терминал");
        Map<String, Object> reported = outbox("ANOMALY_REPORTED").stream()
                .filter(event -> event.get("payload").toString().contains(MISMATCH))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("E4.3: события отчёта о расхождении нет"));
        for (Map<String, Object> event : List.of(closed, reported, hold)) {
            awaitJournalRow(event);
        }
        awaitDealFact(closed);
        Database statistics = trail.database(Party.STATISTICS);
        assertThat(statistics.count("deal_facts")).as("E4.3: сделочный факт один").isEqualTo(dealFacts + 1);
        Trail.await("E4.3: факты отчёта и подъёма приняты", () -> statistics.query(
                "select event_id from incident_facts where event_id in (?, ?)",
                String.valueOf(reported.get("event_id")), String.valueOf(hold.get("event_id"))).size() == 2);
        assertThat(statistics.query("select hold_rung from incident_facts where event_id = ?",
                        String.valueOf(hold.get("event_id"))).getFirst().get("hold_rung"))
                .as("E4.3: ступень мягкая — счётчик жёстких не двигается").isEqualTo("SOFT");
    }

    /**
     * Красна по находке {@code F9} документа: подъём ступени счёта пишет
     * строку точечным гардированным запросом, и момент её изменения не
     * записан ни в одной колонке — {@code modified_at} остаётся моментом
     * синка проекций (.claude/work/backlog.md §«Точечные запросы обновления
     * не двигают колонки аудита строки»). Ассерт момента строки стои́т
     * последним: порядок по событиям и по журналу прогон с меткой проверяет
     * до него.
     */
    @Test
    @Order(3)
    @Tag("debt")
    @DisplayName("E4.5 — Ступень поднимается после коммита терминала, а не до")
    void e4_5_theRungIsRaisedAfterTheTerminalCommitsNotBefore() {
        Map<String, Object> closed = single(outbox("DEAL_CLOSED"), "E4.5: терминал");
        Map<String, Object> hold = single(outbox("HOLD_RAISED"), "E4.5: подъём ступени");

        Instant terminal = instant(closed.get("occurred_at"));
        assertThat(instant(hold.get("occurred_at"))).as("E4.5: подъём ступени позже терминала").isAfter(terminal);
        Map<String, Object> closedRow = awaitJournalRow(closed);
        Map<String, Object> holdRow = awaitJournalRow(hold);
        assertThat(instant(holdRow.get("occurred_at"))).as("E4.5: у журнала терминал предшествует подъёму")
                .isAfter(instant(closedRow.get("occurred_at")));
        assertThat(trail.database(Party.STATISTICS).query("select event_id from deal_facts where event_id = ?",
                String.valueOf(closed.get("event_id")))).as("E4.5: сделочный факт заведён").hasSize(1);
        assertThat(trail.database(Party.TRADING_CORE).query("select id from deals where status not in "
                        + "('CLOSED', 'EMERGENCY_CLOSED')"))
                .as("E4.5: сделки в нетерминальном статусе после коммита нет").isEmpty();
        Object stood = trail.database(Party.TRADING_CORE).query("select modified_at from exchange_accounts "
                + "where safety_rung = 'HOLD'").getFirst().get("modified_at");
        assertThat(instant(stood)).as("E4.5: строка торгового состояния со ступенью — позже терминала")
                .isAfter(terminal);
    }

    @Test
    @Order(4)
    @DisplayName("E4.4 — Разведочный режим: отчёт есть, ступени нет, события подъёма нет")
    void e4_4_theExploratoryModeReportsWithoutARung() {
        trail.side(Party.TRADING_CORE).set(EXPLORATORY, "true");
        walkToTerminal("0.9");

        Database core = trail.database(Party.TRADING_CORE);
        assertThat(dealRow().get("reconciliation_status")).as("E4.4: признак сверки — «разошлось»")
                .isEqualTo("MISMATCHED");
        assertThat(core.query("select code from anomaly_reports")).as("E4.4: отчёт с тем же кодом заведён")
                .extracting(report -> report.get("code")).contains(MISMATCH);
        assertThat(accountRung()).as("E4.4: ступени не поднято ни одной").isEqualTo("ACTIVE");
        assertThat(outbox("HOLD_RAISED")).as("E4.4: строки outbox класса подъёма ступени нет").isEmpty();
        Map<String, Object> closed = single(outbox("DEAL_CLOSED"), "E4.4: терминал");
        Map<String, Object> reported = outbox("ANOMALY_REPORTED").stream()
                .filter(event -> event.get("payload").toString().contains(MISMATCH))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("E4.4: события отчёта о расхождении нет"));
        awaitJournalRow(reported);
        awaitJournalRow(closed);
        assertThat(journalOf("HOLD_RAISED")).as("E4.4: строки подъёма ступени у журнала нет").hasSize(journalMark);
        awaitDealFact(closed);
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E4.4: счётчик отчётов вырос — факт отчёта принят", () -> statistics
                .query("select event_id from incident_facts where event_id = ?",
                        String.valueOf(reported.get("event_id"))).size() == 1);
        assertThat(holdFacts()).as("E4.4: а поднятых ступеней — нет: фактов подъёма не прибавилось")
                .isEqualTo(holdFacts);
    }

    // ---------------------------------------------------------------- ходы и чтения

    /**
     * Сделка доведена до добытых движений, затем проходы — до терминала.
     * Записи закрытия — два эпизода: net 0,8 и 0,4 при результате без
     * издержек 1,0 и 0,5 и комиссии −0,2 и −0,1; движения — два торговых
     * слитной формы, и второе несёт названную сумму: 0,4 сводит сверку,
     * иное её разводит.
     *
     * @param secondAmount сумма второго торгового движения
     */
    private static void walkToTerminal(String secondAmount) {
        deal = walkToTerminalTranches(trail, String.join(", ",
                        closeRecord(OPENED, CLOSED, "1.0", "-0.2", "0.8"),
                        closeRecord(REOPENED, RECLOSED, "0.5", "-0.1", "0.4")),
                SOURCE_TIME, LAST_BILL, String.join(", ",
                        bill("9004", Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.8", "-0.2", CLOSED),
                        bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", secondAmount, "-0.1", RECLOSED)));
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        trail.passUntil("движения добыты", () -> nonNull(dealRow().get("bills_fetched_through")));
        trail.relayCore();
        journalMark = journalOf("HOLD_RAISED").size();
        dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        holdFacts = holdFacts();
        trail.forgetTraces();

        trail.passUntil("сделка ушла в терминал", () -> isFalse(Objects.equals("EXIT_PENDING",
                dealRead(trail, deal).path("status").asString())));
        trail.relayCore();
    }

    private static Map<String, Object> dealRow() {
        return trail.database(Party.TRADING_CORE).query("select reconciliation_status, breakdown_incomplete, "
                + "result_profit, bills_fetched_through from deals where id = ?", dealId).getFirst();
    }

    private static Integer holdFacts() {
        return trail.database(Party.STATISTICS).query("select event_id from incident_facts "
                + "where event_type = 'HOLD_RAISED'").size();
    }

    private static Object accountRung() {
        return trail.database(Party.TRADING_CORE).query("select safety_rung from exchange_accounts").getFirst()
                .get("safety_rung");
    }

    /** Команды площадке после последнего забывания: всё, что не чтение. */
    private static List<String> commands() {
        return trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(LoggedRequest::getUrl)
                .toList();
    }

    private static List<Map<String, Object>> outbox(String eventType) {
        return trail.database(Party.TRADING_CORE).query("select event_id, occurred_at, payload::text as payload "
                + "from outbox_events where event_type = ? order by id", eventType);
    }

    private static Map<String, Object> single(List<Map<String, Object>> rows, String label) {
        assertThat(rows).as(label + " — одна строка outbox").hasSize(1);
        return rows.getFirst();
    }

    private static List<Map<String, Object>> journalOf(String eventType) {
        return trail.database(Party.AUDIT).query("select event_id from audit_records where event_type = ?",
                eventType);
    }

    private static Map<String, Object> awaitJournalRow(Map<String, Object> event) {
        Database audit = trail.database(Party.AUDIT);
        String eventId = String.valueOf(event.get("event_id"));
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return audit.query("select event_type, occurred_at from audit_records where event_id = ?", eventId)
                .getFirst();
    }

    private static void awaitDealFact(Map<String, Object> closed) {
        Database statistics = trail.database(Party.STATISTICS);
        String eventId = String.valueOf(closed.get("event_id"));
        Trail.await("сделочный факт " + eventId, () -> statistics
                .query("select event_id from deal_facts where event_id = ?", eventId).size() == 1);
    }

    private static Instant instant(Object column) {
        return column instanceof java.sql.Timestamp moment ? moment.toInstant()
                : ((OffsetDateTime) column).toInstant();
    }
}
