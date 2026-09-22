package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка группы {@code B2}, чей предмет — НЕЗАДАННЫЙ секрет подписи
 * билета (.claude/tests/cases/bff.md, {@code B2.7}).
 *
 * <p><b>Свой контекст, потому что предмет и есть положение оси.</b>
 * Пустой секрет означает, что выдача билетов не настроена, — и тогда
 * подписка не открывается вовсе: незаданное есть отказ, а не разрешение
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Обе операции здесь несущие.</b> Отказ только у выдачи оставил бы
 * открытой тропу подписки — пустая подпись сошлась бы с пустой подписью,
 * и билет, собранный кем угодно, прошёл бы проверку.
 */
class UnconfiguredTicketSecretBoxTest extends BffBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(BffSubstrate.TICKET_SECRET_KEY, ""));
    }

    @Test
    @DisplayName("B2.7 — Незаданный секрет есть отказ, а не разрешение")
    void b2_7_anUnsetSecretIsARefusalAndNotAPermission() {
        authAnswersOneMembership();

        Answer issuance = post(TICKETS, "");

        assertThat(issuance.status()).isEqualTo(401);
        assertThat(issuance.carriesErrorDto()).isTrue();
        assertThat(issuance.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(issuance.body()).doesNotContain("ticket");

        try (Subscription withTicket = subscribe("any-value-at-all");
             Subscription withoutTicket = subscribeWithoutTicket()) {

            assertThat(withTicket.status()).isEqualTo(401);
            assertThat(withTicket.carriesStream()).isFalse();
            assertThat(withTicket.errorCode()).isEqualTo(UNAUTHENTICATED);
            assertThat(withoutTicket.status()).isEqualTo(401);
            assertThat(withoutTicket.carriesStream()).isFalse();
            assertThat(withoutTicket.errorCode()).isEqualTo(UNAUTHENTICATED);
        }
    }
}
