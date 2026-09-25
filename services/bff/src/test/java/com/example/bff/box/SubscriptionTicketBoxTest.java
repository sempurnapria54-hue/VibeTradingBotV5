package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B2} документа кейсов: билет подписки как ЗНАЧЕНИЕ —
 * подпись, состав, срок (.claude/tests/cases/bff.md §«B2 — Билет
 * подписки как значение»).
 *
 * <p><b>Группа мерит сам билет, а не то, что он открывает.</b> Что
 * предъявление им открывает ровно тропу потока и ничего сверх неё —
 * предмет группы {@code B3}: склеенная группа была бы зелена при
 * билете, годном и на прокси.
 *
 * <p><b>Сторонний сборщик билета — вход, а не обход.</b> Секрет подписи
 * общий у реплик, и реплика, билета не выдававшая, обязана его принять;
 * в одном контексте эту сторону играет прогон ({@link Tickets}).
 */
class SubscriptionTicketBoxTest extends SharedBffBox {

    /** Срок билета штатного прогона: он же значение оси конфигурации. */
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);

    /** Допуск на длительность самого кейса при сверке момента негодности. */
    private static final Duration SLACK = Duration.ofMinutes(1);

    @Test
    @DisplayName("B2.1 — Выдача под токеном отдаёт билет и момент его негодности")
    void b2_1_theIssuanceReturnsATicketAndItsExpiry() {
        authAnswersOneMembership();

        Answer answer = post(TICKETS, "");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject()).containsOnlyKeys("ticket", "expiresAt");
        assertThat(String.valueOf(answer.asObject().get("ticket"))).isNotBlank();
        OffsetDateTime expiresAt = OffsetDateTime.parse(String.valueOf(answer.asObject().get("expiresAt")));
        assertThat(expiresAt.toInstant())
                .isBetween(Instant.now().plus(TICKET_TTL).minus(SLACK),
                        Instant.now().plus(TICKET_TTL).plus(SLACK));
        // Ни секрета, ни субъекта, ни тенанта отдельными полями: состав
        // тела закрыт двумя ключами выше, а секрет не уезжает и текстом.
        assertThat(answer.body()).doesNotContain(BffSubstrate.TICKET_SECRET);
        assertThat(answer.header("Content-Type")).startsWith("application/json");
        assertThat(recordsSinceStart()).isZero();
    }

    @Test
    @DisplayName("B2.2 — Выданный билет проверяется обратно в субъект и тенант")
    void b2_2_anIssuedTicketResolvesBackToItsSubjectAndTenant() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription stream = subscribe(ticket)) {
            publishDealOpened(tenant, "e-own-1");
            publishDealOpened(secondTenant, "e-foreign");
            // Второе заведомо доезжающее событие — барьер: без него
            // «чужого факта нет» было бы зелено и у мёртвой раздачи
            // (.claude/tests/cases/bff.md §«Новая ось формы»).
            publishDealOpened(tenant, "e-own-2");
            stream.awaitFrames(2);

            assertThat(stream.carriesStream()).isTrue();
            assertThat(stream.ids()).containsExactly("e-own-1", "e-own-2");
        }
    }

    @Test
    @DisplayName("B2.3 — Подделанный билет отвергается")
    void b2_3_aTamperedTicketIsRejected() {
        authAnswersOneMembership();
        String tampered = Tickets.tampered(issuedTicket());

        try (Subscription stream = subscribe(tampered)) {
            assertThat(stream.status()).isEqualTo(401);
            assertThat(stream.carriesStream()).isFalse();
            assertThat(stream.frames()).isEmpty();
            assertThat(stream.errorCode()).isEqualTo(UNAUTHENTICATED);
            // Какая именно проверка не прошла, наружу не уходит.
            assertThat(stream.errorBody())
                    .doesNotContain("одпис")
                    .doesNotContain("рок")
                    .doesNotContain("остав");
        }
    }

    @Test
    @DisplayName("B2.4 — Билет чужого секрета отвергается")
    void b2_4_aTicketOfAForeignSecretIsRejected() {
        String foreign = Tickets.forge("another-replicas-secret", subject, tenant,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        try (Subscription rejected = subscribe(foreign)) {
            assertThat(rejected.status()).isEqualTo(401);
            assertThat(rejected.carriesStream()).isFalse();
            assertThat(rejected.errorCode()).isEqualTo(UNAUTHENTICATED);
        }

        // Обратная сторона того же свойства: билет, собранный НЕ этим
        // процессом, но ОБЩИМ секретом реплик, принимается.
        String ofAnotherReplica = Tickets.forge(BffSubstrate.TICKET_SECRET, subject, tenant,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        try (Subscription accepted = openedStream(ofAnotherReplica, "e-of-another-replica")) {
            assertThat(accepted.status()).isEqualTo(200);
            assertThat(accepted.carriesStream()).isTrue();
            assertThat(accepted.ids()).containsExactly("e-of-another-replica");
        }
    }

    @Test
    @DisplayName("B2.6 — Негодность наружу не различается")
    void b2_6_theKindOfInvalidityIsNotDistinguishableFromOutside() {
        authAnswersOneMembership();
        String expired = Tickets.forge(BffSubstrate.TICKET_SECRET, subject, tenant,
                Instant.now().minus(1, ChronoUnit.MINUTES));
        String tampered = Tickets.tampered(issuedTicket());

        try (Subscription absent = subscribeWithoutTicket();
             Subscription unreadable = subscribe("not-a-ticket-at-all");
             Subscription broken = subscribe(tampered);
             Subscription stale = subscribe(expired)) {

            List<Subscription> answers = List.of(absent, unreadable, broken, stale);
            assertThat(answers).allSatisfy(answer -> {
                assertThat(answer.status()).isEqualTo(absent.status());
                assertThat(answer.errorCode()).isEqualTo(absent.errorCode());
                assertThat(answer.errorMessage()).isEqualTo(absent.errorMessage());
                assertThat(answer.carriesStream()).isFalse();
            });
        }
    }

    @Test
    @DisplayName("B2.8 — В пределах срока билет переиспользуем")
    void b2_8_aTicketIsReusableWithinItsTerm() {
        authAnswersOneMembership();
        String ticket = issuedTicket();

        try (Subscription first = openedStream(ticket, "e-first-open")) {
            assertThat(first.carriesStream()).isTrue();
        }
        try (Subscription second = openedStream(ticket, "e-second-open")) {
            assertThat(second.status()).isEqualTo(200);
            assertThat(second.carriesStream()).isTrue();
            // Повторное предъявление нигде не помечено: тот же билет
            // открывает провод второй раз и получает свою запись.
            assertThat(second.ids()).containsExactly("e-second-open");
        }
    }

    @Test
    @DisplayName("B2.9 — Роли билет не несёт")
    void b2_9_theTicketCarriesNoRole() {
        authAnswers(Bodies.memberships(tenant, "TRADER"));

        List<String> fields = Tickets.fieldsOf(issuedTicket());

        // Три поля: субъект, тенант и собственный срок. Срок — по коду
        // (SubscriptionTicketService#issue), а не по дому: расхождение
        // ушло находкой F-7 документа кейсов.
        assertThat(fields).hasSize(3);
        assertThat(fields.get(0)).isEqualTo(subject);
        assertThat(fields.get(1)).isEqualTo(tenant);
        assertThat(Long.parseLong(fields.get(2))).isPositive();
        assertThat(fields).doesNotContain("TRADER");
    }

    @Test
    @DisplayName("B2.10 — Выдача билета без токена не проходит")
    void b2_10_theIssuanceWithoutATokenDoesNotPass() {
        authAnswersOneMembership();

        Answer anonymous = postAnonymously(TICKETS, "");
        Answer foreignKey = postWith(TICKETS, identity.foreignKeyToken(), "");

        assertThat(List.of(anonymous, foreignKey)).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(401);
            assertThat(answer.carriesErrorDto()).isTrue();
            assertThat(answer.header("WWW-Authenticate")).isNotBlank();
            assertThat(answer.body()).doesNotContain("ticket");
        });
        // Резолв до отказа не доходит: контур отвергает раньше.
        assertThat(owners.count()).isZero();
    }

    @Test
    @DisplayName("B2.11 — Повторная выдача прежний билет не отменяет")
    void b2_11_aSecondIssuanceDoesNotRevokeTheFirst() {
        authAnswersOneMembership();
        String first = issuedTicket();
        String second = issuedTicket();

        try (Subscription byFirst = openedStream(first, "e-by-first")) {
            assertThat(byFirst.status()).isEqualTo(200);
            assertThat(byFirst.carriesStream()).isTrue();
        }
        try (Subscription bySecond = openedStream(second, "e-by-second")) {
            assertThat(bySecond.status()).isEqualTo(200);
            assertThat(bySecond.carriesStream()).isTrue();
        }
    }
}
