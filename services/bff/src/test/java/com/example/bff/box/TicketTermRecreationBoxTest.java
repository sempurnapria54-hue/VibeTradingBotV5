package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетка группы {@code B3}: плановое закрытие подписки за сроком билета
 * (.claude/tests/cases/bff.md, {@code B3.10}).
 *
 * <p><b>Срок соединения длиннее срока билета, и это объявленное решение,
 * а не расхождение</b> (docs/architecture/contracts.md §«Подписку
 * открывает билет, а не сам токен»): переподключение после планового
 * закрытия предъявляет истёкший билет и получает отказ, а поток
 * продолжается ПЕРЕСОЗДАНИЕМ — новым билетом и идентичностью последнего
 * события, переданной клиентом. Клетка предъявляет обе половины: отказ
 * старому билету и продолжение без разрыва — окно тенанта пережило его
 * последнюю подписку.
 *
 * <p><b>Реплика своя:</b> оба срока сдвинуты до секунд, чтобы плановое
 * закрытие наступило внутри клетки, а срок билета истёк раньше него.
 */
class TicketTermRecreationBoxTest extends SharedBffBox {

    /** Срок билета реплики: истекает раньше планового закрытия. */
    private static final Duration TICKET_TTL = Duration.ofSeconds(5);

    /** Срок соединения реплики: плановое закрытие за сроком билета. */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(8);

    /** Класс записи разрыва. */
    private static final String GAP = "PERIMETER_GAP";

    /** Тенант клетки. */
    private static final String TENANT = "TR10";

    @Test
    @DisplayName("B3.10 — Плановое закрытие за сроком билета продолжается пересозданием подписки")
    void b3_10_aPlannedCloseBeyondTheTicketTermContinuesByRecreation() {
        try (Replica replica = Replica.delivering(this, Map.of(
                BffSubstrate.TICKET_TTL_KEY, TICKET_TTL.toMillis() + "ms",
                BffSubstrate.CONNECTION_TIMEOUT_KEY, CONNECTION_TIMEOUT.toMillis() + "ms"))) {
            authAnswers(Bodies.memberships(TENANT, ROLE));
            String ticket = issuedTicketAt(replica.port(), token());
            try (Subscription planned = openedStreamAt(replica.port(), TENANT, ticket, "e-b3-10-1")) {
                planned.awaitClosed(CONNECTION_TIMEOUT.plusSeconds(20));
                assertThat(planned.status()).isEqualTo(200);
            }
            publishDealOpened(TENANT, "e-b3-10-2");

            // Переподключение тем же адресом: билет истёк раньше планового
            // закрытия, и открытие отвечает отказом.
            try (Subscription sameTicket = subscribeAt(replica.port(), ticket, "e-b3-10-1")) {
                assertThat(sameTicket.status()).isEqualTo(401);
            }

            // Пересоздание: новый билет и позиция, переданная клиентом, —
            // поток продолжается без разрыва, окно подписку пережило.
            String fresh = issuedTicketAt(replica.port(), token());
            try (Subscription recreated = subscribeAt(replica.port(), fresh, "e-b3-10-1")) {
                recreated.awaitFrames(1);
                assertThat(recreated.types()).doesNotContain(GAP);
                assertThat(recreated.ids()).containsExactly("e-b3-10-2");
            }
        }
    }
}
