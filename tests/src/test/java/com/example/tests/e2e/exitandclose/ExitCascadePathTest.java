package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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

import static com.example.tests.e2e.exitandclose.ExitTrail.CANCELED;
import static com.example.tests.e2e.exitandclose.ExitTrail.CANCEL_ALGOS;
import static com.example.tests.e2e.exitandclose.ExitTrail.CANCEL_ORDER;
import static com.example.tests.e2e.exitandclose.ExitTrail.CLOSE_POSITION;
import static com.example.tests.e2e.exitandclose.ExitTrail.EXTERNAL_OCO;
import static com.example.tests.e2e.exitandclose.ExitTrail.SECOND_ORDER;
import static com.example.tests.e2e.exitandclose.ExitTrail.coreOutbox;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeAcceptsTeardown;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeHoldsSecondLeg;
import static com.example.tests.e2e.exitandclose.ExitTrail.journalOf;
import static com.example.tests.e2e.exitandclose.ExitTrail.passUntilLeaves;
import static com.example.tests.e2e.exitandclose.ExitTrail.plain;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToScaledIn;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTwoTranches;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E2} тропы выхода: каскад в транши и порядок снятия у площадки
 * (.claude/tests/cases/e2e-exit-and-close.md §«E2 — Каскад в транши и порядок
 * снятия у площадки»).
 *
 * <p><b>Четыре клетки одного транша идут одной сделкой, от отказа к снятию:</b>
 * {@code E2.4} — площадка отказывает в отмене второй входной ноги,
 * {@code E2.2} — отмена принята, а нога читается живой, {@code E2.1} и
 * {@code E2.5} — нога читается снятой, и выход доходит до терминальности
 * транша. Предусловие у всех четырёх одно — состояние {@code E1.2} с живой
 * второй ногой добора, — а отказ и неподтверждённая отмена порядка звеньев
 * не меняют: отмена, принятая на {@code E2.2}, и есть отмена, чей момент
 * {@code E2.1} сравнивает с закрытием. Журнал стаба поэтому с {@code E2.2} до
 * {@code E2.5} не забывается.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2 — Каскад в транши и порядок снятия у площадки")
class ExitCascadePathTest {

    private static final String ANCHORLESS = "ANCHORLESS_COMMAND_REFUSED";

    private static final String REFRESH = "REFRESH_DEAL_CONTEXT_ACTION";

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static Trail trail;

    private static String deal;

    private static String size;

    private static Long actionMark;

    private static Integer journalMark;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("x2");
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
    @DisplayName("E2.4 — Отказ площадки на отмене: закрытия не уходит, звено повторяется")
    void e2_4_aRefusedCancelSendsNoCloseAndTheLinkRepeats() {
        deal = walkToScaledIn(trail);
        size = plain(trail.database(Party.TRADING_CORE)
                .query("select size from orders where external_id = ?", SECOND_ORDER).getFirst().get("size"));
        trail.marketPhaseIs("BEAR_TREND");
        passUntilLeaves(trail, deal, "ACTIVE");
        trail.relayCore();
        journalMark = journalOf(trail, deal).size();
        actionMark = actionMark();
        Long strategyRows = trail.database(Party.TRADING_CORE).count("deal_strategy_action_states");
        trail.exchange().answersPost(CANCEL_ORDER, """
                {"code": "51400", "msg": "Order cancellation failed as the order has been filled, canceled or does not exist",
                  "data": []}
                """);
        trail.forgetTraces();

        trail.passUntil("E2.4: отмена отвергнута дважды", () -> trail.exchange().requests(CANCEL_ORDER).size() >= 2);
        trail.orchestrate();
        trail.relayCore();

        assertThat(trail.accesses(Party.CONNECTOR)).as("E2.4: отказ вернулся ядру классом отказа границы")
                .filteredOn(access -> access.path().endsWith("/orders/cancellations"))
                .isNotEmpty()
                .allSatisfy(access -> assertThat(access.status()).isEqualTo(502));
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E2.4: запроса закрытия позиции у стаба нет")
                .isEmpty();
        assertThat(dealRead(trail, deal).path("status").asString())
                .as("E2.4: сделка осталась в координированном выходе").isEqualTo("EXIT_PENDING");
        assertThat(trail.database(Party.TRADING_CORE).count("deal_strategy_action_states"))
                .as("E2.4: строки исполнения по отмене нет").isEqualTo(strategyRows);
        assertThat(systemActionsSince(actionMark)).as("E2.4: учёта отказа нет — системные строки только добычи")
                .allSatisfy(type -> assertThat(type).isEqualTo(REFRESH));
        List<Map<String, Object>> reports = trail.database(Party.TRADING_CORE)
                .query("select internal_id from anomaly_reports where code = ?", ANCHORLESS);
        assertThat(reports).as("E2.4: отчёт об отказе без анкера один, сколько бы отказ ни повторялся")
                .hasSize(1);
        List<Map<String, Object>> reported = trail.database(Party.TRADING_CORE).query(
                "select event_id from outbox_events where event_type = 'ANOMALY_REPORTED'");
        assertThat(reported).as("E2.4: событие отчёта одно").hasSize(1);
        String event = String.valueOf(reported.getFirst().get("event_id"));
        Database audit = trail.database(Party.AUDIT);
        Trail.await("E2.4: строка журнала отчёта", () -> audit
                .query("select id from audit_records where event_id = ?", event).size() == 1);
        assertThat(journalOf(trail, deal)).as("E2.4: строки терминала у журнала нет")
                .noneSatisfy(row -> assertThat(row.get("event_type")).isEqualTo("DEAL_CLOSED"));
    }

    @Test
    @Order(2)
    @DisplayName("E2.2 — Пока отмена не подтверждена, закрытия нетто-экспозиции у стаба нет")
    void e2_2_whileTheCancelIsUnconfirmedNoNetCloseReachesTheStub() {
        Long strategyRows = trail.database(Party.TRADING_CORE).count("deal_strategy_action_states");
        exchangeAcceptsTeardown(trail);
        trail.forgetTraces();

        trail.passUntil("E2.2: отмена принята", () -> nonNull(secondLeg().get("close_reason")));
        trail.orchestrate();
        trail.orchestrate();
        trail.relayCore();

        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E2.2: запроса закрытия позиции у стаба нет")
                .isEmpty();
        assertThat(trail.exchange().requests(CANCEL_ORDER)).as("E2.2: принятая отмена не повторяется")
                .hasSize(1);
        List<LoggedRequest> reads = trail.exchange().requests(Trail.EXCHANGE_ORDER).stream()
                .filter(request -> Objects.equals("GET", request.getMethod().getName()))
                .filter(request -> Objects.equals(SECOND_ORDER, request.queryParameter("ordId").firstValue()))
                .toList();
        LoggedRequest cancel = trail.exchange().requests(CANCEL_ORDER).getFirst();
        assertThat(reads).as("E2.2: чтение состояния заявки повторилось после отмены")
                .filteredOn(request -> request.getLoggedDate().after(cancel.getLoggedDate()))
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(dealRead(trail, deal).path("status").asString())
                .as("E2.2: сделка осталась в координированном выходе, терминала нет").isEqualTo("EXIT_PENDING");
        assertThat(trail.database(Party.TRADING_CORE).count("deal_strategy_action_states"))
                .as("E2.2: строки исполнения по отмене не заведено").isEqualTo(strategyRows);
        assertThat(systemActionsSince(actionMark)).as("E2.2: счётчика попыток у отмены нет")
                .allSatisfy(type -> assertThat(type).isEqualTo(REFRESH));
        assertThat(journalOf(trail, deal)).as("E2.2: строк о терминале у журнала нет")
                .noneSatisfy(row -> assertThat(row.get("event_type")).isEqualTo("DEAL_CLOSED"));
    }

    @Test
    @Order(3)
    @DisplayName("E2.1 — Отмена живой входной ноги доходит до стаба раньше закрытия позиции")
    void e2_1_theLiveEntryLegCancelReachesTheStubBeforeThePositionClose() {
        Long dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        exchangeHoldsSecondLeg(trail, size, CANCELED);

        passUntilTranchesTerminal();
        trail.relayCore();

        List<LoggedRequest> commands = commands();
        List<String> paths = commands.stream().map(request -> request.getUrl().split("\\?")[0]).toList();
        assertThat(paths).as("E2.1: отмена заявки принята раньше закрытия позиции").containsSubsequence(
                CANCEL_ORDER, CLOSE_POSITION);
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E2.1: закрытие нетто-экспозиции — одна команда")
                .hasSize(1);
        assertThat(commands).as("E2.1: обе команды несут ключ счёта")
                .filteredOn(request -> List.of(CANCEL_ORDER, CLOSE_POSITION)
                        .contains(request.getUrl().split("\\?")[0]))
                .allSatisfy(request -> assertThat(request.getHeader("OK-ACCESS-KEY")).isNotBlank());
        assertThat(trail.accesses(Party.CONNECTOR)).as("E2.1: и идентификатор счёта у коннектора")
                .extracting(Side.Access::path)
                .contains("/api/v1/accounts/" + trail.account() + "/orders/cancellations",
                        "/api/v1/accounts/" + trail.account() + "/positions/closures");
        assertThat(trail.database(Party.TRADING_CORE).query("select id from orders where "
                        + "position_reducing_only = false and status not in ('COMPLETED', 'CANCELED', 'ERROR')"))
                .as("E2.1: живых входных ног у траншей не осталось").isEmpty();
        assertThat(journalOf(trail, deal)).as("E2.1: у журнала следа сверх классов E5 нет").hasSize(journalMark);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E2.1: у статистики следа нет")
                .isEqualTo(dealFacts);
    }

    @Test
    @Order(4)
    @DisplayName("E2.5 — Защиты транша снимаются после закрытия нетто-экспозиции, а не раньше")
    void e2_5_theTrancheProtectionsAreRemovedAfterTheNetCloseNotBefore() {
        List<String> paths = commands().stream().map(request -> request.getUrl().split("\\?")[0]).toList();
        List<LoggedRequest> algoCancels = trail.exchange().requests(CANCEL_ALGOS);

        assertThat(paths).as("E2.5: отмена ноги, затем закрытие, затем снятие защит")
                .containsSubsequence(CANCEL_ORDER, CLOSE_POSITION, CANCEL_ALGOS, CANCEL_ALGOS);
        assertThat(paths.subList(0, paths.indexOf(CLOSE_POSITION))).as("E2.5: снятия защиты раньше закрытия нет")
                .doesNotContain(CANCEL_ALGOS);
        assertThat(algoCancels).as("E2.5: сняты отдельная условная заявка и встроенная защита")
                .extracting(LoggedRequest::getBodyAsString)
                .anySatisfy(body -> assertThat(body).contains(EXTERNAL_OCO))
                .anySatisfy(body -> assertThat(body).contains(attached().get("internal_id").toString()));
        assertThat(attached().get("close_reason")).as("E2.5: намерение снятия встроенной — «снята стратегией»")
                .isEqualTo("CANCELED_BY_STRATEGY");
        assertThat(trail.database(Party.TRADING_CORE).query(
                        "select status from algo_orders where status not in ('COMPLETED', 'CANCELED', 'ERROR')"))
                .as("E2.5: живых отдельных защит у транша не осталось").isEmpty();
        assertThat(trail.database(Party.TRADING_CORE).query("select status from attached_algo_orders "
                        + "where status not in ('COMPLETED', 'CANCELED', 'ERROR')"))
                .as("E2.5: живых встроенных защит не осталось").isEmpty();
        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("tranches")).as("E2.5: транш терминален")
                .allSatisfy(tranche -> assertThat(tranche.path("status").asString()).isEqualTo("CLOSED"));
        assertThat(journalOf(trail, deal)).as("E2.5: классов E5 у журнала ещё нет — сделка не терминальна")
                .noneSatisfy(row -> assertThat(row.get("event_type")).isEqualTo("DEAL_CLOSED"));
    }

    /**
     * Красна по находке {@code F7} документа: поверхность сделки отдаёт
     * экспозицию транша без приписанного объёма закрытия уровня сделки, и у
     * закрытого транша она остаётся налитым объёмом
     * (.claude/work/backlog.md §«Поверхность сделки отдаёт экспозицию транша
     * без приписанного объёма закрытия»). Ассерт экспозиции стои́т последним:
     * прочие ожидания клетки прогон с меткой проверяет до него.
     */
    @Test
    @Order(5)
    @Tag("debt")
    @DisplayName("E2.3 — Под каскадом сделки транш своей закрывающей ноги не выпускает")
    void e2_3_underTheDealCascadeATrancheIssuesNoClosingLegOfItsOwn() {
        deal = walkToTwoTranches(trail, 2);
        trail.marketPhaseIs("BEAR_TREND");
        passUntilLeaves(trail, deal, "ACTIVE");
        trail.relayCore();
        Integer decided = coreOutbox(trail, ORDER_DECIDED, deal).size();
        trail.forgetTraces();

        passUntilTranchesTerminal();
        trail.relayCore();

        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E2.3: закрытие позиции на всю сделку одно")
                .hasSize(1);
        assertThat(trail.exchange().requests(Trail.EXCHANGE_ORDER))
                .as("E2.3: reduce-only заявок от траншей нет ни одной")
                .noneSatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("POST"));
        JsonNode read = dealRead(trail, deal);
        assertThat(read.path("tranches")).as("E2.3: у сделки два транша, оба терминальны").hasSize(2)
                .allSatisfy(tranche -> assertThat(tranche.path("status").asString()).isEqualTo("CLOSED"));
        assertThat(tranchesOf(deal)).as("E2.3: у каждого транша своя причина закрытия")
                .allSatisfy(tranche -> assertThat(tranche.get("close_reason")).isNotNull());
        assertThat(coreOutbox(trail, ORDER_DECIDED, deal)).as("E2.3: решений о создании заявки на отрезке нет")
                .hasSize(decided);
        assertThat(journalOf(trail, deal)).as("E2.3: и строк журнала о них")
                .filteredOn(row -> Objects.equals(ORDER_DECIDED, row.get("event_type")))
                .hasSize(decided);
        assertThat(read.path("tranches")).as("E2.3: экспозиция обоих траншей обнулена приписанным объёмом закрытия")
                .allSatisfy(tranche -> assertThat(tranche.path("exposure").decimalValue()).isZero());
    }

    @Test
    @Order(6)
    @DisplayName("E2.6 — Транш с истинным условием входа под сворачиванием заявки не выпускает")
    void e2_6_aTrancheWithATrueEntryConditionPlacesNoOrderUnderCollapse() {
        deal = walkToTwoTranches(trail, 1);
        Map<String, Object> waiting = tranchesOf(deal).stream()
                .filter(tranche -> Objects.equals("PRECHECK", tranche.get("status")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Предусловие E2.6: второго транша в предвходовой "
                        + "проверке нет"));
        Object waitingId = waiting.get("id");
        Database core = trail.database(Party.TRADING_CORE);
        List<Map<String, Object>> tried = core.query(
                "select status from deal_strategy_action_states where deal_tranche_id = ?", waitingId);
        assertThat(tried).as("предусловие E2.6: условие входа второго транша истинно — его действие дошло "
                + "до преконтроля и отвергнуто потолком риска").isNotEmpty();
        Integer decided = coreOutbox(trail, ORDER_DECIDED, deal).size();
        trail.marketPhaseIs("BEAR_TREND");
        trail.forgetTraces();

        trail.passUntil("E2.6: сделка ушла в выход", () -> isFalse(Objects.equals("ACTIVE",
                dealRead(trail, deal).path("status").asString())));
        trail.passUntil("E2.6: второй транш терминален", () -> Objects.equals("CLOSED", core
                .query("select status from deal_tranches where id = ?", waitingId).getFirst().get("status")));
        trail.relayCore();

        assertThat(trail.exchange().requests(Trail.EXCHANGE_ORDER))
                .as("E2.6: постановки заявки по второму траншу у стаба нет")
                .noneSatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("POST"));
        Object dealReason = core.query("select close_reason from deals where internal_id = ?", deal).getFirst()
                .get("close_reason");
        assertThat(core.query("select close_reason from deal_tranches where id = ?", waitingId).getFirst()
                .get("close_reason")).as("E2.6: второй транш закрыт причиной сделки").isNotNull()
                .isEqualTo(dealReason);
        assertThat(core.query("select id from orders where deal_tranche_id = ?", waitingId))
                .as("E2.6: заявки по второму траншу не заведено").isEmpty();
        assertThat(core.query("select status from deal_strategy_action_states where deal_tranche_id = ?",
                waitingId)).as("E2.6: строки исполнения по нему не прибавилось").hasSize(tried.size());
        assertThat(coreOutbox(trail, ORDER_DECIDED, deal)).as("E2.6: решения о заявке нет").hasSize(decided);
        assertThat(journalOf(trail, deal)).as("E2.6: и строки журнала о нём")
                .filteredOn(row -> Objects.equals(ORDER_DECIDED, row.get("event_type")))
                .hasSize(decided);
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Транши сделки строками ядра — в порядке заведения. */
    private static List<Map<String, Object>> tranchesOf(String dealInternalId) {
        return trail.database(Party.TRADING_CORE).query("select t.id, t.status, t.close_reason from deal_tranches t "
                + "join deals d on d.id = t.deal_id where d.internal_id = ? order by t.id", dealInternalId);
    }

    /** Проходы сопровождения, пока все транши сделки не станут терминальными. */
    private static void passUntilTranchesTerminal() {
        trail.passUntil("транши сделки терминальны", () -> {
            JsonNode tranches = dealRead(trail, deal).path("tranches");
            for (JsonNode tranche : tranches) {
                if (isFalse(Objects.equals("CLOSED", tranche.path("status").asString()))) {
                    return false;
                }
            }
            return true;
        });
    }

    /** Команды площадке после последнего забывания: всё, что не чтение, в порядке прихода. */
    private static List<LoggedRequest> commands() {
        return trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .toList();
    }

    private static Map<String, Object> secondLeg() {
        return trail.database(Party.TRADING_CORE)
                .query("select close_reason from orders where external_id = ?", SECOND_ORDER).getFirst();
    }

    private static Map<String, Object> attached() {
        return trail.database(Party.TRADING_CORE).query("select a.internal_id, a.close_reason from "
                + "attached_algo_orders a join orders o on o.id = a.order_id where o.external_id = ?",
                Trail.EXTERNAL_ORDER).getFirst();
    }

    /** Метка общей последовательности строк исполнения — обе таблицы берут её одну. */
    private static Long actionMark() {
        Object mark = trail.database(Party.TRADING_CORE).query("select coalesce(max(id), 0) as id from ("
                + "select id from deal_strategy_action_states union all "
                + "select id from deal_system_action_states) rows").getFirst().get("id");
        return ((Number) mark).longValue();
    }

    private static List<Object> systemActionsSince(Long mark) {
        return trail.database(Party.TRADING_CORE)
                .query("select system_action_type from deal_system_action_states where id > ?", mark).stream()
                .map(row -> row.get("system_action_type"))
                .toList();
    }
}
