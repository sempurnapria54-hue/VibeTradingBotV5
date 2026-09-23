package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Клетки, чей предмет — СТАРТ процесса реплики
 * (.claude/tests/cases/bff.md, {@code B4.10} — перезапуск теряет эфемерное
 * состояние; {@code B6.8} — позиция группы при подъёме; {@code B6.9} —
 * перечень тем подписки).
 *
 * <p><b>Реплику клетка поднимает сама, потому что предмет у всех трёх —
 * момент подъёма.</b> Контекст ящика поднят до первой клетки и из кэша не
 * уходит: запись, положенная «до подъёма», и группа, чьё назначение
 * читается у брокера, у него уже в прошлом. Собственный контекст ящика
 * клеткам служит только наблюдателем субстрата.
 *
 * <p><b>Перезапуск здесь настоящий, а не его подобие.</b> Клетка поднимает
 * реплику той же формой, что и ящик, наполняет её состояние, ОСТАНАВЛИВАЕТ
 * её и поднимает следующую на тех же осях — то есть процесс с тем же
 * диском и тем же брокером. Очистка памяти изнутри проверяла бы нашу
 * очистку, а не то, что у периметра нет состояния, переживающего процесс.
 * Собственный контекст ящика клетке служит только наблюдателем субстрата.
 *
 * <p><b>Потолок подписок обеих реплик — единица, и это вход, а не
 * упрощение.</b> Открытых подписок реплика наружу не отдаёт ничем; при
 * единице «новая подписка тенанта открылась» и есть предъявление, что
 * подписок у тенанта на реплике нет ни одной — при штатных тридцати двух
 * она открылась бы при любом наследстве. Клеткам {@code B6} потолок
 * безразличен: подписка у них одна.
 *
 * <p>Форма подъёма реплики — {@link Replica}.
 */
class ReplicaStartBoxTest extends SharedBffBox {

    /** Тенант клетки о перезапуске. */
    private static final String TENANT_OF_CELL = "TR10";

    /** Тенант клетки о позиции группы при подъёме. */
    private static final String LATE_TENANT = "TE8";

    /** Тема без производителя: у классов `auth` его не построено. */
    private static final String PRODUCERLESS_TOPIC = "auth.facts";

    /** Группа реплики клетки о перечне тем: назначение читается у брокера по имени. */
    private static final String NAMED_GROUP = "bff-box-b6-9";

    /** Потолок подписок обеих реплик: см. шапку класса. */
    private static final Integer CEILING = 1;

    @Test
    @DisplayName("B4.10 — Перезапуск процесса теряет окно, кэш и подписки — и это законно")
    void b4_10_aRestartLosesTheWindowTheCacheAndTheSubscriptions() {
        String ticket;
        Subscription warm;
        try (ConfigurableApplicationContext first = replica()) {
            Integer firstPort = portOf(first);
            awaitDeliveryAt(firstPort);
            authAnswers(Bodies.memberships(TENANT_OF_CELL, ROLE));
            owners.forgetRequests();
            // Кэш членств горяч: контекст и выдача билета — один запрос к
            // владельцу, второй отдан из памяти.
            assertThat(getAt(firstPort, CONTEXT, token()).status()).isEqualTo(200);
            ticket = issuedTicketAt(firstPort, token());
            assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(1);

            // Подписка открыта, окно наполнено: потолок тенанта исчерпан.
            warm = subscribeAt(firstPort, ticket, null);
            publishDealOpened(TENANT_OF_CELL, "e-b4-10-1");
            publishDealOpened(TENANT_OF_CELL, "e-b4-10-2");
            warm.awaitFrames(2);
            assertThat(warm.ids()).containsExactly("e-b4-10-1", "e-b4-10-2");
        }
        // Остановленная реплика рвёт свой провод: подписка не пережила
        // процесс и на клиенте.
        warm.awaitClosed(Duration.ofSeconds(30));
        warm.close();

        try (ConfigurableApplicationContext restarted = replica()) {
            Integer restartedPort = portOf(restarted);
            awaitDeliveryAt(restartedPort);
            authAnswers(Bodies.memberships(TENANT_OF_CELL, ROLE));
            owners.forgetRequests();

            // Членства перечитываются у владельца: горячей записи кэша нет.
            assertThat(getAt(restartedPort, CONTEXT, token()).status()).isEqualTo(200);
            assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(1);

            // Окно пусто — прежняя позиция даёт разрыв; и подписок у
            // тенанта нет ни одной — при потолке в единицу новая открылась.
            // Билет прежней реплики годен: секрет подписи общий у реплик.
            try (Subscription cold = subscribeAt(restartedPort, ticket, "e-b4-10-1")) {
                cold.awaitFrames(1);
                publishDealOpened(TENANT_OF_CELL, "e-b4-10-3");
                cold.awaitFrames(2);

                assertThat(cold.types()).containsExactly("PERIMETER_GAP", "DEAL_OPENED");
                assertThat(cold.ids()).containsExactly(null, "e-b4-10-3");
                assertThat(cold.ids()).doesNotContain("e-b4-10-2");
            }
        }
    }

    @Test
    @DisplayName("B6.8 — Группа начинается с текущего момента, истории не переигрывает")
    void b6_8_theGroupStartsAtTheCurrentMoment() {
        // Запись положена ДО подъёма реплики: её группа ещё не существует.
        publishDealOpened(LATE_TENANT, "e-b6-8-before");

        try (ConfigurableApplicationContext late = replica()) {
            Integer latePort = portOf(late);
            awaitDeliveryAt(latePort);
            authAnswers(Bodies.memberships(LATE_TENANT, ROLE));
            String ticket = issuedTicketAt(latePort, token());

            try (Subscription stream = subscribeAt(latePort, ticket, null)) {
                publishDealOpened(LATE_TENANT, "e-b6-8-after");
                stream.awaitFrames(1);

                // Доехала только новая: позиция группы — момент подъёма, и
                // на начало темы она не сбрасывается.
                assertThat(stream.ids()).containsExactly("e-b6-8-after");
            }
        }
    }

    @Test
    @DisplayName("B6.9 — Подписка идёт только на темы, у которых есть производитель")
    void b6_9_theSubscriptionCoversOnlyTopicsWithAProducer() {
        wire.createTopic(PRODUCERLESS_TOPIC);
        Set<String> topicsBefore = wire.topics();

        try (ConfigurableApplicationContext named = replica(Map.of("broker.group-id", NAMED_GROUP))) {
            awaitDeliveryAt(portOf(named));

            // Назначение группы — ровно объявленные темы; темы `auth` среди
            // них нет, хотя в брокере она заведена.
            assertThat(wire.assignedTopics(NAMED_GROUP))
                    .containsExactlyInAnyOrder(Wire.CORE_TOPIC, Wire.STRATEGIES_TOPIC)
                    .doesNotContain(PRODUCERLESS_TOPIC);
            // Подписка на несуществующую тему не создаётся: подъём реплики
            // не завёл в брокере ни одной темы.
            assertThat(wire.topics()).isEqualTo(topicsBefore);
        }
    }

    /** Реплика с потолком подписок в единицу: см. шапку класса. */
    private static ConfigurableApplicationContext replica() {
        return Replica.launch(Map.of(BffSubstrate.MAX_SUBSCRIPTIONS_KEY, String.valueOf(CEILING)));
    }

    private static ConfigurableApplicationContext replica(Map<String, String> overrides) {
        return Replica.launch(overrides);
    }

    private static Integer portOf(ConfigurableApplicationContext replica) {
        return Replica.portOf(replica);
    }
}
