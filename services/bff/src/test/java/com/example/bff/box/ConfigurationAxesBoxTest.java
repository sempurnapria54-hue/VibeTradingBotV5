package com.example.bff.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.testsupport.SchedulerCapacityContract;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Группа {@code B10} документа кейсов: конфигурация как вход
 * (.claude/tests/cases/bff.md §«B10 — Конфигурация как вход»). Клетка о
 * неподъёме контекста живёт без {@code @SpringBootTest}
 * ({@link UnconfiguredAccessContourTest}).
 *
 * <p><b>Объявление осей читается с носителя умолчаний, и это не обход
 * формы ящика:</b> «величина не зашита в код» есть утверждение об
 * объявлении, а контекст ящика все оси переопределяет — прочитанные у него
 * значения говорили бы о прогоне. Поведенческая сторона каждой оси
 * предъявлена клетками групп выше, где ось сдвинута своим контекстом.
 */
class ConfigurationAxesBoxTest extends SharedBffBox {

    /** Носитель объявленных умолчаний сервиса. */
    private static final String DECLARATION = "application.yaml";

    /** Оси окружения периметра — ключи конфигурации, объявленные плейсхолдерами. */
    private static final List<String> AXES = List.of(
            BffSubstrate.OWNER_URL_TEMPLATE_KEY, BffSubstrate.READ_RETRIES_KEY,
            BffSubstrate.MEMBERSHIP_CACHE_TTL_KEY, BffSubstrate.STREAM_TOPICS_KEY,
            BffSubstrate.REPLAY_WINDOW_KEY, BffSubstrate.PULSE_INTERVAL_KEY,
            BffSubstrate.PULSE_ENABLED_KEY, BffSubstrate.CONNECTION_TIMEOUT_KEY,
            BffSubstrate.MAX_SUBSCRIPTIONS_KEY, BffSubstrate.TICKET_SECRET_KEY,
            BffSubstrate.TICKET_TTL_KEY);

    /** Плейсхолдер переменной окружения с умолчанием либо без него. */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([A-Z0-9_]+)(?::(.*))?}$");

    @Test
    @DisplayName("B10.2 — Подпись токена проверяется локально по ключам провайдера")
    void b10_2_theTokenSignatureIsVerifiedLocally() {
        authAnswersOneMembership();
        Integer before = identity.paths().size();

        for (int request = 0; request < 10; request++) {
            assertThat(getWith(CONTEXT, identity.anotherTokenFor(subject)).status()).isEqualTo(200);
        }

        // Десяток ГОДНЫХ токенов не прибавил ни одного обращения к
        // провайдеру: набор ключей уже добыт, подпись проверяется на месте
        // (мерится ростом, а не нулём — ловушка TC-075).
        assertThat(identity.paths().subList(before, identity.paths().size())).isEmpty();
        // К `auth` — только за членствами, и одним запросом: подписи он не
        // проверяет.
        assertThat(owners.requests(OwnerStub.AUTH)).hasSize(1);
        assertThat(OwnerStub.pathOf(owners.requests(OwnerStub.AUTH).getFirst()))
                .isEqualTo(OwnerStub.MEMBERSHIPS_PATH);

        // Чужой ключ отвергается без вызова `auth`; к провайдеру уходит
        // разве что добыча ключей — незнакомый `kid` перечитывает их.
        owners.forgetRequests();
        Integer beforeForeign = identity.paths().size();
        assertThat(getWith(CONTEXT, identity.foreignKeyToken()).status()).isEqualTo(401);
        assertThat(owners.count()).isZero();
        assertThat(identity.paths().subList(beforeForeign, identity.paths().size()))
                .isSubsetOf(IdentityStub.keyRetrievalPaths());
    }

    @Test
    @DisplayName("B10.3 — Выдающая сторона токена сверяется")
    void b10_3_theTokenIssuerIsChecked() {
        authAnswersOneMembership();

        Answer foreignIssuer = getWith(CONTEXT, identity.foreignIssuerToken());
        Answer ownIssuer = get(CONTEXT);

        assertThat(foreignIssuer.status()).isEqualTo(401);
        assertThat(foreignIssuer.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(ownIssuer.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("B10.4 — Оси окружения приезжают конфигурацией, а не хардкодом")
    void b10_4_environmentAxesArriveAsConfiguration() throws IOException {
        Map<String, String> declared = declaredValues();

        // Каждая ось объявлена плейсхолдером переменной окружения.
        assertThat(AXES).allSatisfy(axis -> {
            assertThat(declared).as(axis).containsKey(axis);
            assertThat(PLACEHOLDER.matcher(declared.get(axis)).matches())
                    .as("%s объявлена значением %s, а не переменной окружения", axis, declared.get(axis))
                    .isTrue();
        });
        // Секрет билета без умолчания: незаданное — отказ выдачи
        // (клетка B2.7), а не подпись пустым ключом.
        assertThat(defaultOf(declared.get(BffSubstrate.TICKET_SECRET_KEY))).isEmpty();
    }

    @Test
    @DisplayName("B10.5 — Размер пула планировщика осью окружения НЕ является")
    void b10_5_theSchedulerPoolSizeIsNotAnEnvironmentAxis() throws IOException {
        String declared = SchedulerCapacityContract.declaredPoolSizeValue();

        assertThat(declared).doesNotContain("${");
        assertThat(declared).matches("\\d+");
    }

    @Test
    @Tag("debt")
    @DisplayName("B10.6 — Перечень тем подписки — ось окружения, и пустой он подписки не даёт")
    void b10_6_theTopicListIsAnAxisAndEmptyGivesNoSubscription() {
        // Одна тема: записи второй в провод не уходят.
        try (Replica single = Replica.delivering(this, Map.of(BffSubstrate.STREAM_TOPICS_KEY, Wire.CORE_TOPIC))) {
            String ticket = ticketAt(single, "TA6");
            try (Subscription stream = openedStreamAt(single.port(), "TA6", ticket, "e-b10-6-core")) {
                wire.publish(Wire.STRATEGIES_TOPIC, "TA6", "e-b10-6-strategy", "STRATEGY_DELETED",
                        "2026-09-20T11:00:00Z", Bodies.fullMessage("STRATEGY_DELETED", "b10-6"));
                publishDealOpened("TA6", "e-b10-6-barrier");
                stream.awaitFrames(2);
                assertThat(stream.ids()).containsExactly("e-b10-6-core", "e-b10-6-barrier");
            }
        }

        // Пустой перечень: поверхность поднимается и отвечает, подписки нет
        // ни на одну тему. Сегодня красно: пустое значение разбирается как
        // одна тема с пустым именем, и слушатель заводится и отказывает
        // (находка F-8 документа кейсов).
        // Зонда назначения у этой реплики нет: слушать ей нечего, и зонд
        // ждал бы записи, которой не доехать.
        try (ConfigurableApplicationContext empty = Replica.launch(Map.of(BffSubstrate.STREAM_TOPICS_KEY, ""))) {
            authAnswersOneMembership();
            assertThat(getAt(Replica.portOf(empty), CONTEXT, token()).status()).isEqualTo(200);
        }
    }

    /** Билет субъекта клетки у реплики, чьё единственное членство — названный тенант. */
    private String ticketAt(Replica replica, String tenant) {
        authAnswers(Bodies.memberships(tenant, ROLE));
        return issuedTicketAt(replica.port(), token());
    }

    /** Объявленные значения носителя умолчаний — до подстановки переменных. */
    private static Map<String, String> declaredValues() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("declared", new ClassPathResource(DECLARATION))) {
            if (source.getSource() instanceof Map<?, ?> map) {
                map.forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
            }
        }
        return values;
    }

    /** Умолчание плейсхолдера; пусто — умолчания нет либо оно пустое. */
    private static String defaultOf(String placeholder) {
        Matcher matcher = PLACEHOLDER.matcher(placeholder);
        if (isFalse(matcher.matches())) {
            throw new AssertionError("Значение " + placeholder + " — не плейсхолдер");
        }
        return Objects.isNull(matcher.group(2)) ? "" : matcher.group(2);
    }
}
