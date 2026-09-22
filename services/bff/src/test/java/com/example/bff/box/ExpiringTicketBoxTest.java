package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки, чей предмет — ИСТЕЧЕНИЕ срока билета
 * (.claude/tests/cases/bff.md, {@code B2.5} и {@code B3.11}).
 *
 * <p><b>Свой контекст, потому что сдвинута ось.</b> У соседних клеток
 * срок билета заведомо больше их длительности — это их предусловие, — а
 * здесь он обязан истечь внутри клетки.
 *
 * <p><b>Клетка утверждает ДВЕ стороны одного свойства.</b> Просроченный
 * билет не открывает новой подписки — и он же не рвёт уже
 * установленной: проверка стои́т на открытии, а не на каждой записи.
 * Одна половина без другой оставила бы предмет непроверенным: живой
 * поток, обрывающийся по таймеру, прошёл бы первую и провалил бы вторую.
 *
 * <p><b>Две клетки одной оси, и предметы у них РАЗНЫЕ.</b> {@code B2.5}
 * мерит сам билет как значение — что негодным его делает срок; {@code
 * B3.11} мерит, ЧТО ПРИ ЭТОМ ПРОИСХОДИТ С ПОТОКОМ: установленный не
 * рвётся, новый не открывается, а названная домом обязанность клиента —
 * взять новый билет — исполнима. Третья половина у {@code B2.5}
 * отсутствует, и без неё «новая подписка не открывается» было бы зелено
 * и у периметра, переставшего выдавать билеты вовсе.
 */
class ExpiringTicketBoxTest extends BffBox {

    /** Срок билета этого контекста: он обязан истечь внутри клетки. */
    private static final Duration TICKET_TTL = Duration.ofSeconds(2);

    /** Пауза: заведомо больше срока, но не настолько, чтобы прогон стоил минут. */
    private static final Duration PAUSE = TICKET_TTL.plusMillis(700);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(
                BffSubstrate.TICKET_TTL_KEY, TICKET_TTL.toMillis() + "ms"));
    }

    @Test
    @DisplayName("B2.5 — Просроченный билет отвергается при ОТКРЫТИИ")
    void b2_5_anExpiredTicketIsRejectedOnOpening() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription established = openedStream(ticket, "e-before-expiry")) {
            assertThat(established.carriesStream()).isTrue();
            pause();

            try (Subscription refused = subscribe(ticket)) {
                assertThat(refused.status()).isEqualTo(401);
                assertThat(refused.carriesStream()).isFalse();
                assertThat(refused.errorCode()).isEqualTo(UNAUTHENTICATED);
            }

            // Установленная подписка не порвана, и предъявляется это
            // ДОЕХАВШЕЙ записью, а не признаком живости соединения:
            // открытый сокет сам по себе о раздаче не говорит ничего.
            assertThat(established.isOpen()).isTrue();
            publishDealOpened(TENANT, "e-after-expiry");
            established.awaitFrames(2);

            assertThat(established.ids()).containsExactly("e-before-expiry", "e-after-expiry");
        }
    }

    @Test
    @DisplayName("B3.11 — Просроченный билет закрытую подписку не воскрешает и открытую не рвёт")
    void b3_11_anExpiredTicketNeitherRevivesNorTearsDown() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription established = openedStream(ticket, "e-b3-11-before")) {
            pause();
            publishDealOpened(TENANT, "e-b3-11-after");
            established.awaitFrames(2);

            // Открытая подписка запись получает: проверка стои́т на
            // открытии, и живой поток по таймеру не рвётся.
            assertThat(established.isOpen()).isTrue();
            assertThat(established.ids()).containsExactly("e-b3-11-before", "e-b3-11-after");

            try (Subscription refused = subscribe(ticket)) {
                assertThat(refused.carriesStream()).isFalse();
                assertThat(refused.errorCode()).isEqualTo(UNAUTHENTICATED);
            }

            // Обязанность клиента, названная домом, исполнима: новый билет
            // открывает поток тем же адресом.
            String renewed = issuedTicket();
            try (Subscription reopened = openedStream(renewed, "e-b3-11-renewed")) {
                assertThat(reopened.status()).isEqualTo(200);
                assertThat(reopened.carriesStream()).isTrue();
                assertThat(reopened.ids()).containsExactly("e-b3-11-renewed");
            }
        }
    }

    /** Пауза длиннее объявленного срока билета. */
    private static void pause() {
        Awaitility.await()
                .pollDelay(PAUSE)
                .atMost(PAUSE.plusSeconds(5))
                .until(() -> Boolean.TRUE);
    }
}
