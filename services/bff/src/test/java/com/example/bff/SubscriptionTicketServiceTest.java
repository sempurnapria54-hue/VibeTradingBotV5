package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.SubscriptionTicket;
import com.example.bff.domain.SubscriptionTicketService;
import com.example.bff.domain.TicketRejectedException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Билет подписки: вторая форма предъявления на тропе потока
 * (docs/architecture/contracts.md §«Подписку открывает билет, а не сам
 * токен»).
 *
 * <p>Проверяется то, на чём стои́т конструкция: билет собирается и
 * разбирается без таблицы, испорченный не проходит, просроченный не
 * проходит, а ненастроенная выдача отвечает отказом, а не открывает
 * подписку.
 */
class SubscriptionTicketServiceTest {

    private static final String SUBJECT = "user-42";
    private static final String TENANT = "tenant-7";

    @Test
    @DisplayName("Выданный билет разбирается в того же субъекта и тот же тенант")
    void anIssuedTicketVerifiesBackToItsSubjectAndTenant() {
        SubscriptionTicketService service = serviceWith("secret-of-replicas", Duration.ofMinutes(10));

        SubscriptionTicket verified = service.verify(service.issue(SUBJECT, TENANT));

        assertThat(verified.subject()).isEqualTo(SUBJECT);
        assertThat(verified.tenantId()).isEqualTo(TENANT);
    }

    /**
     * Подпись — единственное, чем билет держится: таблицы выданных нет,
     * и подделанное значение обязано отвергаться самой проверкой.
     */
    @Test
    @DisplayName("Билет с подменённым значением не проходит")
    void aTamperedTicketIsRejected() {
        SubscriptionTicketService service = serviceWith("secret-of-replicas", Duration.ofMinutes(10));
        String issued = service.issue(SUBJECT, TENANT);
        String tampered = "x" + issued.substring(1);

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(TicketRejectedException.class);
    }

    /**
     * Билет, выданный на чужом секрете, не проходит: секрет общий у
     * реплик, и подпись — то, чем реплика узнаёт свой билет.
     */
    @Test
    @DisplayName("Билет чужого секрета не проходит")
    void aTicketOfAForeignSecretIsRejected() {
        String issued = serviceWith("foreign-secret", Duration.ofMinutes(10)).issue(SUBJECT, TENANT);

        assertThatThrownBy(() -> serviceWith("secret-of-replicas", Duration.ofMinutes(10)).verify(issued))
                .isInstanceOf(TicketRejectedException.class);
    }

    /**
     * Срок проверяется при открытии подписки. Просроченный билет
     * отвергается — иначе срок не был бы сроком.
     */
    @Test
    @DisplayName("Просроченный билет не проходит")
    void anExpiredTicketIsRejected() {
        SubscriptionTicketService service = serviceWith("secret-of-replicas", Duration.ofSeconds(-1));
        String issued = service.issue(SUBJECT, TENANT);

        assertThatThrownBy(() -> service.verify(issued))
                .isInstanceOf(TicketRejectedException.class);
    }

    /**
     * Незаданный секрет означает, что выдача не настроена, — и тогда
     * подписка не открывается вовсе: незаданное есть отказ, а не
     * разрешение.
     */
    @Test
    @DisplayName("Ненастроенная выдача отвечает отказом на обе операции")
    void anUnconfiguredSecretRefusesBothOperations() {
        SubscriptionTicketService service = serviceWith("", Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.issue(SUBJECT, TENANT))
                .isInstanceOf(TicketRejectedException.class);
        assertThatThrownBy(() -> service.verify("anything"))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Пустого билета не бывает: подписку он не открывает. */
    @Test
    @DisplayName("Непредъявленный билет не проходит")
    void anAbsentTicketIsRejected() {
        SubscriptionTicketService service = serviceWith("secret-of-replicas", Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.verify(null))
                .isInstanceOf(TicketRejectedException.class);
    }

    private SubscriptionTicketService serviceWith(String secret, Duration ttl) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.getTicket().setSecret(secret);
        properties.getTicket().setTtl(ttl);
        return new SubscriptionTicketService(properties);
    }
}
