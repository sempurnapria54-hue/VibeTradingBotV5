package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка группы {@code B3}, чей предмет — СРОК ЖИЗНИ соединения подписки
 * (.claude/tests/cases/bff.md, {@code B3.9}).
 *
 * <p><b>Свой контекст, потому что сдвинуты ДВЕ оси, и обе несущие.</b>
 * Срок соединения обязан истечь внутри клетки — при штатных тридцати
 * минутах предмет не наблюдаем вовсе. Потолок подписок при этом задан
 * ЕДИНИЦЕЙ: без него «место под потолком освободилось» не наблюдается
 * ничем — при штатных тридцати двух следующая подписка открылась бы
 * независимо от того, освободилось место или нет.
 *
 * <p><b>Закрытие по сроку — ШТАТНЫЙ конец, и различает его не факт
 * закрытия, а его форма:</b> ответ провода имеет свой тип содержимого и
 * свой код, а записи об ошибке в проводе нет ни одной. Клетка,
 * утверждающая только «соединение закрылось», была бы зелена и у обрыва
 * отказом.
 */
class ConnectionExpiryBoxTest extends BffBox {

    /** Срок соединения этого контекста: он обязан истечь внутри клетки. */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(3);

    /** Потолок подписок: единица делает освобождение места наблюдаемым. */
    private static final Integer CEILING = 1;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(
                BffSubstrate.CONNECTION_TIMEOUT_KEY, CONNECTION_TIMEOUT.toMillis() + "ms",
                BffSubstrate.MAX_SUBSCRIPTIONS_KEY, String.valueOf(CEILING)));
    }

    @Test
    @Tag("debt")
    @DisplayName("B3.9 — Соединение закрывается по своему сроку штатно")
    void b3_9_theConnectionIsClosedOnItsOwnTermGracefully() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription abandoned = subscribe(ticket)) {
            abandoned.awaitClosed(CONNECTION_TIMEOUT.plusSeconds(20));

            // Закрытие штатное: тип содержимого — провод, код — успех, и
            // ни одной записи об ошибке в провод не ушло. Сегодня красно:
            // наблюдено 401 — тик по сроку снимает подписку из набора, но
            // сам emitter не завершает, и каркас отвечает отказом по
            // таймауту (находка F-13 документа кейсов).
            assertThat(abandoned.status()).isEqualTo(200);
            assertThat(abandoned.contentType()).startsWith("text/event-stream");
            assertThat(abandoned.frames()).isEmpty();
            assertThat(abandoned.isOpen()).isFalse();
        }

        // Место под потолком освободилось: следующая подписка открывается
        // тем же билетом — то самое, что делает браузерное переподключение.
        try (Subscription reopened = openedStream(ticket, "e-b3-9")) {
            assertThat(reopened.status()).isEqualTo(200);
            assertThat(reopened.carriesStream()).isTrue();
            assertThat(reopened.ids()).containsExactly("e-b3-9");
        }
    }
}
