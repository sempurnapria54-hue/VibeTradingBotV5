package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} документа кейсов: открытие подписки и радиус тенанта
 * (.claude/tests/cases/bff.md §«B3 — Открытие подписки и радиус
 * тенанта»).
 *
 * <p><b>Здесь клетки, которым довольно ШТАТНОГО положения осей.</b>
 * Клетки, чей предмет — ПОТОЛОК подписок либо СРОК соединения, живут
 * своими классами ({@link SubscriptionCeilingBoxTest},
 * {@link ConnectionExpiryBoxTest}) и платят своим подъёмом: одно и то же
 * положение оси эти предметы выразить не могут.
 *
 * <p><b>Группа мерит то, что билет ОТКРЫВАЕТ, а не сам билет как
 * значение.</b> Подпись, состав и срок — предмет группы {@code B2}:
 * склеенная группа была бы зелена при билете, годном и на прокси.
 *
 * <p><b>Всякое утверждение «провод открылся» предъявляется ДОЕХАВШЕЙ
 * записью.</b> Заголовки ответа провода уходят клиенту вместе с первой
 * записью (находка F-11 документа кейсов), и открытый сокет сам по себе о
 * раздаче не говорит ничего.
 */
class StreamSubscriptionBoxTest extends SharedBffBox {

    /**
     * Момент происшествия записи клетки {@code B3.1}: назначенный, а не
     * текущий, — иначе равенство «момент записи равен заголовку конверта»
     * держалось бы совпадением часов.
     */
    private static final String OCCURRED_AT = "2026-09-20T10:15:30Z";

    @Test
    @DisplayName("B3.1 — Годный билет открывает поток, и факт тенанта доезжает")
    void b3_1_aValidTicketOpensTheStreamAndTheTenantFactArrives() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription stream = subscribe(ticket)) {
            wire.publish(Wire.CORE_TOPIC, TENANT, "e-b3-1", "DEAL_OPENED", OCCURRED_AT,
                    Bodies.dealOpened("D-e-b3-1"));
            stream.awaitFrames(1);

            assertThat(stream.status()).isEqualTo(200);
            assertThat(stream.carriesStream()).isTrue();
            assertThat(stream.frames()).hasSize(1);

            Subscription.Frame frame = stream.frames().getFirst();
            assertThat(frame.id()).isEqualTo("e-b3-1");
            assertThat(frame.type()).isEqualTo("DEAL_OPENED");

            Map<String, Object> record = frame.content();
            assertThat(record).containsEntry("id", "e-b3-1").containsEntry("type", "DEAL_OPENED");
            assertThat(OffsetDateTime.parse(String.valueOf(record.get("occurredAt"))).toInstant())
                    .isEqualTo(OffsetDateTime.parse(OCCURRED_AT).toInstant());
            // Содержимое — форма ПЕРИМЕТРА: доменного класса на проводе
            // нет, и компонент, формой не объявленный, не едет (состав
            // формы — предмет группы B7, здесь довольно её признака).
            assertThat(contentOf(record))
                    .containsEntry("dealInternalId", "D-e-b3-1")
                    .containsEntry("direction", "LONG");

            // Второй записи о том же событии нет: дедупа на сервере нет, и
            // один факт уезжает в провод ровно однажды.
            assertThat(stream.ids()).containsExactly("e-b3-1");
            assertThat(recordsSinceStart()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("B3.2 — Факт чужого тенанта в провод не идёт")
    void b3_2_aForeignTenantFactDoesNotEnterTheWire() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription stream = subscribe(ticket)) {
            publishDealOpened(SECOND_TENANT, "e-b3-2-foreign");
            // Вторая запись предъявлена НАМЕРЕННО: без неё «чужого факта
            // нет» было бы зелено и у мёртвого провода
            // (.claude/tests/cases/bff.md §«Новая ось формы»).
            publishDealOpened(TENANT, "e-b3-2-own");
            stream.awaitFrames(1);

            assertThat(stream.ids()).containsExactly("e-b3-2-own");
            assertThat(stream.frames()).hasSize(1);
            assertThat(stream.frames().getFirst().data()).doesNotContain("e-b3-2-foreign");
        }
    }

    @Test
    @DisplayName("B3.3 — Без билета подписка не открывается")
    void b3_3_withoutATicketNoSubscriptionOpens() {
        authAnswersOneMembership();

        try (Subscription refused = subscribeWithoutTicket()) {
            assertThat(refused.status()).isEqualTo(401);
            assertThat(refused.carriesStream()).isFalse();
            assertThat(refused.errorCode()).isEqualTo(UNAUTHENTICATED);
            // Соединение не удерживается открытым, и последующая запись в
            // него не уходит: отказ есть законченный документ.
            assertThat(refused.isOpen()).isFalse();
            publishDealOpened(TENANT, "e-b3-3");
            assertThat(refused.frames()).isEmpty();
        }
    }

    @Test
    @DisplayName("B3.4 — Тропа подписки вне bearer-цепочки, но открытой не становится")
    void b3_4_theStreamPathIsOutsideTheBearerChainYetNotOpen() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription byToken = subscribeBearing(token())) {
            // Годный токен на этой тропе добытчиком не является: контур её
            // пропускает, а проверку билета делает сама точка.
            assertThat(byToken.carriesStream()).isFalse();
            assertThat(byToken.frames()).isEmpty();
            // КЛАСС отказа здесь не утверждается, и это не послабление:
            // предмет клетки — что провод не открылся, а формат отказа —
            // предмет группы B9. Наблюдено: 500 с телом каркаса, без
            // нашего класса вовсе (находка F-12 документа кейсов: отказ на
            // тропе подписки не рендерится, потому что `produces` провода
            // не принимает JSON).
            assertThat(byToken.status()).isGreaterThanOrEqualTo(400);
        }

        // Обратная сторона того же свойства: билет БЕЗ токена провод
        // открывает — исключение из цепочки заменяет форму предъявления,
        // а не снимает её.
        try (Subscription byTicket = openedStream(ticket, "e-b3-4")) {
            assertThat(byTicket.status()).isEqualTo(200);
            assertThat(byTicket.carriesStream()).isTrue();
            assertThat(byTicket.ids()).containsExactly("e-b3-4");
        }
    }

    @Test
    @DisplayName("B3.8 — Оборванная подписка не лишает данных остальные")
    void b3_8_anAbortedSubscriptionDoesNotStarveTheOthers() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription surviving = openedStream(ticket, "e-b3-8-first")) {
            Subscription aborted = openedStream(ticket, "e-b3-8-second");
            surviving.awaitFrames(2);

            // Обрыв на стороне клиента — ушедший браузер: штатного
            // закрытия подписки в протоколе нет вовсе.
            aborted.close();
            publishDealOpened(TENANT, "e-b3-8-after");
            surviving.awaitFrames(3);

            assertThat(surviving.ids())
                    .containsExactly("e-b3-8-first", "e-b3-8-second", "e-b3-8-after");
            // Оборванная выбывает из набора: последующие записи в неё не
            // пишутся, а приём события не роняется — третья запись доехала.
            assertThat(aborted.ids()).containsExactly("e-b3-8-second");
            assertThat(recordsSinceStart()).isEqualTo(3L);
        }
    }

    /** Содержимое записи в форме периметра. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> contentOf(Map<String, Object> record) {
        return (Map<String, Object>) record.get("content");
    }
}
