package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import com.example.tests.e2e.smokelive.SmokeRun.JournalRead;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.smokelive.SmokeRun.decimal;
import static com.example.tests.e2e.smokelive.SmokeRun.require;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E6} дыма: журнал и агрегаты тенанта
 * (.claude/tests/cases/smoke-live.md §«E6 — Журнал и агрегаты тенанта»).
 *
 * <p><b>Тики обеих сторон не подаются:</b> ручного фасада у журнала и
 * статистики нет намеренно. Приём журнала идёт слушателем, и след ждётся за
 * реле владельцев; пересчёт агрегатов — по расписанию окружения раз в час, и
 * {@code E6.2} ждёт его до двух тактов ({@link SmokeRun#recomputeTimeout()}).
 *
 * <p><b>Счёт закрытых сделок растёт на число сделок, закрытых прогоном</b>, а
 * не на одну: тропа документа закрывает сделку и на {@code E4.5}, и на
 * {@code E5.3}.
 *
 * <p><b>{@code E6.4} кодом не покрыт</b> — подписка на поток до первого хода
 * тропы и сверка событий с журналом остаются перечнем остатка документа.
 */
@Tag("smoke")
@DisplayName("E6 — Журнал и агрегаты тенанта")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E6JournalSmokeTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private static SmokeRun run;

    @BeforeAll
    static void requirePreconditions() {
        run = SmokeRun.get();
        run.requireCommonPreconditions();
    }

    @Test
    @Order(1)
    @DisplayName("E6.1 — Классы происшествий дыма лежат строками журнала тенанта")
    void e6_1_theSmokeIncidentClassesLieInTheTenantJournal() {
        List<String> closedDeals = closedSmokeDeals();
        require(isFalse(closedDeals.isEmpty()), "состояние после E5.3 либо E4.5 не достигнуто");

        JournalRead journal = run.journal(run.startedAt());
        String window = "/api/v1/audit/journal/records?from=" + iso(run.startedAt()) + "&to="
                + iso(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        Reply tenantWide = run.perimeter().get(window);
        Reply forged = run.perimeter().getWithHeader(window, TENANT_HEADER, UUID.randomUUID().toString());

        for (String deal : closedDeals) {
            for (String eventType : List.of("DEAL_OPENED", "ORDER_DECIDED", "DEAL_CLOSED")) {
                List<JsonNode> rows = journal.of(eventType, deal);
                assertThat(rows).as("E6.1: нет строки класса %s по сделке %s", eventType, deal).isNotEmpty();
                rows.forEach(row -> {
                    assertThat(row.path("exchangeAccountInternalId").asString(""))
                            .as("E6.1: радиус строки %s не несёт счёта дыма", eventType).isEqualTo(run.account());
                    assertThat(row.path("instrumentInternalId").asString(""))
                            .as("E6.1: радиус строки %s не несёт инструмента дыма", eventType)
                            .isEqualTo(run.instrument());
                });
            }
        }
        assertThat(journal.records().stream().anyMatch(row -> "HOLD_RAISED".equals(row.path("eventType").asString(""))
                && Objects.equals(run.instrument(), row.path("instrumentInternalId").asString(""))))
                .as("E6.1: нет строки журнала о ступени на паре дыма").isTrue();
        assertThat(tenantWide.status()).as("E6.1: журнал тенанта не прочитан: %s", tenantWide).isEqualTo(200);
        tenantWide.json().path("records").forEach(row -> assertThat(List.of("", run.account()))
                .as("E6.1: в выдаче строка чужого счёта — чужого тенанта: %s", row)
                .contains(row.path("exchangeAccountInternalId").asString("")));
        List<Instant> moments = new ArrayList<>();
        tenantWide.json().path("records").forEach(row ->
                moments.add(OffsetDateTime.parse(row.path("occurredAt").asString()).toInstant()));
        for (int index = 1; index < moments.size(); index++) {
            assertThat(moments.get(index)).as("E6.1: порядок выдачи не обратный хронологический")
                    .isBeforeOrEqualTo(moments.get(index - 1));
        }
        assertThat(eventIds(forged)).as("E6.1: заголовок тенанта, названный клиентом, дошёл до владельца")
                .isEqualTo(eventIds(tenantWide));
        assertThat(tenantWide.json().path("completeness").isObject())
                .as("E6.1: полнота страницы выдачей не объявлена").isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("E6.2 — Агрегат несёт сделочное зерно с исходом дыма")
    void e6_2_theAggregateCarriesTheSmokeDealGrain() {
        List<String> closedDeals = closedSmokeDeals();
        require(nonNull(run.marketDeal()) && closedDeals.contains(run.marketDeal()),
                "состояние после E5.3 не достигнуто");
        Long expected = run.closedDealsAtStart() + closedDeals.size();

        run.awaitTrace("такт пересчёта агрегатов учёл сделки прогона", run.recomputeTimeout(),
                () -> run.closedDeals(run.aggregates("DEAL")) >= expected);
        JsonNode first = rowOf(run.aggregates("DEAL"), run.marketDefinition());
        JsonNode deal = run.deal(run.marketDeal());

        assertThat(run.closedDeals(run.aggregates("DEAL"))).as("E6.2: счёт закрытых сделок вырос не на число"
                + " сделок, закрытых прогоном").isEqualTo(expected);
        assertThat(first).as("E6.2: строки зерна определения рыночного входа за сутки прогона нет").isNotNull();
        assertThat(first.path("closedDeals").asLong(0)).as("E6.2: у строки определения не одна закрытая сделка")
                .isEqualTo(1L);
        assertThat(first.path("resultCurrency").asString("")).as("E6.2: расчётная валюта строки не названа")
                .isNotBlank();
        if (deal.path("resultProfit").isNumber()) {
            assertThat(decimal(first.path("resultBeforeFundingSum")))
                    .as("E6.2: результат до финансирования у агрегата расходится со сделкой ядра")
                    .isEqualByComparingTo(decimal(deal.path("resultProfit")));
        }

        String assembledAt = first.path("assembledAt").asString("");
        run.awaitTrace("второй такт пересчёта агрегатов", run.recomputeTimeout(), () -> {
            JsonNode row = rowOf(run.aggregates("DEAL"), run.marketDefinition());
            return nonNull(row) && isFalse(Objects.equals(assembledAt, row.path("assembledAt").asString("")));
        });
        JsonNode second = rowOf(run.aggregates("DEAL"), run.marketDefinition());
        assertThat(second.path("closedDeals").asLong(0)).as("E6.2: второй такт удвоил счёт сделок")
                .isEqualTo(first.path("closedDeals").asLong(0));
        assertThat(decimal(second.path("resultBeforeFundingSum")))
                .as("E6.2: второй такт изменил сумму результата")
                .isEqualByComparingTo(decimal(first.path("resultBeforeFundingSum")));
    }

    @Test
    @Order(3)
    @DisplayName("E6.3 — Полнота приёма обеих групп предъявлена, а не подразумевается")
    void e6_3_receptionCompletenessOfBothGroupsIsPresented() {
        JsonNode journal = run.journal(run.startedAt()).completeness();
        JsonNode aggregates = run.aggregates("DEAL").path("completeness");

        for (JsonNode completeness : List.of(journal, aggregates)) {
            assertThat(completeness.path("lowerBound").asString(""))
                    .as("E6.3: нижняя граница полноты пуста — группа не наблюдает ни одной темы: %s", completeness)
                    .isNotBlank();
            assertThat(completeness.path("continuityClaimable").asBoolean(false))
                    .as("E6.3: непрерывность приёма не утверждаема: %s", completeness).isTrue();
        }
    }

    private static List<String> closedSmokeDeals() {
        List<String> closed = new ArrayList<>();
        for (String deal : new String[] {run.restingDeal(), run.marketDeal()}) {
            if (nonNull(deal) && SmokeRun.TERMINAL_DEAL.contains(run.deal(deal).path("status").asString(""))) {
                closed.add(deal);
            }
        }
        return closed;
    }

    private static JsonNode rowOf(JsonNode page, String strategy) {
        for (JsonNode row : page.path("dealRows")) {
            if (Objects.equals(run.account(), row.path("exchangeAccountInternalId").asString(""))
                    && Objects.equals(strategy, row.path("strategyInternalId").asString(""))) {
                return row;
            }
        }
        return null;
    }

    private static List<String> eventIds(Reply page) {
        List<String> ids = new ArrayList<>();
        page.json().path("records").forEach(row -> ids.add(row.path("eventId").asString()));
        return ids;
    }

    private static String iso(Instant moment) {
        return moment.toString().replace(":", "%3A");
    }
}
