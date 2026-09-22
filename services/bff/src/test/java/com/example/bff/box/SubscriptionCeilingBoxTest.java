package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки группы {@code B3}, чей предмет — ПОТОЛОК одновременных подписок
 * тенанта (.claude/tests/cases/bff.md, {@code B3.5}-{@code B3.7}).
 *
 * <p><b>Свой контекст, потому что сдвинута ось.</b> Штатный потолок
 * прогона — 32 подписки, и исчерпать его значило бы открыть тридцать три
 * соединения ради одного ожидания; здесь он задан малым числом, и
 * исчерпание становится входом, а не нагрузкой.
 *
 * <p><b>Обе стороны границы утверждаются в одном теле.</b> Потолок мерится
 * не отказом сверх него, а ПАРОЙ: ровно столько, сколько допущено, —
 * открывается и получает записи; следующая — отвергается. Клетка,
 * стоящая на одной стороне, была бы зелена и у потолка, закрывающего
 * первую же подписку (пробел G4 документа кейсов).
 *
 * <p><b>Число ответа пишется в «Факт», а не в ассерт.</b> Отказ потолка
 * бросает наш собственный код, и набор HTTP-кодов поверхности — вопрос
 * без ответа (.claude/tests/cases/bff.md §«Число ответа и класс отказа —
 * разные ожидания»); ожидание стои́т на классе отказа и на поводе,
 * который называет его текст.
 *
 * <p><b>Тенант у каждой клетки СВОЙ, и это следствие наблюдённого:</b>
 * место под потолком освобождается не в момент закрытия подписки
 * клиентом, а на ПЕРВОЙ следующей записи тенанта — сервер узнаёт об
 * ушедшем клиенте, только когда пишет в его провод. Клетки общего тенанта
 * поэтому наследовали бы друг другу подписки, которых сервер ещё не
 * заметил закрытыми, и вторая по порядку упиралась бы в потолок на первом
 * же открытии. В проде эту уборку делает пульс, идущий раз в такт; в
 * прогоне его такт длиннее самого прогона (см. {@link BffSubstrate}).
 */
class SubscriptionCeilingBoxTest extends BffBox {

    /** Потолок подписок этого контекста: он обязан исчерпаться внутри клетки. */
    private static final Integer CEILING = 2;

    /** Тенант клетки о самом потолке. */
    private static final String CEILING_TENANT = "TC5";

    /** Тенант клетки о радиусе счёта — первый. */
    private static final String RADIUS_TENANT = "TC6";

    /** Тенант клетки о радиусе счёта — второй, чей потолок считается отдельно. */
    private static final String SECOND_RADIUS_TENANT = "TC6B";

    /** Тенант клетки об освобождении места. */
    private static final String RELEASE_TENANT = "TC7";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(
                BffSubstrate.MAX_SUBSCRIPTIONS_KEY, String.valueOf(CEILING)));
    }

    @Test
    @Tag("debt")
    @DisplayName("B3.5 — Потолок подписок тенанта: сверх него — отказ «слишком много запросов»")
    void b3_5_beyondTheCeilingTheOpeningIsRefused() {
        authAnswers(Bodies.memberships(CEILING_TENANT, ROLE));
        String ticket = issuedTicket();

        // Каждая подписка предъявляется СВОЕЙ записью: открытие
        // асинхронно, и запись, положенная до регистрации, не приходит
        // никуда (см. шапку openedStreamOf).
        try (Subscription first = openedStreamOf(CEILING_TENANT, ticket, "e-b3-5-first");
             Subscription second = openedStreamOf(CEILING_TENANT, ticket, "e-b3-5-second")) {
            first.awaitFrames(2);
            // Сторона границы, на которой потолок ещё не исчерпан: ровно
            // столько подписок, сколько допущено, открыто и получает записи.
            assertThat(first.ids()).containsExactly("e-b3-5-first", "e-b3-5-second");
            assertThat(second.ids()).containsExactly("e-b3-5-second");

            try (Subscription beyond = subscribe(ticket)) {
                assertThat(beyond.carriesStream()).isFalse();
                assertThat(beyond.carriesErrorDto()).isTrue();
                // Отказ — часть контракта, а не отказ доступа: класс
                // отличается от отказа контура. Сегодня красно: наблюдено
                // ACCESS_UNAUTHENTICATED — отказ точки подписки не
                // рендерится вовсе, потому что `produces` провода не
                // принимает JSON единого error-DTO (находка F-12
                // документа кейсов), и клиент получает отказ КОНТУРА с
                // тропы обработки ошибки.
                assertThat(beyond.errorCode()).isEqualTo(REQUEST_REJECTED);
                assertThat(beyond.errorCode()).isNotEqualTo(UNAUTHENTICATED);
                assertThat(beyond.errorMessage()).contains("подписок тенанта больше");
            }

            // Ранее открытые не рвутся: следующая запись доезжает в обе.
            publishDealOpened(CEILING_TENANT, "e-b3-5-third");
            first.awaitFrames(3);
            second.awaitFrames(2);
            assertThat(first.ids())
                    .containsExactly("e-b3-5-first", "e-b3-5-second", "e-b3-5-third");
            assertThat(second.ids()).containsExactly("e-b3-5-second", "e-b3-5-third");
        }
    }

    @Test
    @DisplayName("B3.6 — Потолок считается по тенанту, а не по субъекту и не по процессу")
    void b3_6_theCeilingIsCountedPerTenant() {
        authAnswers(Bodies.memberships(RADIUS_TENANT, ROLE));
        String ownTicket = issuedTicket();
        // Тенант билета выводится из членств ПРЕДЪЯВИТЕЛЯ, поэтому билет
        // второго тенанта берётся вторым субъектом: своего тенанта клиент
        // не называет ни в одном поле.
        authAnswers(Bodies.memberships(SECOND_RADIUS_TENANT, ROLE));
        String foreignTicket = issuedTicketWith(identity.tokenFor(secondSubject));

        try (Subscription first = openedStreamOf(RADIUS_TENANT, ownTicket, "e-b3-6-first");
             Subscription second = openedStreamOf(RADIUS_TENANT, ownTicket, "e-b3-6-second");
             Subscription ofAnotherTenant =
                     openedStreamOf(SECOND_RADIUS_TENANT, foreignTicket, "e-b3-6-foreign")) {

            // Потолок первого тенанта ИСЧЕРПАН, и это половина входа:
            // без неё «счёт ведётся по тенанту» было бы верно и у
            // потолка, не считающего вовсе. Класс отказа здесь не
            // утверждается — он предмет клетки B3.5.
            try (Subscription beyondOwn = subscribe(ownTicket)) {
                assertThat(beyondOwn.carriesStream()).isFalse();
            }

            // Исчерпание одного тенанта не закрывает поток другому: счёт
            // ведётся по тенанту, а не по процессу.
            assertThat(ofAnotherTenant.status()).isEqualTo(200);
            assertThat(ofAnotherTenant.carriesStream()).isTrue();
            assertThat(ofAnotherTenant.ids()).containsExactly("e-b3-6-foreign");
            // И не по субъекту тоже: обе подписки первого тенанта открыты
            // ОДНИМ субъектом, и потолок его не различает.
            first.awaitFrames(2);
            assertThat(first.ids()).containsExactly("e-b3-6-first", "e-b3-6-second");
            assertThat(second.ids()).containsExactly("e-b3-6-second");
        }
    }

    @Test
    @DisplayName("B3.7 — Закрытая подписка освобождает место под потолком")
    void b3_7_aClosedSubscriptionFreesItsPlace() {
        authAnswers(Bodies.memberships(RELEASE_TENANT, ROLE));
        String ticket = issuedTicket();

        try (Subscription surviving = openedStreamOf(RELEASE_TENANT, ticket, "e-b3-7-first")) {
            Subscription closed = openedStreamOf(RELEASE_TENANT, ticket, "e-b3-7-second");
            surviving.awaitFrames(2);

            closed.close();
            // Закрытая в рассылку больше не входит, и отказ записи в неё
            // не роняет рассылку остальным: третья запись доезжает. Ею же
            // сервер и УЗНАЁТ о закрытии — место освобождается на первой
            // следующей записи тенанта, а не в момент закрытия.
            publishDealOpened(RELEASE_TENANT, "e-b3-7-third");
            surviving.awaitFrames(3);
            assertThat(surviving.ids())
                    .containsExactly("e-b3-7-first", "e-b3-7-second", "e-b3-7-third");
            assertThat(closed.ids()).containsExactly("e-b3-7-second");

            try (Subscription opened = openedStreamOf(RELEASE_TENANT, ticket, "e-b3-7-fourth")) {
                assertThat(opened.status()).isEqualTo(200);
                assertThat(opened.carriesStream()).isTrue();
                assertThat(opened.ids()).containsExactly("e-b3-7-fourth");
            }
            surviving.awaitFrames(4);
            assertThat(surviving.ids()).containsExactly("e-b3-7-first", "e-b3-7-second",
                    "e-b3-7-third", "e-b3-7-fourth");
        }
    }
}
