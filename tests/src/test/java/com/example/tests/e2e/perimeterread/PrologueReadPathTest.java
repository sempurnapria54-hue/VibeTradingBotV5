package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.SharedStand;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E2} тропы периметра, клетки {@code E2.1}-{@code E2.4} и
 * {@code E2.10}: чтение и команда через периметр у настоящего владельца строк,
 * сложенных прологом
 * (.claude/tests/cases/e2e-perimeter-read.md §«E2 — Чтение и команда через
 * периметр у настоящего владельца»).
 *
 * <p><b>Своим классом, а не в {@code OwnerReadPathTest}:</b> там клетки
 * такта 1 без пролога, и конфигурация тропы у них другая — пролог двигает
 * данные всех владельцев, а клетки такта 1 читают их пустыми.
 *
 * <p><b>Кэш членств перед каждым чтением прогрет</b> выводом контекста: срок
 * записи — ось процесса периметра, пролог длится дольше него, а клетки
 * утверждают, что резолва у владельца членств на чтении нет.
 */
@Tag("e2e")
@DisplayName("E2 — Чтение через периметр строк, сложенных прологом")
class PrologueReadPathTest {

    private static final String CONTEXT = "/api/v1/bff/context";

    private static final String JOURNAL = "/api/v1/audit/journal/records";

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";

    private static final String FOREIGN_TENANT = "tenant-foreign";

    private static final List<Party> DATA_OWNERS =
            List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS);

    private static Trail trail;

    private static String token;

    private static Prologue.Tenancy prologue;

    @BeforeAll
    static void walkThePrologue() {
        trail = SharedStand.perimeter(PrologueReadPathTest.class);
        token = trail.identity().browserToken(Subjects.fresh("subject-s1"), "Trader One");
        prologue = Prologue.walk(trail, token);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            SharedStand.release(PrologueReadPathTest.class);
        }
    }

    @Test
    @DisplayName("E2.1 — Чтение журнала уходит владельцу с выведенным тенантом, и отбирает строки он")
    void e2_1_theJournalReadGoesToTheOwnerWhoSelectsTheRows() {
        String foreign = produceForeignFact();
        Database audit = trail.database(Party.AUDIT);
        Trail.await("E2.1: строка чужого тенанта у журнала есть",
                () -> audit.query("select id from audit_records where event_id = ?", foreign).size() == 1);
        Long rows = audit.count("audit_records");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String path = JOURNAL + "?from=" + now.minusDays(2) + "&to=" + now.plusMinutes(5);
        warmTheCache();

        Answer through = viaPerimeter(path);

        assertThat(through.status()).as("E2.1: ответ владельца — " + through.body()).isEqualTo(200);
        List<Side.Access> reached = trail.accesses(Party.AUDIT);
        assertThat(reached).as("E2.1: запрос ушёл владельцу журнала тем же путём и той же строкой запроса")
                .extracting(Side.Access::uri).containsExactly(path);
        assertThat(reached.getFirst().tenant()).as("E2.1: заголовок контекста несёт выведенный тенант")
                .isEqualTo(prologue.tenant());
        assertThat(reached.getFirst().role()).as("E2.1: и роль в нём").isEqualTo("OWNER");
        JsonNode page = Json.tree(through.body());
        assertThat(page.path("records")).as("E2.1: строки пролога отданы").isNotEmpty()
                .allSatisfy(record -> assertThat(record.toString()).doesNotContain(FOREIGN_TENANT));
        assertThat(page.path("completeness").path("lowerBound").asString())
                .as("E2.1: объявленная полнота страницы доехала").isNotBlank();
        Answer direct = trail.call(Party.AUDIT, "GET", path, prologue.tenant(), null);
        assertThat(Json.tree(through.body())).as("E2.1: ответ вернулся браузеру как отдал владелец")
                .isEqualTo(Json.tree(direct.body()));
        assertThat(audit.count("audit_records")).as("E2.1: своей записи чтение не сделало").isEqualTo(rows);
        assertThat(trail.accesses(Party.AUTH)).as("E2.1: резолва у владельца членств нет — кэш годен").isEmpty();
        assertOthersUntouched("E2.1", Party.AUDIT);
    }

    @Test
    @DisplayName("E2.2 — Чтение агрегатов идёт той же тропой, и форма ответа — владельца")
    void e2_2_theAggregateReadTakesTheSameTrailInTheOwnerForm() {
        Database statistics = trail.database(Party.STATISTICS);
        List<Map<String, Object>> before = statistics.query("select id, assembled_at from incident_aggregates");
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String path = ROWS + "?grain=INCIDENT&from=" + day + "&to=" + day;
        warmTheCache();

        Answer through = viaPerimeter(path);

        assertThat(through.status()).as("E2.2: ответ владельца — " + through.body()).isEqualTo(200);
        assertThat(trail.accesses(Party.STATISTICS)).as("E2.2: запрос ушёл владельцу статистики")
                .extracting(Side.Access::uri, Side.Access::tenant)
                .containsExactly(tuple(path, prologue.tenant()));
        JsonNode page = Json.tree(through.body());
        assertThat(page.path("incidentRows")).as("E2.2: строка суток пролога отдана").hasSize(1);
        Answer direct = trail.call(Party.STATISTICS, "GET", path, prologue.tenant(), null);
        assertThat(page).as("E2.2: форма владельца — поля не добавлены, не переименованы, не убраны")
                .isEqualTo(Json.tree(direct.body()));
        assertThat(statistics.query("select id, assembled_at from incident_aggregates"))
                .as("E2.2: своей записи и такта пересчёта чтение не запустило").isEqualTo(before);
        assertOthersUntouched("E2.2", Party.STATISTICS);
    }

    @Test
    @DisplayName("E2.3 — Чтение определений отбирается заголовком, а не телом запроса")
    void e2_3_theDefinitionReadIsSelectedByTheHeader() {
        String secondToken = trail.identity().browserToken(Subjects.fresh("subject-s2"), "Trader Two");
        Long tenants = trail.database(Party.AUTH).count("tenants");
        warmTheCache();

        Answer first = viaPerimeter(Trail.STRATEGIES);
        Answer second = trail.callWith(secondToken, Party.BFF, "GET", Trail.STRATEGIES, null, null);

        assertThat(first.status()).as("E2.3: первый вызов — " + first.body()).isEqualTo(200);
        assertThat(Json.tree(first.body())).as("E2.3: первому предъявителю — определение пролога")
                .extracting(definition -> definition.path("internalId").asString())
                .contains(prologue.definition());
        assertThat(second.status()).as("E2.3: второй вызов — " + second.body()).isEqualTo(200);
        assertThat(Json.tree(second.body())).as("E2.3: второму — пустой перечень").isEmpty();
        List<Side.Access> reached = trail.accesses(Party.STRATEGIES);
        assertThat(reached).as("E2.3: оба чтения дошли до владельца").hasSize(2);
        String secondTenant = reached.get(1).tenant();
        assertThat(reached.getFirst().tenant()).as("E2.3: первому выведен тенант пролога")
                .isEqualTo(prologue.tenant());
        assertThat(secondTenant).as("E2.3: второму выведен свой тенант, и оба уехали заголовком")
                .isNotBlank().isNotEqualTo(prologue.tenant());
        assertThat(Json.tree(first.body())).as("E2.3: своей фильтрации периметр не делает")
                .isEqualTo(Json.tree(trail.call(Party.STRATEGIES, "GET", Trail.STRATEGIES, prologue.tenant(), null)
                        .body()));
        assertThat(trail.database(Party.AUTH).count("tenants")).as("E2.3: резолв второго завёл ему свой тенант")
                .isEqualTo(tenants + 1);
        assertOthersUntouched("E2.3", Party.STRATEGIES);
    }

    @Test
    @DisplayName("E2.4 — Чтение у ядра: тенант приезжает операндом вызова, а не заголовком")
    void e2_4_theCoreTakesTheTenantAsAnOperandNotAHeader() {
        String deals = Trail.CORE + "/deals?exchangeAccountInternalId=" + prologue.account();
        String numbers = Trail.CORE + "/risk-appetite";
        warmTheCache();

        Answer dealsThrough = viaPerimeter(deals);
        Answer numbersThrough = viaPerimeter(numbers);

        assertThat(dealsThrough.status()).as("E2.4: сделки — " + dealsThrough.body()).isEqualTo(200);
        assertThat(numbersThrough.status()).as("E2.4: числа — " + numbersThrough.body()).isEqualTo(200);
        assertThat(Json.tree(dealsThrough.body())).as("E2.4: сделка пролога отдана")
                .extracting(deal -> deal.path("internalId").asString()).containsExactly(prologue.deal());
        List<Side.Access> reached = trail.accesses(Party.TRADING_CORE);
        assertThat(reached).as("E2.4: ни одного операнда пути периметр не подменил и не дописал")
                .extracting(Side.Access::uri).containsExactly(deals, numbers);
        assertThat(reached).as("E2.4: заголовок контекста в запросе есть")
                .allSatisfy(access -> assertThat(access.tenant()).isEqualTo(prologue.tenant()));
        Answer foreignHeader = trail.call(Party.TRADING_CORE, "GET", deals, FOREIGN_TENANT, null);
        assertThat(Json.tree(foreignHeader.body())).as("E2.4: радиусом отбора у ядра заголовок не служит")
                .isEqualTo(Json.tree(dealsThrough.body()));
        assertOthersUntouched("E2.4", Party.TRADING_CORE);
    }

    @Test
    @DisplayName("E2.10 — Команда пользователя у второго пишущего владельца: смена статуса ложится на строку "
            + "выведенного тенанта")
    void e2_10_aUserCommandAtTheSecondWritingOwnerLandsOnTheDerivedTenantRow() {
        String status = Trail.STRATEGIES + "/" + prologue.definition() + "/status";
        String body = """
                {"status": "INACTIVE"}
                """;
        warmTheCache();

        Answer answer = trail.callWith(token, Party.BFF, "PUT", status, null, body);

        assertThat(answer.status()).as("E2.10: команда принята владельцем — " + answer.body()).isEqualTo(200);
        assertThat(trail.database(Party.STRATEGIES).query("select status from strategies where internal_id = ?",
                prologue.definition()).getFirst().get("status"))
                .as("E2.10: определение тенанта пролога стало неактивным").isEqualTo("INACTIVE");
        List<Side.Access> reached = trail.accesses(Party.STRATEGIES);
        assertThat(reached).as("E2.10: глагол и путь ушли владельцу как есть, повтора нет ни одного")
                .extracting(Side.Access::method, Side.Access::path)
                .containsExactly(tuple("PUT", status));
        assertThat(reached.getFirst().tenant()).as("E2.10: заголовком контекста — выведенный тенант")
                .isEqualTo(prologue.tenant());
        assertOthersUntouched("E2.10", Party.STRATEGIES);
        Answer foreign = trail.call(Party.STRATEGIES, "PUT", status, FOREIGN_TENANT, body);
        assertThat(foreign.status()).as("E2.10: с чужим тенантом заголовком владелец определения не находит — "
                + foreign.body()).isEqualTo(404);
    }

    // ---------------------------------------------------------------- ходы и чтения

    /**
     * Факт чужого тенанта в теме ядра — так, как положил бы производитель.
     *
     * @return идентичность события: строки прочих тенантов на общем стенде
     *         есть и без него, и дождаться надо именно его
     */
    private static String produceForeignFact() {
        String eventId = UUID.randomUUID().toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("eventId", eventId);
        headers.put("eventType", "DEAL_OPENED");
        headers.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        headers.put("version", "1");
        trail.produce(Substrate.CORE_TOPIC, FOREIGN_TENANT, """
                {"dealInternalId": "%s", "exchangeAccountInternalId": "foreign-account",
                 "instrumentInternalId": "%s", "strategyInternalId": "%s", "entryReason": "STRATEGY",
                 "direction": "LONG", "entryMarketPhase": "BULL_TREND"}
                """.formatted(UUID.randomUUID(), Trail.INSTRUMENT, UUID.randomUUID()), headers);
        return eventId;
    }

    /** Кэш членств прогрет выводом контекста, следы до хода кейса забыты. */
    private static void warmTheCache() {
        Answer context = trail.callWith(token, Party.BFF, "GET", CONTEXT, null, null);
        assertThat(context.status()).as("контекст для прогрева кэша — " + context.body()).isEqualTo(200);
        trail.forgetTraces();
    }

    private static Answer viaPerimeter(String path) {
        return trail.callWith(token, Party.BFF, "GET", path, null, null);
    }

    /** К владельцам данных вне предмета кейса не ушло ни одного обращения. */
    private static void assertOthersUntouched(String label, Party addressed) {
        for (Party owner : DATA_OWNERS) {
            if (Objects.equals(owner, addressed)) {
                continue;
            }
            assertThat(trail.accesses(owner)).as(label + ": к " + owner.module() + " обращений нет").isEmpty();
        }
    }
}
