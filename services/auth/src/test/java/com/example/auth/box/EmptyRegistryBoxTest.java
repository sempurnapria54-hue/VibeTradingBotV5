package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Пустой реестр — клетка {@code B3.2} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Контейнер базы у кейса СВОЙ по ВТОРОМУ основанию: предмет кейса
 * есть состояние таблицы ЦЕЛИКОМ.</b> Разностная форма такой предмет не
 * выражает вовсе — «строк не прибавилось» истинно и на непустом реестре,
 * а проверяется ровно пустой; на общем контейнере клетка краснела бы от
 * соседа, прошедшего раньше
 * (.claude/decisions/test-contour-design-pass.md §«Оснований брать свой
 * контейнер ДВА, и второе не выводится из первого…»).
 *
 * <p><b>Что мерится:</b> ядро обязано отличить «счетов нет» от «реестр не
 * ответил» — пустота выборки есть значение, а не ошибка
 * (`docs/rules/absent-value-semantics.md`).
 */
class EmptyRegistryBoxTest extends AuthBox {

    private static final PostgreSQLContainer OWN_DATABASE = startEmptyDatabase();

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of(
                "spring.datasource.url", OWN_DATABASE.getJdbcUrl(),
                "spring.datasource.username", OWN_DATABASE.getUsername(),
                "spring.datasource.password", OWN_DATABASE.getPassword()));
    }

    @Test
    @DisplayName("B3.2 — пустой реестр — пустой перечень, а не отказ")
    void b3_2_anEmptyRegistryIsAnEmptyListAndNotARefusal() {
        assertThat(Rows.of(OWN_DATABASE).count("exchange_accounts")).isZero();

        Answer answer = get(EXCHANGE_ACCOUNTS, identity.browserToken("user-b3-2"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).isEmpty();
    }

    private static PostgreSQLContainer startEmptyDatabase() {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(AuthSubstrate.DATABASE_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("auth")
                .withUsername("auth")
                .withPassword("auth");
        container.start();
        return container;
    }
}
