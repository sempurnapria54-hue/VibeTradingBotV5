package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B9} документа кейсов: контур доступа и единый формат отказа
 * (.claude/tests/cases/bff.md §«B9 — Контур доступа и единый формат
 * отказа»).
 *
 * <p><b>Числа, пиньнутые здесь, пиньнуты законно:</b> {@code 401} ставит
 * точка входа контура, {@code 404} и {@code 405} — контейнер, и дом отказа
 * фиксирует их за этими исходами
 * (.claude/tests/cases/bff.md §«Число ответа и класс отказа — разные
 * ожидания»).
 *
 * <p><b>«Устройства наружу не рассказывает» мерится по ТЕЛУ целиком</b>:
 * ни имени класса исключения, ни пакета дерева, ни строки стека — в каком
 * бы поле они ни оказались.
 */
class AccessContourBoxTest extends SharedBffBox {

    /** Признаки устройства, которых в теле отказа быть не должно. */
    private static final List<String> INTERNALS = List.of("Exception", "com.example", "\tat ", "java.");

    @Test
    @DisplayName("B9.1 — Умолчание контура закрыто")
    void b9_1_theContourDefaultIsClosed() {
        authAnswersOneMembership();
        List<String> owned = List.of(OwnerStub.AUTH, OwnerStub.STRATEGIES, OwnerStub.TRADING_CORE,
                OwnerStub.MARKET_DATA, OwnerStub.AUDIT, OwnerStub.STATISTICS);
        owned.forEach(owner -> owners.answersAnything(owner, 200, "{}"));

        List<Answer> answers = new ArrayList<>();
        answers.add(getAnonymously(CONTEXT));
        answers.add(postAnonymously(TICKETS, ""));
        answers.add(getAnonymously("/v3/api-docs"));
        owned.forEach(owner -> answers.add(getAnonymously("/api/v1/" + owner + "/anything")));

        assertThat(answers).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(401);
            assertThat(answer.header("WWW-Authenticate")).startsWith("Bearer");
        });
        assertThat(owners.count()).isZero();
    }

    @Test
    @DisplayName("B9.2 — Открытых точек ровно две, и вторая — тропа билета")
    void b9_2_exactlyTwoOpenPointsAndTheSecondIsTheTicketPath() {
        authAnswersOneMembership();

        Answer health = getAnonymously(HEALTH);
        assertThat(health.status()).isEqualTo(200);
        // О состоянии контура проба живости не сообщает ничего: ни состава
        // компонентов, ни подробностей — только общий статус и имена групп
        // проб живости.
        assertThat(health.asObject()).doesNotContainKeys("components", "details");
        assertThat(health.body().toLowerCase()).doesNotContain("jwt", "oauth", "issuer", "security", "auth");

        try (Subscription withoutTicket = subscribeWithoutTicket()) {
            assertThat(withoutTicket.carriesStream()).isFalse();
            assertThat(withoutTicket.errorCode()).isEqualTo(UNAUTHENTICATED);
        }

        // Съёма метрик нет вовсе: без предъявления — отказ контура (точка не
        // в перечне открытых), под принятым токеном — отказ контейнера
        // (точки не существует).
        Answer metricsAnonymous = getAnonymously("/actuator/prometheus");
        assertThat(metricsAnonymous.status()).isEqualTo(401);
        assertThat(metricsAnonymous.errorCode()).isEqualTo(UNAUTHENTICATED);
        Answer metricsAuthenticated = get("/actuator/prometheus");
        assertThat(metricsAuthenticated.errorCode()).isEqualTo(NOT_ACCEPTED);
    }

    @Test
    @DisplayName("B9.3 — Сессии контур не заводит")
    void b9_3_theContourKeepsNoSession() {
        authAnswersOneMembership();

        List<Answer> authenticated = List.of(get(CONTEXT), get(CONTEXT), post(TICKETS, ""));
        Answer afterwards = getAnonymously(CONTEXT);

        assertThat(authenticated).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(200);
            assertThat(answer.header("Set-Cookie")).isNull();
        });
        assertThat(afterwards.status()).isEqualTo(401);
        assertThat(afterwards.header("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("B9.4 — Отказ фильтр-цепочки отвечает единым error-DTO")
    void b9_4_aFilterChainRefusalAnswersWithTheUnifiedErrorDto() {
        Answer answer = getAnonymously(CONTEXT);

        assertThat(answer.body()).isNotBlank();
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.asObject()).containsKeys("code", "message", "occurredAt");
        assertThat(answer.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(INTERNALS).allSatisfy(internal -> assertThat(answer.body()).doesNotContain(internal));
    }

    @Test
    @DisplayName("B9.5 — Отказ билета отвечает тем же форматом и тем же классом")
    void b9_5_aTicketRefusalAnswersWithTheSameFormAndClass() {
        authAnswersOneMembership();
        String tampered = Tickets.tampered(issuedTicket());

        Answer contour = getAnonymously(CONTEXT);
        try (Subscription refused = subscribe(tampered)) {
            assertThat(refused.carriesStream()).isFalse();
            assertThat(refused.carriesErrorDto()).isTrue();
            assertThat(refused.errorCode()).isEqualTo(contour.errorCode()).isEqualTo(UNAUTHENTICATED);
            // Форма одна: те же поля у обоих отказов.
            Map<String, Object> ticketRefusal = new Answer(refused.status(), refused.errorBody(), Map.of())
                    .asObject();
            assertThat(ticketRefusal.keySet()).isEqualTo(contour.asObject().keySet());
        }
    }

    @Test
    @DisplayName("B9.6 — Отказы контейнера отвечают нашим телом при своём статусе")
    void b9_6_containerRefusalsAnswerWithOurBodyAtTheirStatus() {
        authAnswersOneMembership();

        Answer unknown = get("/unknown");
        Answer tooShort = get("/api/v1");
        Answer wrongVerb = call("POST", HEALTH, "{}");

        assertThat(unknown.status()).isEqualTo(404);
        assertThat(tooShort.status()).isEqualTo(404);
        assertThat(wrongVerb.status()).isEqualTo(405);
        assertThat(List.of(unknown, tooShort, wrongVerb)).allSatisfy(answer -> {
            assertThat(answer.carriesErrorDto()).isTrue();
            assertThat(answer.errorCode()).isEqualTo(NOT_ACCEPTED);
            assertThat(answer.errorMessage()).isNotBlank();
            assertThat(INTERNALS).allSatisfy(internal -> assertThat(answer.body()).doesNotContain(internal));
        });
    }

    @Test
    @DisplayName("B9.7 — Непредусмотренный отказ наружу устройства не рассказывает")
    void b9_7_anUnexpectedFailureTellsNothingAboutTheInsides() {
        // Владелец членств отвечает телом, которое периметр разобрать не
        // может: тропа резолва доходит до непредусмотренного отказа.
        authAnswers("this is not a membership list");
        Integer mark = AppLog.mark();

        Answer answer = get(CONTEXT);

        assertThat(answer.status()).isEqualTo(500);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(INTERNAL_FAILURE);
        assertThat(INTERNALS).allSatisfy(internal -> assertThat(answer.body()).doesNotContain(internal));
        // В журнал отказ уходит целиком — с классом исключения.
        assertThat(AppLog.since(mark)).contains("Exception");
    }

    @Test
    @Tag("debt")
    @DisplayName("B9.8 — Отказ доступа оставляет наблюдаемый след")
    void b9_8_anAccessRefusalLeavesAnObservableTrace() {
        List<String> paths = List.of(CONTEXT, "/api/v1/trading-core/deals", "/api/v1/strategies/definitions",
                "/api/v1/audit/journal", "/api/v1/statistics/summary", "/api/v1/market-data/candles",
                "/api/v1/auth/memberships/self", "/v3/api-docs", PERIMETER + "/unknown", "/unknown");
        Integer mark = AppLog.mark();

        paths.forEach(path -> assertThat(getAnonymously(path).status()).as(path).isEqualTo(401));

        // Ожидание из дома: каждый отказ виден строкой журнала с путём и
        // глаголом, а частота отказов — рядом наблюдателя окружения.
        // Сегодня красно: точка входа отказа не пишет ни строки, а реестра
        // рядов у сервиса нет вовсе (находка F-5 документа кейсов).
        String log = AppLog.since(mark);
        assertThat(paths).allSatisfy(path -> assertThat(log).contains(path));
        assertThat(get("/actuator/prometheus").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("B9.9 — Пер-операционных проверок права у периметра нет")
    void b9_9_thePerimeterHasNoPerOperationRightChecks() {
        authAnswers(Bodies.memberships(TENANT, "VIEWER"));
        owners.answers(OwnerStub.STRATEGIES, "/api/v1/strategies/definitions", "{\"accepted\": true}");

        Answer answer = post("/api/v1/strategies/definitions", "{\"name\": \"b9-9\"}");

        // Периметр запрос переслал: право операции проверяет владелец.
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("{\"accepted\": true}");
        assertThat(owners.single(OwnerStub.STRATEGIES, "/api/v1/strategies/definitions")
                .getHeader(ROLE_HEADER)).isEqualTo("VIEWER");
    }
}
