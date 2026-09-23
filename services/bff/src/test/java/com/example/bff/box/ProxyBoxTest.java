package com.example.bff.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B8} документа кейсов: проксирование владельцу
 * (.claude/tests/cases/bff.md §«B8 — Проксирование владельцу»).
 *
 * <p><b>Владелец за периметром — стаб, и адресат читается приставкой
 * пути:</b> «ушло владельцу X» есть утверждение о разрешённом адресе, а не о
 * том, что запрос куда-то ушёл ({@link OwnerStub}).
 *
 * <p><b>Запросы предусловия отделяются от запросов клетки.</b> Резолв
 * контекста уходит в тот же журнал стаба, что и пересылка, поэтому клетка,
 * утверждающая о пересылке, читает журнал владельца данных, а не весь.
 *
 * <p><b>Число ответа, написанное нашим кодом, пишется в «Факт», а не в
 * ассерт</b> (.claude/tests/cases/bff.md §«Число ответа и класс отказа —
 * разные ожидания»); ответ ВЛАДЕЛЬЦА — другое дело: он пересылается как
 * есть, и его число есть предмет клетки.
 */
class ProxyBoxTest extends SharedBffBox {

    private static final String DEALS = "/api/v1/trading-core/deals";

    private static final String DEFINITIONS = "/api/v1/strategies/definitions";

    /**
     * Заголовки, которые исходящий клиент периметра ставит САМ, — рамка его
     * собственного соединения и его собственные умолчания. Предметом
     * пересылки они не являются: браузер их не присылал. Перечень снят с
     * наблюдённого запроса: рамка соединения, попытка перехода на HTTP/2 и
     * умолчания клиента JDK.
     */
    private static final Set<String> OUTGOING_CLIENT_OWN = Set.of("host", "content-length", "connection",
            "user-agent", "accept-encoding", "transfer-encoding", "upgrade", "http2-settings");

    @Test
    @DisplayName("B8.1 — Чтение уходит владельцу с путём и строкой запроса как есть")
    void b8_1_aReadGoesToTheOwnerWithPathAndQueryAsIs() {
        authAnswersOneMembership();
        owners.answersWith(OwnerStub.TRADING_CORE, DEALS, 200, "application/json",
                "{\"deals\": [\"D-1\", \"D-2\"]}");

        Answer answer = get(DEALS + "?status=OPEN&limit=20");

        LoggedRequest forwarded = owners.single(OwnerStub.TRADING_CORE, DEALS);
        assertThat(forwarded.getMethod().getName()).isEqualTo("GET");
        assertThat(OwnerStub.fullPathOf(forwarded)).isEqualTo(DEALS + "?status=OPEN&limit=20");
        // Ответ владельца вернулся как есть: статус, тело и тип содержимого.
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("{\"deals\": [\"D-1\", \"D-2\"]}");
        assertThat(answer.header("Content-Type")).isEqualTo("application/json");
        assertThat(recordsSinceStart()).isZero();
    }

    @Test
    @DisplayName("B8.2 — Заголовки уходят закрытым перечнем")
    void b8_2_headersGoAsAClosedList() {
        authAnswersOneMembership();
        owners.answers(OwnerStub.STRATEGIES, DEFINITIONS, "{}");
        String presented = token();

        postWithHeaders(DEFINITIONS, presented, Map.of("X-Box-Marker", "browser-only",
                "Accept-Language", "ru"), "{\"name\": \"Definition b8-2\"}");

        LoggedRequest forwarded = owners.single(OwnerStub.STRATEGIES, DEFINITIONS);
        // Заголовки предмета — ровно четыре; всё прочее ставит исходящий
        // клиент сам, и браузерного среди этого нет.
        assertThat(subjectHeaders(forwarded)).containsOnlyKeys(
                "authorization", "x-tenant-id", "x-tenant-role", "content-type");
        assertThat(forwarded.getHeader("Authorization")).isEqualTo("Bearer " + presented);
        assertThat(forwarded.getHeader("X-Tenant-Id")).isEqualTo(TENANT);
        assertThat(forwarded.getHeader("X-Tenant-Role")).isEqualTo(ROLE);
        assertThat(forwarded.getHeader("Content-Type")).startsWith("application/json");
        // Хост — адрес владельца, а не исходного соединения.
        assertThat(forwarded.getHeader("Host")).doesNotContain(addressOf("").replace("http://", ""));
    }

    @Test
    @DisplayName("B8.3 — Присланный браузером контекст не пересылается владельцу")
    void b8_3_theBrowserSentContextIsNotForwarded() {
        authAnswersOneMembership();
        owners.answersAnything(OwnerStub.TRADING_CORE, 200, "{}");

        getWith(DEALS, token(), Map.of(TENANT_HEADER, "T9", ROLE_HEADER, "OWNER"));

        LoggedRequest forwarded = owners.single(OwnerStub.TRADING_CORE, DEALS);
        assertThat(forwarded.header(TENANT_HEADER).values()).containsExactly(TENANT);
        assertThat(forwarded.header(ROLE_HEADER).values()).containsExactly(ROLE);
        assertThat(forwarded.getHeaders().all()).noneMatch(header -> header.values().contains("T9"));
    }

    @Test
    @DisplayName("B8.4 — Ответ владельца, каким бы он ни был, есть его решение")
    void b8_4_theOwnerAnswerIsItsDecision() {
        authAnswersOneMembership();
        owners.answersInTurn(OwnerStub.TRADING_CORE, DEALS, List.of(409, 500),
                List.of("{\"owner\": \"conflict\"}", "{\"owner\": \"failure\"}"));

        Answer conflict = get(DEALS);
        Answer failure = get(DEALS);

        // Статус и тело владельца — как есть; нашим error-DTO не подменены.
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).isEqualTo("{\"owner\": \"conflict\"}");
        assertThat(failure.status()).isEqualTo(500);
        assertThat(failure.body()).isEqualTo("{\"owner\": \"failure\"}");
        assertThat(conflict.carriesErrorDto()).isFalse();
        assertThat(failure.carriesErrorDto()).isFalse();
        // Повтора нет ни одного: отказ владельца — не отказ транспорта.
        assertThat(owners.requests(OwnerStub.TRADING_CORE, DEALS)).hasSize(2);
    }

    @Test
    @DisplayName("B8.5 — Чтение повторяется в пределах объявленного бюджета")
    void b8_5_aReadIsRetriedWithinTheDeclaredBudget() {
        authAnswersOneMembership();
        // Бюджет повторов штатного прогона — один (`perimeter.read-retries`):
        // первая попытка рвётся, вторая — последняя в бюджете — отвечает.
        owners.failsTransportThenAnswers(OwnerStub.TRADING_CORE, DEALS, 1, "{\"deals\": []}");

        Answer answer = get(DEALS);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("{\"deals\": []}");
        assertThat(owners.requests(OwnerStub.TRADING_CORE, DEALS)).hasSize(2);
        assertThat(owners.addressedOwners()).containsOnly(OwnerStub.AUTH, OwnerStub.TRADING_CORE);

        // Второй вход (пробел G2): глагол HEAD объявлен повторяемым наравне
        // с GET.
        owners.reset();
        authAnswersOneMembership();
        owners.failsTransportThenAnswers(OwnerStub.TRADING_CORE, DEALS, 1, "");
        Answer head = call("HEAD", DEALS, null);

        assertThat(head.status()).isEqualTo(200);
        assertThat(owners.requests(OwnerStub.TRADING_CORE, DEALS)).hasSize(2);
    }

    @Test
    @DisplayName("B8.6 — Мутирующий запрос не повторяется никогда")
    void b8_6_aMutatingRequestIsNeverRetried() {
        authAnswersOneMembership();
        owners.failsTransport(OwnerStub.TRADING_CORE);

        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            owners.forgetRequests();
            Answer answer = call(method, DEALS, "{}");

            assertThat(owners.requests(OwnerStub.TRADING_CORE, DEALS)).as(method).hasSize(1);
            assertThat(answer.errorCode()).as(method).isEqualTo(PEER_UNAVAILABLE);
        }
    }

    @Test
    @DisplayName("B8.7 — Исчерпанный бюджет отвечает классом соседа, а не своим отказом")
    void b8_7_anExhaustedBudgetAnswersWithThePeerClass() {
        authAnswersOneMembership();
        owners.failsTransport(OwnerStub.TRADING_CORE);
        Integer mark = AppLog.mark();

        Answer answer = get(DEALS);

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(PEER_UNAVAILABLE)
                .isNotEqualTo(REQUEST_REJECTED).isNotEqualTo(INTERNAL_FAILURE);
        // Строка журнала называет владельца и глагол.
        assertThat(AppLog.since(mark)).contains("owner=" + OwnerStub.TRADING_CORE, "method=GET");
    }

    @Test
    @DisplayName("B8.8 — Адресат выводится первым сегментом; таблицы маршрутов нет")
    void b8_8_theAddresseeIsTheFirstSegment() {
        List<String> names = List.of(OwnerStub.AUTH, OwnerStub.STRATEGIES, OwnerStub.TRADING_CORE,
                OwnerStub.MARKET_DATA, OwnerStub.AUDIT, OwnerStub.STATISTICS, "risk-desk");
        names.forEach(name -> owners.answersAnything(name, 200, "{\"from\": \"" + name + "\"}"));
        // Заготовка членств ставится ПОСЛЕ: у стаба выигрывает поставленная
        // последней, а «любой путь auth» накрыл бы и точку резолва.
        authAnswersOneMembership();

        names.forEach(name -> {
            Answer answer = get("/api/v1/" + name + "/probe");
            assertThat(answer.body()).as(name).isEqualTo("{\"from\": \"" + name + "\"}");
        });

        // Все семь ушли по адресу, собранному из шаблона подстановкой
        // имени, — и седьмой, которого в инвентаре нет, тоже.
        assertThat(names).allSatisfy(name ->
                assertThat(owners.requests(name, "/api/v1/" + name + "/probe")).as(name).hasSize(1));
    }

    @Test
    @DisplayName("B8.9 — Периметр себя не проксирует")
    void b8_9_thePerimeterDoesNotProxyItself() {
        authAnswersOneMembership();

        Answer answer = get(PERIMETER + "/unknown-point");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(REQUEST_REJECTED).isNotEqualTo(NOT_ACCEPTED);
        // Повод текстом не различается — его различает отсутствие вызова.
        assertThat(owners.count()).isZero();
    }

    @Test
    @DisplayName("B8.10 — Имя не той формы адресом не становится")
    void b8_10_aMisshapenNameDoesNotBecomeAnAddress() {
        authAnswersOneMembership();
        owners.answersAnything("auth", 200, "{}");

        for (String name : List.of("AUTH", "auth:8080", "a_uth")) {
            Answer answer = get("/api/v1/" + name + "/memberships/self");
            assertThat(answer.errorCode()).as(name).isEqualTo(REQUEST_REJECTED);
        }
        // Обход каталога и пустой сегмент отвергает контур раньше нашего
        // кода; ожидание у них слабее — к стабам не ушло ничего.
        Answer traversal = get("/api/v1/../auth/memberships/self");
        Answer empty = get("/api/v1//memberships/self");

        assertThat(traversal.status()).isGreaterThanOrEqualTo(400);
        assertThat(empty.status()).isGreaterThanOrEqualTo(400);
        assertThat(owners.count()).isZero();
    }

    @Test
    @DisplayName("B8.11 — Тело пересылается байтами и не разбирается")
    void b8_11_theBodyIsForwardedAsBytes() {
        authAnswersOneMembership();
        owners.answers(OwnerStub.STRATEGIES, DEFINITIONS, "{\"accepted\": true}");
        String notJson = "{not json at all, \"quoted\": [";

        Answer answer = call("POST", DEFINITIONS, notJson);

        LoggedRequest forwarded = owners.single(OwnerStub.STRATEGIES, DEFINITIONS);
        assertThat(new String(forwarded.getBody(), StandardCharsets.UTF_8)).isEqualTo(notJson);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("{\"accepted\": true}");
    }

    @Test
    @DisplayName("B8.12 — Пустое тело есть отсутствие тела, а не тело нулевой длины")
    void b8_12_anEmptyBodyIsNoBody() {
        authAnswersOneMembership();
        owners.answers(OwnerStub.STRATEGIES, DEFINITIONS, "{}");

        Answer read = call("GET", DEFINITIONS, null);
        Answer write = call("POST", DEFINITIONS, "");

        assertThat(read.status()).isEqualTo(200);
        assertThat(write.status()).isEqualTo(200);
        List<LoggedRequest> forwarded = owners.requests(OwnerStub.STRATEGIES, DEFINITIONS);
        assertThat(forwarded).hasSize(2);
        // Тела нет ни у одного. Длины у чтения нет вовсе; у записи без тела
        // исходящий клиент ставит рамку `Content-Length: 0` сам — глагол
        // определяет смысл тела, и рамка обязана быть (RFC 9110), — а
        // периметр длины не выдумывает: ненулевой она не бывает.
        assertThat(forwarded).allSatisfy(request -> assertThat(request.getBody()).isEmpty());
        assertThat(forwarded.getFirst().containsHeader("Content-Length")).isFalse();
        assertThat(forwarded.get(1).getHeader("Content-Length")).isIn(null, "0");
    }

    @Test
    @Tag("debt")
    @DisplayName("B8.13 — Переговорные заголовки содержимого пересылаются владельцу")
    void b8_13_negotiationHeadersAreForwarded() {
        authAnswersOneMembership();
        owners.answersToAccept(OwnerStub.TRADING_CORE, DEALS, "application/xml", "<deals/>");

        Answer answer = getWith(DEALS, token(), Map.of("Accept", "application/xml"));

        // Ожидание из дома: принимаемое браузера уходит владельцу, и тот
        // согласует ответ с ним. Сегодня красно: перечень пересылаемого
        // несёт только тип содержимого, а `Accept` уходит умолчанием
        // исходящего клиента (находка F-6 документа кейсов).
        LoggedRequest forwarded = owners.single(OwnerStub.TRADING_CORE, DEALS);
        assertThat(forwarded.getHeader("Accept")).isEqualTo("application/xml");
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("<deals/>");
    }

    @Test
    @DisplayName("B8.14 — Без принятого токена пересылки не происходит")
    void b8_14_withoutAnAcceptedTokenNothingIsForwarded() {
        authAnswersOneMembership();
        owners.answersAnything(OwnerStub.TRADING_CORE, 200, "{}");

        List<Answer> answers = List.of(getAnonymously(DEALS),
                getWith(DEALS, identity.foreignKeyToken()),
                getWith(DEALS, identity.expiredToken()));

        assertThat(answers).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(401);
            assertThat(answer.errorCode()).isEqualTo(UNAUTHENTICATED);
        });
        // Ни владельцу, ни `auth`: резолв контекста до отказа не доходит.
        assertThat(owners.count()).isZero();
    }

    @Test
    @DisplayName("B8.15 — Отказ резолва контекста владельца не тревожит")
    void b8_15_aContextResolutionRefusalDoesNotReachTheOwner() {
        authAnswers(Bodies.membershipsOf(TENANT, SECOND_TENANT));
        owners.answersAnything(OwnerStub.TRADING_CORE, 200, "{}");

        Answer answer = get(DEALS);

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(answer.errorMessage()).contains("членств больше одного");
        assertThat(owners.requests(OwnerStub.TRADING_CORE)).isEmpty();
    }

    @Test
    @DisplayName("B8.16 — Агрегации сегодня нет ни одной, и слитого отказа нет тоже")
    void b8_16_thereIsNoAggregationAndNoMergedRefusal() {
        authAnswersOneMembership();
        owners.answersAnything(OwnerStub.STATISTICS, 200, "{\"from\": \"statistics\"}");
        owners.failsTransport(OwnerStub.AUDIT);

        // Обход поверхности: собственные точки периметра и пересылка к
        // каждому из двух владельцев. Каждый запрос браузера уходит ровно
        // одному владельцу данных — либо ни одному.
        get(CONTEXT);
        post(TICKETS, "");
        assertThat(owners.addressedOwners()).containsOnly(OwnerStub.AUTH);

        owners.forgetRequests();
        Answer fromStatistics = get("/api/v1/statistics/summary");
        assertThat(owners.addressedOwners()).containsOnly(OwnerStub.STATISTICS);

        owners.forgetRequests();
        Answer fromAudit = get("/api/v1/audit/journal");
        assertThat(owners.addressedOwners()).containsOnly(OwnerStub.AUDIT);

        // Отказ одного источника не сливается с ответом другого: у каждого
        // свой исход.
        assertThat(fromStatistics.status()).isEqualTo(200);
        assertThat(fromAudit.errorCode()).isEqualTo(PEER_UNAVAILABLE);
    }

    /** Заголовки запроса к владельцу за вычетом собственных заголовков исходящего клиента. */
    private static Map<String, List<String>> subjectHeaders(LoggedRequest forwarded) {
        return forwarded.getHeaders().all().stream()
                .filter(header -> isFalse(OUTGOING_CLIENT_OWN.contains(header.key().toLowerCase())))
                .collect(Collectors.toMap(header -> header.key().toLowerCase(), HttpHeader::values));
    }

    /** Мутирующий вызов JSON под названным токеном с добавленными заголовками браузера. */
    private Answer postWithHeaders(String path, String presented, Map<String, String> headers, String body) {
        Map<String, String> all = new HashMap<>(headers);
        all.put("Content-Type", "application/json");
        return postWith(path, presented, all, body);
    }
}
