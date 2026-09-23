package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testsupport.SchedulerCapacityContract;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Группа {@code B11} документа кейсов: границы — чего периметр не делает
 * (.claude/tests/cases/bff.md §«B11 — Границы: чего периметр не делает»).
 *
 * <p><b>Всякое «не делает» предъявляется на ПОЛНОМ цикле, а не на пустом
 * прогоне.</b> Отрицание, снятое там, где периметру нечего было делать,
 * было бы зелено и у периметра, который делает запрещённое при первой же
 * работе: клетка сперва проводит его через контекст, билет, подписку,
 * приём, пересылку и отказы и только потом смотрит, чего не случилось.
 *
 * <p><b>Утверждение об отсутствии способности у всей единицы читается
 * объявлением и деревом, а не прогоном</b>, где её не понадобилось
 * (.claude/skills/test-code.md §«Уровень 2 — юниты библиотеки или модуля»):
 * базы — объявлением конфигурации и драйвером на пути классов, тика
 * состояния приёма — счётом {@code @Scheduled}-методов дерева.
 */
class BoundariesBoxTest extends SharedBffBox {

    @Test
    @DisplayName("B11.1 — Своей базы нет ни одной")
    void b11_1_thereIsNoDatabaseOfItsOwn() throws IOException {
        fullCycle("TB1");

        // Контекст поднят на субстрате, где контейнера базы нет вовсе, и
        // полный цикл прошёл: ни одна тропа базы не потребовала.
        assertThat(getAnonymously(HEALTH).status()).isEqualTo(200);
        // Подключаться не к чему и нечем: адреса базы и миграций
        // конфигурация не объявляет, драйвера базы на пути классов нет.
        assertThat(declaredKeys()).noneMatch(key -> key.startsWith("spring.datasource")
                || key.startsWith("spring.flyway") || key.startsWith("spring.jpa"));
        assertThat(isOnClasspath("org.postgresql.Driver")).isFalse();
        assertThat(isOnClasspath("org.flywaydb.core.Flyway")).isFalse();
    }

    @Test
    @DisplayName("B11.2 — В темы периметр не публикует ничего")
    void b11_2_thePerimeterPublishesNothing() {
        Long published = fullCycle("TB2");
        // Отказы каждого рода: доступа, по праву резолва, соседа.
        getAnonymously(CONTEXT);
        authAnswers(Bodies.membershipsOf(TENANT, SECOND_TENANT));
        getWith(CONTEXT, identity.tokenFor(secondSubject));
        owners.failsTransport(OwnerStub.AUDIT);
        get("/api/v1/audit/journal");

        // В темы легли ровно записи, положенные клеткой.
        assertThat(recordsSinceStart()).isEqualTo(published);
    }

    @Test
    @DisplayName("B11.3 — Durable-группой периметр не принимает")
    void b11_3_thePerimeterIsNotADurableConsumer() throws IOException {
        Set<String> before = new HashSet<>(wire.consumerGroups());
        List<String> added = new ArrayList<>();
        for (int replica = 0; replica < 2; replica++) {
            try (ConfigurableApplicationContext started = Replica.launch(Map.of())) {
                awaitDeliveryAt(Replica.portOf(started));
                Set<String> now = new HashSet<>(wire.consumerGroups());
                now.removeAll(before);
                now.removeAll(added);
                // Группа у реплики ОДНА, и имя её своё на процесс.
                assertThat(now).hasSize(1);
                assertThat(now.iterator().next()).startsWith("bff-");
                added.addAll(now);
            }
        }
        assertThat(added).doesNotHaveDuplicates().hasSize(2);

        // Величин полноты приёма поверхность не отдаёт, а тика состояния
        // приёма в дереве нет: единственная джоба — пульс.
        authAnswersOneMembership();
        assertThat(get(PERIMETER + "/reception").errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(SchedulerCapacityContract.scheduledDeclarationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("B11.4 — К ярусу интеграции периметр не ходит")
    void b11_4_thePerimeterDoesNotGoToTheIntegrationTier() {
        owners.answersAnything("connector-okx", 200, "{\"from\": \"connector\"}");

        fullCycle("TB4");
        assertThat(owners.requests("connector-okx")).isEmpty();

        // Явный адрес браузера — одна пересылка, и ничего сверх неё.
        authAnswersOneMembership();
        get("/api/v1/connector-okx/instruments");
        assertThat(owners.requests("connector-okx")).hasSize(1);
    }

    @Test
    @DisplayName("B11.5 — Торговых решений периметр не принимает")
    void b11_5_thePerimeterTakesNoTradingDecisions() {
        String tenant = "TB5";
        authAnswers(Bodies.memberships(tenant, ROLE));
        String ticket = issuedTicket();
        owners.forgetRequests();

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b11-5-open")) {
            wire.publish(Wire.CORE_TOPIC, tenant, "e-b11-5-hold", "HOLD_RAISED",
                    "2026-09-20T11:00:00Z", Bodies.fullMessage("HOLD_RAISED", "b11-5"));
            wire.publish(Wire.CORE_TOPIC, tenant, "e-b11-5-anomaly", "ANOMALY_REPORTED",
                    "2026-09-20T11:00:00Z", Bodies.fullMessage("ANOMALY_REPORTED", "b11-5"));
            stream.awaitFrames(3);

            // Записи показаны — и только: ни одной команды владельцам, ни
            // одной публикации.
            assertThat(stream.types()).containsExactly("DEAL_OPENED", "HOLD_RAISED", "ANOMALY_REPORTED");
            assertThat(owners.count()).isZero();
            assertThat(recordsSinceStart()).isEqualTo(3L);
        }
    }

    @Test
    @DisplayName("B11.6 — Контракта «на экран» периметр не заводит")
    void b11_6_thePerimeterKeepsNoScreenContract() {
        authAnswers(Bodies.memberships("TB6", ROLE));
        String ownerForm = "{\"ownerField\": 1, \"nested\": {\"x\": [true]}}";
        owners.answersWith(OwnerStub.TRADING_CORE, "/api/v1/trading-core/deals", 200, "application/json", ownerForm);

        // Пересланное — формой владельца, байт в байт.
        assertThat(get("/api/v1/trading-core/deals").body()).isEqualTo(ownerForm);
        // Порождённое — формой периметра.
        assertThat(get(CONTEXT).asObject()).containsOnlyKeys("tenantId", "role");
        Answer issued = post(TICKETS, "");
        assertThat(issued.asObject()).containsOnlyKeys("ticket", "expiresAt");
        assertThat(getAnonymously(CONTEXT).carriesErrorDto()).isTrue();
        try (Subscription stream = openedStreamOf("TB6", String.valueOf(issued.asObject().get("ticket")),
                "e-b11-6")) {
            assertThat(stream.frames().getFirst().content()).containsOnlyKeys("id", "type", "occurredAt", "content");
        }
    }

    @Test
    @DisplayName("B11.7 — Членств периметр не пишет и своей копии не держит")
    void b11_7_thePerimeterNeitherWritesNorKeepsMemberships() {
        authAnswersOneMembership();

        for (int index = 0; index < 10; index++) {
            assertThat(getWith(CONTEXT, identity.tokenFor(subject + "-" + index)).status()).isEqualTo(200);
        }

        // К `auth` — только резолвы, по одному на субъекта, и ни одного
        // вызова сверх точки резолва.
        List<LoggedRequest> toAuth = owners.requests(OwnerStub.AUTH);
        assertThat(toAuth).hasSize(10);
        assertThat(toAuth).allSatisfy(request ->
                assertThat(OwnerStub.pathOf(request)).isEqualTo(OwnerStub.MEMBERSHIPS_PATH));

        // Копии, переживающей процесс, нет: после перезапуска тот же
        // субъект перечитывается у владельца.
        for (int replica = 0; replica < 2; replica++) {
            try (ConfigurableApplicationContext started = Replica.launch(Map.of())) {
                owners.forgetRequests();
                assertThat(getAt(Replica.portOf(started), CONTEXT, token()).status()).isEqualTo(200);
                assertThat(owners.requests(OwnerStub.AUTH)).hasSize(1);
            }
        }
    }

    @Test
    @DisplayName("B11.8 — Отказы источников периметр в один не сливает")
    void b11_8_thePerimeterDoesNotMergeSourceRefusals() {
        authAnswersOneMembership();
        owners.answersAnything(OwnerStub.STATISTICS, 200, "{\"from\": \"statistics\"}");
        owners.failsTransport(OwnerStub.AUDIT);

        Answer succeeded = get("/api/v1/statistics/summary");
        Answer refused = get("/api/v1/audit/journal");

        // Исход каждого — отдельно, своим статусом и телом.
        assertThat(succeeded.status()).isEqualTo(200);
        assertThat(succeeded.body()).isEqualTo("{\"from\": \"statistics\"}");
        assertThat(refused.errorCode()).isEqualTo(PEER_UNAVAILABLE);
        assertThat(refused.body()).doesNotContain("statistics");
    }

    /**
     * Полный цикл периметра на своём тенанте: контекст, билет, подписка,
     * приём записи, пересылка.
     *
     * @return сколько записей положила в темы сама клетка
     */
    private Long fullCycle(String tenant) {
        authAnswers(Bodies.memberships(tenant, ROLE));
        owners.answersAnything(OwnerStub.TRADING_CORE, 200, "{}");
        assertThat(get(CONTEXT).status()).isEqualTo(200);
        String ticket = issuedTicket();
        try (Subscription stream = openedStreamOf(tenant, ticket, "e-cycle-" + tenant)) {
            assertThat(stream.ids()).containsExactly("e-cycle-" + tenant);
        }
        assertThat(get("/api/v1/trading-core/deals").status()).isEqualTo(200);
        return 1L;
    }

    private static Set<String> declaredKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("declared", new ClassPathResource("application.yaml"))) {
            if (source.getSource() instanceof Map<?, ?> map) {
                map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            }
        }
        return keys;
    }

    private static Boolean isOnClasspath(String className) {
        try {
            Class.forName(className, false, BoundariesBoxTest.class.getClassLoader());
            return Boolean.TRUE;
        } catch (ClassNotFoundException absent) {
            return Boolean.FALSE;
        }
    }
}
