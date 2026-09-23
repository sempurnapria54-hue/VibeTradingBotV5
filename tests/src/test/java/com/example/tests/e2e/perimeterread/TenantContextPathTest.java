package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E1} тропы «токен → контекст тенанта → чтение и поток»:
 * токен, владелец членств и контекст тенанта
 * (.claude/tests/cases/e2e-perimeter-read.md §«E1 — Токен, владелец членств
 * и контекст тенанта»).
 *
 * <p><b>Владелец членств здесь — сторона, а не стаб:</b> стык «периметр →
 * владелец членств» и есть первое звено тропы. Периметр находит его тем же
 * способом, что в кластере, — шаблоном адреса из имени владельца, — и тропа
 * воспроизводит конвенцию кластера, а не подменяет её
 * ({@link Trail#openPerimeter}).
 *
 * <p><b>Срок кэша членств — ось процесса периметра</b>, и кейс, которому
 * нужен свой срок, поднимает периметр заново с ним: часы процессов не
 * двигаются. Перезапуск сам снимает кэш, поэтому кейс, чей предмет —
 * ИСТЕЧЕНИЕ срока, наполняет кэш уже после подъёма.
 *
 * <p><b>Субъекты — средство прогона</b>: {@code S1} заводит тенанта первым
 * ходом, {@code S2} — второго для радиуса, {@code S3} ни разу не
 * предъявлялся до кейса о недоступном владельце.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E1 — Токен, владелец членств и контекст тенанта (тропа периметра)")
class TenantContextPathTest {

    private static final String CONTEXT = "/api/v1/bff/context";

    private static final String TICKETS = "/api/v1/bff/stream-tickets";

    private static final String RESOLVE = "/api/v1/auth/memberships/self";

    private static final String FIRST_SUBJECT = "subject-s1";

    private static final String FIRST_NAME = "Trader One";

    private static final String SECOND_SUBJECT = "subject-s2";

    private static final String THIRD_SUBJECT = "subject-s3";

    private static final String CACHE_TTL_KEY = "perimeter.membership.cache-ttl";

    private static final List<Party> DATA_OWNERS =
            List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS);

    private static Trail trail;

    private static String firstToken;

    @BeforeAll
    static void openTrail() {
        trail = Trail.openPerimeter("p1");
        firstToken = trail.identity().browserToken(FIRST_SUBJECT, FIRST_NAME);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E1.1 — Браузерный токен без членств заводит тенанта у владельца, и он же становится контекстом")
    void e1_1_aBrowserTokenWithoutMembershipsProvisionsTheTenant() {
        Database auth = trail.database(Party.AUTH);
        assertThat(membershipsOf(FIRST_SUBJECT)).as("E1.1: предусловие — членств у S1 нет").isEmpty();
        Long tenantsBefore = auth.count("tenants");
        trail.forgetTraces();

        Answer answer = context(firstToken);

        assertThat(answer.status()).as("E1.1: контекст поднят — ответ " + answer.body()).isEqualTo(200);
        Map<String, Object> context = Json.object(answer.body());
        assertThat(context.get("role")).isEqualTo("OWNER");
        assertThat(String.valueOf(context.get("tenantId"))).isNotBlank();
        assertThat(resolves()).as("E1.1: к владельцу членств пришёл ровно один резолв").hasSize(1);
        assertThat(auth.count("tenants")).as("E1.1: заведена одна строка тенанта").isEqualTo(tenantsBefore + 1);
        List<Map<String, Object>> memberships = membershipsOf(FIRST_SUBJECT);
        assertThat(memberships).as("E1.1: одна строка членства субъекта").hasSize(1);
        assertThat(memberships.getFirst().get("role")).isEqualTo("OWNER");
        Map<String, Object> tenant = tenantOf(FIRST_SUBJECT);
        assertThat(tenant.get("internal_id")).as("E1.1: контекст — тот самый тенант").isEqualTo(context.get("tenantId"));
        assertThat(tenant.get("name")).as("E1.1: имя тенанта — из claim'а имени предъявителя").isEqualTo(FIRST_NAME);
        assertDataOwnersUntouched("E1.1");
        assertTopicsEmpty("E1.1");
    }

    @Test
    @Order(2)
    @DisplayName("E1.5 — Годная запись кэша владельца не спрашивает, а копии членства не заводит")
    void e1_5_aValidCacheEntryDoesNotAskTheOwner() {
        String tenant = provisioned(firstToken);
        trail.forgetTraces();

        List<Answer> answers = List.of(context(firstToken), context(firstToken), context(firstToken));

        assertThat(answers).as("E1.5: три ответа одинаковы").extracting(Answer::body).containsOnly(answers.getFirst().body());
        assertThat(tenantIdOf(answers.getFirst())).isEqualTo(tenant);
        assertThat(resolves()).as("E1.5: годная запись кэша — к владельцу не ходили").isEmpty();
        assertThat(membershipsOf(FIRST_SUBJECT)).as("E1.5: строк членства одна").hasSize(1);
        assertDataOwnersUntouched("E1.5");

        restartPerimeter("60s");
        trail.forgetTraces();

        Answer afterRestart = context(firstToken);

        assertThat(tenantIdOf(afterRestart)).as("E1.5: ответ владельца тот же").isEqualTo(tenant);
        assertThat(resolves()).as("E1.5: перезапуск снял кэш — запрос снова дошёл до владельца").hasSize(1);
    }

    @Test
    @Order(3)
    @DisplayName("E1.4 — К владельцу членств уезжает токен ПОЛЬЗОВАТЕЛЯ, а не идентичность периметра")
    void e1_4_theUserTokenTravelsToTheMembershipOwner() {
        restartPerimeter("60s");
        trail.forgetTraces();

        Answer answer = context(firstToken);

        assertThat(answer.status()).isEqualTo(200);
        List<Side.Access> resolves = resolves();
        assertThat(resolves).hasSize(1);
        assertThat(resolves.getFirst().bearer()).as("E1.4: предъявление у владельца — тот же токен байт в байт")
                .isEqualTo(firstToken);
        assertThat(membershipsOf(FIRST_SUBJECT)).as("E1.4: субъект членства — субъект этого токена").hasSize(1);
        assertThat(trail.identity().requests()).as("E1.4: исходящей идентичности периметр не заводит — выдачи токена нет")
                .noneMatch(request -> request.getUrl().startsWith("/token"));
        assertDataOwnersUntouched("E1.4");
    }

    @Test
    @Order(4)
    @DisplayName("E1.2 — Повторный вывод второго тенанта не заводит")
    void e1_2_aRepeatedResolutionProvisionsNoSecondTenant() {
        restartPerimeter("2s");
        String tenant = provisioned(firstToken);
        Object createdAt = tenantOf(FIRST_SUBJECT).get("created_at");
        Long tenants = trail.database(Party.AUTH).count("tenants");
        pastCacheTtl(Duration.ofSeconds(2));
        trail.forgetTraces();

        Answer answer = context(firstToken);

        assertThat(tenantIdOf(answer)).as("E1.2: тот же тенант").isEqualTo(tenant);
        assertThat(Json.object(answer.body()).get("role")).isEqualTo("OWNER");
        assertThat(resolves()).as("E1.2: срок истёк — пришёл второй резолв").hasSize(1);
        assertThat(trail.database(Party.AUTH).count("tenants")).as("E1.2: строк тенанта не прибавилось")
                .isEqualTo(tenants);
        assertThat(membershipsOf(FIRST_SUBJECT)).as("E1.2: строк членства одна").hasSize(1);
        assertThat(tenantOf(FIRST_SUBJECT).get("created_at")).as("E1.2: момент создания не изменился")
                .isEqualTo(createdAt);
        assertDataOwnersUntouched("E1.2");
        assertTopicsEmpty("E1.2");
    }

    @Test
    @Order(5)
    @DisplayName("E1.6 — Истиной остаётся ответ владельца: кэш срок переживает, а роль истины не занимает")
    void e1_6_theOwnerAnswerStaysTheTruth() {
        restartPerimeter("3s");
        String tenant = provisioned(firstToken);
        Integer memberships = membershipsOf(FIRST_SUBJECT).size();
        trail.forgetTraces();

        Answer beforeExpiry = context(firstToken);
        Integer resolvesBeforeExpiry = resolves().size();
        pastCacheTtl(Duration.ofSeconds(3));
        Answer afterExpiry = context(firstToken);
        Integer resolvesAfterExpiry = resolves().size();
        restartPerimeter("3s");
        Answer afterRestart = context(firstToken);

        assertThat(List.of(beforeExpiry, afterExpiry, afterRestart)).as("E1.6: три ответа одинаковы по значению")
                .extracting(TenantContextPathTest::tenantIdOf).containsOnly(tenant);
        assertThat(resolvesBeforeExpiry).as("E1.6: до истечения срока резолва не приходило").isZero();
        assertThat(resolvesAfterExpiry).as("E1.6: после истечения он пришёл").isEqualTo(1);
        assertThat(resolves()).as("E1.6: после перезапуска пришёл третий — кэш процесс не переживает").hasSize(2);
        assertThat(membershipsOf(FIRST_SUBJECT)).as("E1.6: резолв существующего членства ничего не переписал")
                .hasSize(memberships);
        assertDataOwnersUntouched("E1.6");
    }

    @Test
    @Order(6)
    @Tag("debt")
    @DisplayName("E1.3 — Служебная идентичность тенанта не заводит")
    void e1_3_aServiceIdentityProvisionsNoTenant() {
        String serviceToken = trail.identity().issuedServiceToken();
        Long tenants = trail.database(Party.AUTH).count("tenants");
        Long memberships = trail.database(Party.AUTH).count("memberships");
        trail.forgetTraces();

        Answer answer = context(serviceToken);
        Answer repeated = context(serviceToken);

        assertThat(trail.database(Party.AUTH).count("tenants")).as("E1.3: строки тенанта не заведено")
                .isEqualTo(tenants);
        assertThat(trail.database(Party.AUTH).count("memberships")).as("E1.3: строки членства не заведено")
                .isEqualTo(memberships);
        assertThat(resolves()).as("E1.3: резолв пришёл и отвергнут по claim'у клиента")
                .isNotEmpty().allSatisfy(access -> assertThat(access.status()).isEqualTo(403));
        assertDataOwnersUntouched("E1.3");
        assertThat(repeated.body()).as("E1.3: повтор состояния не меняет").isNotNull();
        assertThat(Json.object(answer.body())).as("E1.3: единый error-DTO без тенанта — ответ " + answer.body())
                .containsKeys("code", "occurredAt").doesNotContainKey("tenantId");
        assertThat(answer.status()).as("E1.3: отказ доступа доезжает классом отказа доступа").isEqualTo(403);
    }

    @Test
    @Order(7)
    @DisplayName("E1.7 — Владелец членств остановлен: контекст отвечает отказом, и тенант не заводится нигде")
    void e1_7_aStoppedMembershipOwnerRefusesTheContext() {
        String thirdToken = trail.identity().browserToken(THIRD_SUBJECT, "Trader Three");
        restartPerimeter("60s");
        trail.stop(Party.AUTH);
        trail.forgetTraces();

        Answer context = context(thirdToken);
        Answer ticket = trail.callWith(thirdToken, Party.BFF, "POST", TICKETS, null, "");

        try {
            assertThat(context.status()).as("E1.7: контекст отвечает отказом — " + context.body()).isGreaterThanOrEqualTo(400);
            assertThat(Json.object(context.body())).as("E1.7: единый формат отказа").containsKeys("code", "occurredAt");
            assertThat(ticket.status()).as("E1.7: билет не выдан — " + ticket.body()).isGreaterThanOrEqualTo(400);
            assertThat(Json.object(ticket.body())).containsKeys("code", "occurredAt");
            assertDataOwnersUntouched("E1.7");
        } finally {
            trail.start(Party.AUTH);
        }
        assertThat(membershipsOf(THIRD_SUBJECT)).as("E1.7: после подъёма у владельца членств S3 нет").isEmpty();

        Answer contextAgain = context(thirdToken);
        Answer ticketAgain = trail.callWith(thirdToken, Party.BFF, "POST", TICKETS, null, "");

        assertThat(contextAgain.status()).as("E1.7: повтор после подъёма проходит").isEqualTo(200);
        assertThat(ticketAgain.status()).as("E1.7: билет выдан после подъёма — " + ticketAgain.body())
                .isBetween(200, 201);
        assertThat(membershipsOf(THIRD_SUBJECT)).as("E1.7: тенант заведён тем же ходом, что E1.1").hasSize(1);
    }

    @Test
    @Order(8)
    @DisplayName("E1.8 — Присланный браузером тенант не читается: владельцу уезжает выведенный")
    void e1_8_aBrowserSuppliedTenantIsNotRead() {
        String first = provisioned(firstToken);
        String second = provisioned(trail.identity().browserToken(SECOND_SUBJECT, "Trader Two"));
        assertThat(second).as("E1.8: у второго субъекта свой тенант").isNotEqualTo(first);
        Long tenants = trail.database(Party.AUTH).count("tenants");
        trail.forgetTraces();

        Answer answer = trail.callWith(firstToken, Party.BFF, "GET", "/api/v1/audit/journal/records"
                + "?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z&tenantInternalId=" + second, second, null);

        assertThat(answer.status()).as("E1.8: чтение отдано — " + answer.body()).isEqualTo(200);
        List<Side.Access> audit = trail.accesses(Party.AUDIT);
        assertThat(audit).as("E1.8: журнал получил одно чтение").hasSize(1);
        assertThat(audit.getFirst().tenant()).as("E1.8: заголовок контекста несёт выведенного тенанта, а не присланного")
                .isEqualTo(first);
        assertThat(trail.database(Party.AUTH).count("tenants")).as("E1.8: у владельца членств строк не изменилось")
                .isEqualTo(tenants);
        for (Party owner : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.STATISTICS)) {
            assertThat(trail.accesses(owner)).as("E1.8: к " + owner.module() + " обращений нет").isEmpty();
        }
    }

    // ---------------------------------------------------------------- ходы и чтения

    private static Answer context(String token) {
        return trail.callWith(token, Party.BFF, "GET", CONTEXT, null, null);
    }

    /** Контекст токена, поднятый ходом тропы: тенант заводится, если его ещё нет. */
    private static String provisioned(String token) {
        Answer answer = context(token);
        assertThat(answer.status()).as("предусловие — контекст поднят: " + answer.body()).isEqualTo(200);
        return tenantIdOf(answer);
    }

    private static String tenantIdOf(Answer answer) {
        return String.valueOf(Json.object(answer.body()).get("tenantId"));
    }

    private static List<Side.Access> resolves() {
        return trail.accesses(Party.AUTH).stream()
                .filter(access -> Objects.equals("POST", access.method()) && Objects.equals(RESOLVE, access.path()))
                .toList();
    }

    private static List<Map<String, Object>> membershipsOf(String subject) {
        return trail.database(Party.AUTH).query("select * from memberships where user_id = ?", subject);
    }

    private static Map<String, Object> tenantOf(String subject) {
        return trail.database(Party.AUTH).query("""
                select t.* from tenants t join memberships m on m.tenant_id = t.internal_id where m.user_id = ?
                """, subject).getFirst();
    }

    private static void restartPerimeter(String cacheTtl) {
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(CACHE_TTL_KEY, cacheTtl);
        trail.start(Party.BFF);
    }

    /** Срок записи кэша истекает временем процесса, а не подвинутыми часами: ждётся он паузой сверх срока. */
    private static void pastCacheTtl(Duration ttl) {
        Awaitility.await().pollDelay(ttl.plusMillis(500)).atMost(ttl.plusSeconds(5)).until(() -> true);
    }

    /** Контекст тенанта владельцев данных не трогает: ни одного обращения к их поверхности. */
    private static void assertDataOwnersUntouched(String label) {
        for (Party owner : DATA_OWNERS) {
            assertThat(trail.accesses(owner)).as(label + ": к " + owner.module() + " обращений нет").isEmpty();
        }
    }

    private static void assertTopicsEmpty(String label) {
        assertThat(trail.records(Substrate.CORE_TOPIC)).as(label + ": тема ядра пуста").isEmpty();
        assertThat(trail.records(Substrate.STRATEGY_TOPIC)).as(label + ": тема владельца определений пуста")
                .isEmpty();
    }
}
