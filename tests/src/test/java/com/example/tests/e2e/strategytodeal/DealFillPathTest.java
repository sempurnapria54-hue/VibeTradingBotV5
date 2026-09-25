package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.strategytodeal.DealTrace.dealRead;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E5} тропы «активация определения → сделка»: факт с площадки
 * возвращается в зеркало ядра
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E5 — Факт с площадки
 * возвращается в зеркало ядра»).
 *
 * <p><b>Каждый кейс начинает с состояния после {@code E4.1}</b> — своей
 * сделки с отправленной входной заявкой; стаб площадки добычи до хода кейса
 * заявки не знает.
 */
@Tag("e2e")
@DisplayName("E5 — Факт с площадки возвращается в зеркало ядра")
class DealFillPathTest {

    private static final String CONNECTOR_LOOKUP = "/api/v1/accounts/" + Trail.ACCOUNT + "/orders/lookup";

    private static Trail trail;

    private String dealId;

    private Long journalBefore;

    private Long incidentsBefore;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("e5");
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @BeforeEach
    void submitTheEntry() {
        trail.exchangeAcceptsCommands();
        trail.withoutDeals();
        trail.activeDefinition();
        dealId = trail.openDeal();
        trail.forgetTraces();
        trail.entrySubmitted();
        trail.relayCore();
        Trail.await("журнал принял решение о заявке", () -> DealTrace.present(trail.database(Party.AUDIT).query(
                "select id from audit_records where deal_internal_id = ? and event_type = ?", dealId,
                DealTrace.ORDER_DECIDED)));
        journalBefore = trail.database(Party.AUDIT).count("audit_records");
        incidentsBefore = trail.database(Party.STATISTICS).count("incident_facts");
    }

    @Test
    @DisplayName("E5.1 — Добыча факта: наполнение заявки доезжает до зеркала и двигает транш")
    void e5_1_theFetchedFillReachesTheMirrorAndMovesTheTranche() {
        Database core = trail.database(Party.TRADING_CORE);
        Long outboxBefore = core.count("outbox_events");
        String size = plain(core.query("select size from orders").getFirst().get("size"));
        trail.exchangeFillsEntry();
        trail.forgetTraces();

        trail.orchestrate();
        trail.relayCore();

        assertThat(trail.exchange().requests(Trail.EXCHANGE_ORDER))
                .as("E5.1: к площадке пришла добыча заявки").isNotEmpty()
                .allSatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("GET"));
        assertThat(trail.accesses(Party.CONNECTOR)).as("E5.1: ядро спросило заявку у коннектора")
                .extracting(Side.Access::path).contains(CONNECTOR_LOOKUP);
        Map<String, Object> mirror = core.query(
                "select status, external_status, accumulated_fill_size, average_price from orders").getFirst();
        assertThat(mirror.get("external_status")).as("E5.1: зеркало несёт наблюдённое состояние").isEqualTo("filled");
        assertThat(plain(mirror.get("accumulated_fill_size"))).as("E5.1: размер наполнения").isEqualTo(size);
        assertThat(plain(mirror.get("average_price"))).as("E5.1: цена наполнения").isEqualTo(Trail.ENTRY_PRICE);
        JsonNode tranche = dealRead(trail, dealId).path("tranches").get(0);
        assertThat(tranche.path("status").asString()).as("E5.1: транш сдвинут по своему жизненному циклу")
                .isNotEqualTo("PRECHECK");
        assertThat(core.count("outbox_events")).as("E5.1: класса события на переход строки исполнения нет")
                .isEqualTo(outboxBefore);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E5.1: у журнала следа нет")
                .isEqualTo(journalBefore);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E5.1: у статистики следа нет")
                .isEqualTo(incidentsBefore);
        assertThat(trail.accesses(Party.STRATEGIES)).as("E5.1: к владельцу определений обращений нет").isEmpty();
    }

    @Test
    @DisplayName("E5.2 — Подтверждение приёма состоянием не считается")
    void e5_2_anAcknowledgementIsNotAState() {
        Database core = trail.database(Party.TRADING_CORE);

        JsonNode deal = dealRead(trail, dealId);

        Map<String, Object> mirror = core.query(
                "select external_id, accumulated_fill_size, average_price from orders").getFirst();
        assertThat(mirror.get("external_id")).as("E5.2: зеркало несёт внешний идентификатор из подтверждения")
                .isEqualTo(Trail.EXTERNAL_ORDER);
        assertThat(mirror.get("accumulated_fill_size")).as("E5.2: наполнения нет").isNull();
        assertThat(mirror.get("average_price")).as("E5.2: цены исполнения нет").isNull();
        assertThat(deal.path("tranches").get(0).path("status").asString())
                .as("E5.2: транш не сдвинут в состояние, требующее наблюдённого факта").isEqualTo("PRECHECK");
        assertThat(core.count("positions")).as("E5.2: позиции у сделки нет").isZero();
        assertThat(trail.exchange().requests(Trail.EXCHANGE_ORDER))
                .as("E5.2: запросов добычи не приходило — только постановка")
                .noneMatch(request -> Objects.equals("GET", request.getMethod().getName()));
        List<Object> classes = trail.database(Party.AUDIT).query(
                "select event_type from audit_records where deal_internal_id = ?", dealId).stream()
                .map(row -> row.get("event_type")).toList();
        assertThat(classes).as("E5.2: у журнала следа сверх E4.1 нет")
                .containsExactlyInAnyOrder(DealTrace.DEAL_OPENED, DealTrace.ORDER_DECIDED);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E5.2: у статистики следа сверх E4.1 нет")
                .isEqualTo(incidentsBefore);
    }

    private static String plain(Object number) {
        return ((BigDecimal) number).stripTrailingZeros().toPlainString();
    }
}
