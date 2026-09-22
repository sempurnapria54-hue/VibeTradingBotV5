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
 * Клетки группы {@code B1}, чей предмет — ИСТЕЧЕНИЕ срока годности
 * записи кэша членств (.claude/tests/cases/bff.md, {@code B1.8},
 * {@code B1.9}).
 *
 * <p><b>Свой контекст, потому что сдвинута ось.</b> Срок годности у
 * соседних клеток заведомо больше их длительности — это их
 * предусловие, — а здесь он обязан истечь внутри клетки; одно и то же
 * положение оси обе группы выразить не могут.
 *
 * <p><b>Пауза ждёт ОБЪЯВЛЕННОГО срока, а не асинхронного следа, и
 * подменой часов не является.</b> Возраст записи кэша — величина
 * конфигурации, и кейс выбирает её значением прогона
 * (.claude/tests/cases/bff.md §«Чем достаются выходы»: часы процесса не
 * двигаются ни в одном кейсе). Ждать приходится ровно потому, что
 * истечение срока и есть предмет клетки.
 */
class MembershipCacheExpiryBoxTest extends BffBox {

    /** Срок годности записи кэша этого контекста: он обязан истечь внутри клетки. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    /** Пауза: заведомо больше срока, но не настолько, чтобы прогон стоил минут. */
    private static final Duration PAUSE = CACHE_TTL.plusMillis(700);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(
                BffSubstrate.MEMBERSHIP_CACHE_TTL_KEY, CACHE_TTL.toMillis() + "ms"));
    }

    @Test
    @DisplayName("B1.8 — Просроченная запись перечитывается у владельца")
    void b1_8_anExpiredEntryIsReReadFromTheOwner() {
        authAnswers(Bodies.memberships(TENANT, ROLE));

        Answer first = get(CONTEXT);
        authAnswers(Bodies.memberships(SECOND_TENANT, ROLE));
        pause();
        Answer second = get(CONTEXT);

        assertThat(first.asObject()).containsEntry("tenantId", TENANT);
        assertThat(second.asObject()).containsEntry("tenantId", SECOND_TENANT);
        assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(2);
    }

    @Test
    @DisplayName("B1.9 — Ключ кэша — субъект, и ошибка направлена в разрешающую сторону")
    void b1_9_theCacheKeyIsTheSubject() {
        authAnswers(Bodies.memberships(TENANT, ROLE));

        Answer first = get(CONTEXT);
        // Членство «отозвано» у владельца: запись кэша об этом ещё не знает.
        authAnswers(Bodies.noMemberships());
        Answer withAnotherToken = getWith(CONTEXT, identity.anotherTokenFor(subject));

        assertThat(first.asObject()).containsEntry("tenantId", TENANT);
        assertThat(withAnotherToken.asObject()).containsEntry("tenantId", TENANT);
        assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(1);

        // По истечении срока отзыв становится виден: ошибка кэша
        // направлена в разрешающую сторону и ограничена сроком.
        pause();
        Answer afterExpiry = get(CONTEXT);

        assertThat(afterExpiry.carriesErrorDto()).isTrue();
        assertThat(afterExpiry.errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(afterExpiry.errorMessage()).contains("членств у предъявителя нет");
        assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(2);
    }

    /** Пауза длиннее объявленного срока годности записи кэша. */
    private static void pause() {
        Awaitility.await()
                .pollDelay(PAUSE)
                .atMost(PAUSE.plusSeconds(5))
                .until(() -> Boolean.TRUE);
    }
}
