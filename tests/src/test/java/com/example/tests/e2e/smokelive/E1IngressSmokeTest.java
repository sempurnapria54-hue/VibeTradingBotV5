package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Группа {@code E1} дыма: вход снаружи и предъявление себя
 * (.claude/tests/cases/smoke-live.md §«E1 — Вход снаружи и предъявление
 * себя»).
 *
 * <p><b>Предъявление — токен человека от провайдера окружения тропой
 * браузера</b> ({@link HolderToken}); токен чужой подписи прогон собирает сам
 * ({@link ForeignTokens}). Кейсы {@code E1.1} и {@code E1.2} токена держателя
 * не требуют, кроме чтения сделок для отрицания следа.
 *
 * <p><b>Что из ожиданий группы наружу не наблюдается и потому не
 * ассертится</b> (.claude/tests/cases/smoke-live.md §«Пробелы покрытия»,
 * {@code G6}): «к владельцам не ушло вызова», «резолв ушёл под токеном
 * предъявителя», «подпись проверена локально», «строки отказа у {@code auth}
 * нет» — ни лога, ни счётчика вызовов, ни базы наружу не отдаёт ни одна
 * точка. Отрицание следа в журнале у этой группы не ассертится тоже: приём
 * журнала асинхронен и тикает по расписанию окружения, и немедленное чтение
 * пустоты мерило бы такт, а не отсутствие следа.
 */
@Tag("smoke")
@DisplayName("E1 — Вход снаружи и предъявление себя")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E1IngressSmokeTest {

    private static final String CONTEXT = "/api/v1/bff/context";

    private static final String UNAUTHENTICATED = "ACCESS_UNAUTHENTICATED";

    /** Поля единого формата отказа (ErrorApiResponse) — сверх них тело владельца нести нечего. */
    private static final List<String> ERROR_FIELDS = List.of("code", "reason", "message", "occurredAt");

    /** Допуск часов у проверки момента истечения токена (JwtTimestampValidator по умолчанию). */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private static final Integer SUBJECT_ALT_NAME_DNS = 2;

    private static Perimeter anonymous;

    @BeforeAll
    static void resolveHost() {
        Stand.requireHostResolves();
        anonymous = new Perimeter(new HolderToken());
    }

    @Test
    @Order(1)
    @DisplayName("E1.1 — Имя хоста окружения отвечает, и /api/v1 ведёт на периметр")
    void e1_1_theIngressHostAnswersAndRoutesApiToThePerimeter() throws CertificateParsingException {
        SmokeRun run = SmokeRun.get();
        List<String> dealsBefore = run.dealSignatures();

        Reply context = anonymous.call("GET", CONTEXT, null, null);
        Reply unrouted = anonymous.call("GET", "/smoke-unrouted-" + UUID.randomUUID(), null, null);

        assertThat(context.tls()).as("E1.1: вход GET %s без токена — соединение не по TLS", CONTEXT).isPresent();
        assertThat(context.serverCertificates()).as("E1.1: сервер не предъявил сертификата").isNotEmpty();
        X509Certificate certificate = (X509Certificate) context.serverCertificates().getFirst();
        assertThat(dnsNamesOf(certificate))
                .as("E1.1: сертификат выдан не на имя хоста окружения %s", Stand.host())
                .contains(Stand.host());
        assertThat(context.status())
                .as("E1.1: GET %s без токена — ожидался отказ предъявления периметра, пришло %s", CONTEXT, context)
                .isEqualTo(401);
        assertThat(context.errorCode())
                .as("E1.1: отказ не единым форматом периметра — маршрут не доехал до периметра: %s", context)
                .isEqualTo(UNAUTHENTICATED);
        assertThat(unrouted.status())
                .as("E1.1: путь вне маршрутов /api/v1, /realms/vibetrading и /resources имеет маршрут: %s", unrouted)
                .isEqualTo(404);
        assertThat(unrouted.errorCode())
                .as("E1.1: путь вне маршрутов дошёл до периметра — ответ единым форматом: %s", unrouted)
                .isEmpty();
        assertThat(run.dealSignatures()).as("E1.1: анонимный вход оставил след в сделках счёта")
                .isEqualTo(dealsBefore);
    }

    /**
     * Половина ожидания {@code E1.1}, красная по построению на стенде: консоль
     * администратора провайдера и служебный реалм снаружи достижимы вторым
     * объектом входа, который оператор провайдера заводит сам
     * (.claude/work/backlog.md §«Вход снаружи окружения — остаток сверки на
     * стенде»). Метка {@code debt} — красное ожидание, а не ослабленный ассерт.
     */
    @Test
    @Order(2)
    @Tag("debt")
    @DisplayName("E1.1 — Консоль администратора провайдера и служебный реалм снаружи недостижимы")
    void e1_1_theProviderConsoleAndServiceRealmAreUnreachableFromOutside() {
        Reply console = anonymous.call("GET", "/admin/master/console/", null, null);
        Reply serviceRealm = anonymous.call("GET", "/realms/master/.well-known/openid-configuration", null, null);

        assertThat(console.status()).as("E1.1: консоль администратора провайдера снаружи отвечает: %s", console)
                .isNotEqualTo(200);
        assertThat(serviceRealm.status()).as("E1.1: служебный реалм провайдера снаружи отвечает: %s", serviceRealm)
                .isNotEqualTo(200);
    }

    @Test
    @Order(3)
    @DisplayName("E1.2 — Без предъявления периметр отказывает, и своей строки отказа у него нет")
    void e1_2_withoutPresentationThePerimeterRefuses() {
        SmokeRun run = SmokeRun.get();
        List<String> dealsBefore = run.dealSignatures();
        Map<String, Reply> refused = Map.of(
                "собственная точка периметра", anonymous.call("GET", CONTEXT, null, null),
                "проксируемая точка владельца", anonymous.call("GET",
                        "/api/v1/trading-core/deals?exchangeAccountInternalId=" + run.account(), null, null),
                "выдача билета потока", anonymous.call("POST", "/api/v1/bff/stream-tickets", null, null),
                "открытие потока без билета", anonymous.call("GET", "/api/v1/bff/stream", null, null));

        refused.forEach((kind, reply) -> {
            assertThat(reply.status()).as("E1.2: %s без токена — ожидался отказ предъявления, пришло %s", kind, reply)
                    .isEqualTo(401);
            assertThat(reply.errorCode()).as("E1.2: %s — отказ не единым форматом: %s", kind, reply)
                    .isEqualTo(UNAUTHENTICATED);
            assertThat(fieldNames(reply.json()))
                    .as("E1.2: %s — в ответе отказа тело владельца, а не только единый формат: %s", kind, reply)
                    .isSubsetOf(ERROR_FIELDS);
        });
        assertThat(run.dealSignatures()).as("E1.2: отказанный вход оставил след в сделках счёта")
                .isEqualTo(dealsBefore);
    }

    @Test
    @Order(4)
    @DisplayName("E1.3 — Токен человека даёт контекст тенанта, и тенант ровно один")
    void e1_3_aHumanTokenYieldsTheTenantContextAndTheTenantIsOne() {
        SmokeRun run = SmokeRun.get();
        String token = run.holder().current();
        JsonNode claims = HolderToken.claimsOf(token);

        Reply context = run.perimeter().call("GET", CONTEXT, token, null);

        assertThat(claims.path("iss").asString(""))
                .as("E1.3: токен выдан не провайдером окружения — издатель не имя хоста окружения")
                .isEqualTo(Stand.issuer());
        assertThat(claims.path("azp").asString(""))
                .as("E1.3: токен выдан не браузерному клиенту — тропа не человеческая")
                .isEqualTo(HolderToken.BROWSER_CLIENT);
        assertThat(context.status())
                .as("E1.3: GET %s с токеном держателя — ожидался контекст (409 — тенантов больше одного): %s",
                        CONTEXT, context)
                .isEqualTo(200);
        assertThat(context.json().path("tenantId").asString(""))
                .as("E1.3: контекст без идентичности тенанта: %s", context).isNotBlank();
        assertThat(context.json().path("role").asString(""))
                .as("E1.3: контекст без роли предъявителя: %s", context).isNotBlank();
        assertThat(run.perimeter().call("GET", CONTEXT, run.holder().current(), null).json().path("tenantId")
                .asString("")).as("E1.3: повторный контекст назвал другого тенанта — тенант не один")
                .isEqualTo(context.json().path("tenantId").asString(""));
    }

    @Test
    @Order(5)
    @DisplayName("E1.4 — Токен чужого издателя периметром не принят")
    void e1_4_aTokenOfAForeignSignerIsNotAccepted() {
        SmokeRun run = SmokeRun.get();
        String tenant = run.tenant();
        List<String> dealsBefore = run.dealSignatures();
        String holderToken = run.holder().current();
        Map<String, Reply> refused = Map.of(
                "подписанный своим ключом прогона", run.perimeter().call("GET", CONTEXT,
                        ForeignTokens.signedByOwnKey(holderToken), null),
                "с подписью провайдера над другим содержимым", run.perimeter().call("GET", CONTEXT,
                        ForeignTokens.tamperedProviderToken(holderToken), null));

        refused.forEach((kind, reply) -> {
            assertThat(reply.status()).as("E1.4: токен %s принят периметром: %s", kind, reply).isEqualTo(401);
            assertThat(reply.errorCode()).as("E1.4: токен %s — отказ не единым форматом: %s", kind, reply)
                    .isEqualTo(UNAUTHENTICATED);
        });
        assertThat(run.tenant(true)).as("E1.4: чужое предъявление сменило тенанта держателя").isEqualTo(tenant);
        assertThat(run.dealSignatures()).as("E1.4: отказанный вход оставил след в сделках счёта")
                .isEqualTo(dealsBefore);
    }

    /**
     * Просроченный токен провайдера подделать нельзя — его подписывает только
     * провайдер. Поэтому кейс берёт свежий токен держателя и ЖДЁТ его
     * истечения сверх допуска часов: ожидание длится срок жизни токена
     * реалма (минуты), и это цена кейса, а не ослабление.
     */
    @Test
    @Order(6)
    @DisplayName("E1.4 — Просроченный токен провайдера периметром не принят")
    void e1_4_anExpiredProviderTokenIsNotAccepted() {
        SmokeRun run = SmokeRun.get();
        String expiring = HolderToken.acquire();
        Instant expiresAt = HolderToken.expiryOf(expiring);
        assertThat(run.perimeter().call("GET", CONTEXT, expiring, null).status())
                .as("E1.4: предусловие — свежий токен держателя принят").isEqualTo(200);

        await("истечение токена держателя сверх допуска часов")
                .atMost(Duration.between(Instant.now(), expiresAt).plus(CLOCK_SKEW).plusMinutes(2))
                .pollInterval(Duration.ofSeconds(10))
                .until(() -> Instant.now().isAfter(expiresAt.plus(CLOCK_SKEW).plusSeconds(5)));
        Reply expired = run.perimeter().call("GET", CONTEXT, expiring, null);

        assertThat(expired.status()).as("E1.4: просроченный токен провайдера принят: %s", expired).isEqualTo(401);
        assertThat(expired.errorCode()).as("E1.4: просроченный токен — отказ не единым форматом: %s", expired)
                .isEqualTo(UNAUTHENTICATED);
    }

    private static List<String> dnsNamesOf(X509Certificate certificate) throws CertificateParsingException {
        List<String> names = new ArrayList<>();
        Collection<List<?>> alternatives = certificate.getSubjectAlternativeNames();
        if (nonNull(alternatives)) {
            alternatives.stream()
                    .filter(entry -> SUBJECT_ALT_NAME_DNS.equals(entry.get(0)))
                    .forEach(entry -> names.add(String.valueOf(entry.get(1))));
        }
        return names;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
