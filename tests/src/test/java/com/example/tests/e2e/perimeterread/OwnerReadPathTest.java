package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.IdentityStub;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E2} тропы периметра: чтение и команда через периметр у
 * настоящего владельца (.claude/tests/cases/e2e-perimeter-read.md §«E2 —
 * Чтение и команда через периметр у настоящего владельца»).
 *
 * <p><b>Здесь живут клетки, чей предмет пролога не читает</b> — {@code E2.5}
 * - {@code E2.9}; они прогоняются на такте 1. Клетки {@code E2.1} -
 * {@code E2.4} читают строки пролога и гейтятся находкой {@code F6} первой
 * тропы (§«Кейсы, не прогоняемые сегодня» документа).
 *
 * <p><b>Идентичность каждой стороной проверяется заново</b>, и потому у
 * {@code E2.6} владелец поднят с издателем ВТОРОГО стаба провайдера: периметр
 * токен принимает, владелец — отвергает своей проверкой.
 */
@Tag("e2e")
@DisplayName("E2 — Чтение и команда через периметр у настоящего владельца")
class OwnerReadPathTest {

    private static final String SUBJECT = "subject-s1";

    private static final String INSTRUMENTS = "/api/v1/market-data/instruments";

    private static final String FEATURES = INSTRUMENTS + "/" + Trail.INSTRUMENT + "/features";

    private static final String JOURNAL = "/api/v1/audit/journal/records";

    private static final String ISSUER_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    private static final List<Party> DATA_OWNERS =
            List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS);

    private static Trail trail;

    private static String token;

    private static String tenant;

    @BeforeAll
    static void openTrail() {
        trail = Trail.openPerimeter("p2");
        token = trail.identity().browserToken(SUBJECT, "Trader One");
        Answer context = viaPerimeter("GET", "/api/v1/bff/context", null);
        assertThat(context.status()).as("предусловие — состояние E1.1: " + context.body()).isEqualTo(200);
        tenant = String.valueOf(Json.object(context.body()).get("tenantId"));
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @DisplayName("E2.5 — Владелец, тенанта не принимающий, заголовком не тревожится")
    void e2_5_anOwnerWithoutTenantIsNotDisturbedByTheHeader() {
        trail.forgetTraces();

        Answer answer = viaPerimeter("GET", INSTRUMENTS, null);

        assertThat(answer.status()).as("E2.5: ответ владельца — " + answer.body()).isEqualTo(200);
        List<LoggedRequest> reached = trail.marketData().requests(INSTRUMENTS);
        assertThat(reached).as("E2.5: запрос дошёл до стаба владельца").hasSize(1);
        assertThat(reached.getFirst().getHeader(Trail.TENANT_HEADER))
                .as("E2.5: периметр ставит заголовок контекста всем владельцам одинаково").isEqualTo(tenant);
        assertThat(Json.tree(answer.body())).as("E2.5: ответ вернулся как есть")
                .isEqualTo(Json.tree(Trail.stubbedInstrumentsBody()));
        assertDataOwnersUntouched("E2.5", List.of());
    }

    @Test
    @DisplayName("E2.6 — Владелец проверяет подпись токена сам, а не полагается на периметр")
    void e2_6_theOwnerVerifiesTheTokenItself() {
        IdentityStub foreign = new IdentityStub();
        String ownIssuer = trail.identity().issuer();
        restart(Party.AUDIT, foreign.issuer());
        try {
            Long denials = trail.database(Party.AUDIT).count("access_denials");
            trail.forgetTraces();

            Answer answer = viaPerimeter("GET", JOURNAL + "?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z", null);

            List<Side.Access> audit = trail.accesses(Party.AUDIT);
            assertThat(audit).as("E2.6: до владельца тропа дошла — периметр токен принял").hasSize(1);
            assertThat(audit.getFirst().status()).as("E2.6: запрос отвергнут контуром доступа владельца")
                    .isEqualTo(401);
            assertThat(answer.status()).as("E2.6: ответ владельца донесён его статусом — " + answer.body())
                    .isEqualTo(401);
            assertThat(Json.object(answer.body())).as("E2.6: тело — единый формат отказа владельца")
                    .containsKeys("code", "occurredAt");
            assertThat(trail.database(Party.AUDIT).count("access_denials"))
                    .as("E2.6: строка отказа доступа у владельца завелась").isEqualTo(denials + 1);
            Answer elsewhere = viaPerimeter("GET",
                    "/api/v1/statistics/aggregates/rows?grain=INCIDENT&from=2026-01-01&to=2026-01-02", null);
            assertThat(elsewhere.status()).as("E2.6: тот же токен на прочих сторонах принимается — " + elsewhere.body())
                    .isEqualTo(200);
        } finally {
            restart(Party.AUDIT, ownIssuer);
            foreign.stop();
        }
    }

    /**
     * Красна по построению: периметр пересылает браузеру заголовки
     * соединения владельца — {@code Transfer-Encoding: chunked} и
     * {@code Connection} — и пишет тело без разбиения на куски; ответ,
     * пересланный от владельца, отвечающего кусками, браузером не
     * разбирается вовсе. Находка — .claude/work/backlog.md §«Периметр
     * пересылает браузеру заголовки соединения владельца, и ответ не
     * разбирается».
     */
    @Test
    @Tag("debt")
    @DisplayName("E2.7 — Отказ владельца доезжает его решением, а не переписанным")
    void e2_7_anOwnerRefusalArrivesAsTheOwnerDecided() {
        String absentDeal = Trail.CORE + "/deals/absent-deal";
        String reversedWindow = JOURNAL + "?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z";
        Answer coreDirect = trail.call(Party.TRADING_CORE, "GET", absentDeal, tenant, null);
        Answer auditDirect = trail.call(Party.AUDIT, "GET", reversedWindow, tenant, null);
        trail.forgetTraces();

        Answer coreThrough = viaPerimeter("GET", absentDeal, null);
        Answer auditThrough = viaPerimeter("GET", reversedWindow, null);

        assertThat(coreDirect.status()).as("E2.7: ядро отказывает своим статусом").isGreaterThanOrEqualTo(400);
        assertThat(auditDirect.status()).as("E2.7: журнал отказывает своим статусом").isGreaterThanOrEqualTo(400);
        assertThat(coreThrough.status()).as("E2.7: отказ ядра донесён его статусом").isEqualTo(coreDirect.status());
        assertThat(auditThrough.status()).as("E2.7: отказ журнала донесён его статусом").isEqualTo(auditDirect.status());
        assertSameDecision(coreThrough, coreDirect, "E2.7: тело отказа ядра не переписано");
        assertSameDecision(auditThrough, auditDirect, "E2.7: тело отказа журнала не переписано");
        assertThat(coreThrough.body()).as("E2.7: в один отказ ничего не слито").isNotEqualTo(auditThrough.body());
        assertDataOwnersUntouched("E2.7", List.of(Party.TRADING_CORE, Party.AUDIT));
    }

    @Test
    @DisplayName("E2.8 — Команда пользователя: владелец присваивает полученного тенанта своей строке")
    void e2_8_aUserCommandIsAssignedToTheDerivedTenant() {
        String body = """
                {
                  "globalSimultaneousRiskPerDealPercent": 5,
                  "globalCatastrophicRiskPerDealMultiplier": 100,
                  "globalConsecutiveLossLimit": 4
                }
                """;
        trail.forgetTraces();

        Answer answer = viaPerimeter("PUT", Trail.CORE + "/risk-appetites/" + tenant, body);

        assertThat(answer.status()).as("E2.8: команда принята владельцем — " + answer.body()).isEqualTo(200);
        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE)
                .query("select tenant_internal_id from tenant_risk_appetites");
        assertThat(rows).as("E2.8: строка риск-аппетита принадлежит именно этому тенанту, чужому не завелось")
                .extracting(row -> row.get("tenant_internal_id")).containsExactly(tenant);
        List<Side.Access> core = trail.accesses(Party.TRADING_CORE);
        assertThat(core).as("E2.8: глагол и путь ушли владельцу как есть, повтора нет ни одного")
                .extracting(Side.Access::method, Side.Access::path)
                .containsExactly(tuple("PUT", Trail.CORE + "/risk-appetites/" + tenant));
        assertDataOwnersUntouched("E2.8", List.of(Party.TRADING_CORE));
    }

    @Test
    @DisplayName("E2.9 — Чтение повторяется в пределах бюджета, команда — никогда")
    void e2_9_aReadIsRetriedWithinTheBudgetACommandNever() {
        String answered = "[]";
        trail.marketData().failsTransportThenAnswers(INSTRUMENTS, answered);
        trail.forgetTraces();

        Answer read = viaPerimeter("GET", INSTRUMENTS, null);

        assertThat(trail.marketData().requests(INSTRUMENTS)).as("E2.9: у чтения попыток «бюджет + 1»").hasSize(2);
        assertThat(read.status()).as("E2.9: ответ второй попытки доехал браузеру — " + read.body()).isEqualTo(200);
        assertThat(Json.tree(read.body())).isEqualTo(Json.tree(answered));

        trail.marketData().failsTransportThenAnswers(FEATURES, answered);
        trail.forgetTraces();

        Answer command = viaPerimeter("POST", FEATURES, "{}");

        assertThat(trail.marketData().requests(FEATURES)).as("E2.9: у команды попытка одна").hasSize(1);
        assertThat(command.status()).as("E2.9: браузеру уехал отказ недоступности — " + command.body()).isEqualTo(503);
        assertThat(Json.object(command.body()).get("code")).isEqualTo("PEER_UNAVAILABLE");
        assertDataOwnersUntouched("E2.9", List.of());
        trail.marketData().answers(INSTRUMENTS, Trail.stubbedInstrumentsBody());
    }

    // ---------------------------------------------------------------- ходы и чтения

    private static Answer viaPerimeter(String method, String path, String body) {
        return trail.callWith(token, Party.BFF, method, path, null, body);
    }

    private static void restart(Party party, String issuer) {
        trail.stop(party);
        trail.side(party).set(ISSUER_KEY, issuer);
        trail.start(party);
    }

    /** Решение владельца — его код и его сообщение; момент отказа у двух вызовов законно разный. */
    private static void assertSameDecision(Answer through, Answer direct, String label) {
        Map<String, Object> passed = Json.object(through.body());
        Map<String, Object> decided = Json.object(direct.body());
        assertThat(passed.get("code")).as(label).isEqualTo(decided.get("code"));
        assertThat(passed.get("message")).as(label).isEqualTo(decided.get("message"));
        assertThat(passed.keySet()).as(label).isEqualTo(decided.keySet());
    }

    /** К владельцам данных вне предмета кейса не ушло ни одного обращения. */
    private static void assertDataOwnersUntouched(String label, List<Party> addressed) {
        for (Party owner : DATA_OWNERS) {
            if (addressed.contains(owner)) {
                continue;
            }
            assertThat(trail.accesses(owner)).as(label + ": к " + owner.module() + " обращений нет").isEmpty();
        }
    }
}
