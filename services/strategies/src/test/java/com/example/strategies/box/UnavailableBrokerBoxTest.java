package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Недоступный брокер — клетки {@code B7.4} и {@code B7.5} парой
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Пара живёт одной клеткой кода, и это не слияние предметов.</b>
 * Вторая половина — «следующий тик публикует накопленное» — утверждает о
 * состоянии, которое производит ПЕРВАЯ: три непомеченные строки,
 * оставшиеся после отказа. Разведённые по классам, они стали бы двумя
 * контекстами и вторым построением того же состояния.
 *
 * <p><b>Свой контекст, и это ВХОД клетки:</b> адрес брокера подменяется
 * перекрываемой тропой ({@link BrokerGate}), которую клетка закрывает на
 * первом тике и открывает на втором. Общий контейнер брокера при этом
 * ЖИВ и соседям доступен — недоступной делается тропа, а не субстрат:
 * кейс, лишающий соседей адреса, обязан был бы взять свой контейнер
 * (.claude/decisions/test-contour-design-pass.md §«Кейс, разрушающий
 * субстрат, берёт свой контейнер и свой контекст»).
 *
 * <p><b>Предмет — ЧТО НЕ СЛУЧИЛОСЬ.</b> Отказ брокера не есть ошибка
 * решения: переход статуса уже записан и от публикации не зависит —
 * значит статусы стоя́т, строки копятся, а наружу исход работы не идёт
 * (docs/components/OutboxRelayJob.md; docs/rules/error-handling-policy.md).
 *
 * <p><b>Тик ждётся своим потолком.</b> Отказ приходит не сразу:
 * публикующий клиент ждёт раскладки темы до своего умолчания, а
 * величиной конфигурации сервиса этот потолок не объявлен — цена названа
 * у {@link BrokerGate}.
 */
class UnavailableBrokerBoxTest extends StrategiesBox {

    /**
     * Потолок ожидания следа тика против закрытой тропы: он длиннее
     * штатного ровно на умолчание клиента брокера.
     */
    private static final Duration REFUSAL_TIMEOUT = Duration.ofSeconds(240);

    /** Перекрываемая тропа: контекст читает её адрес вместо адреса контейнера. */
    private static final BrokerGate GATE =
            BrokerGate.closedBefore(StrategiesSubstrate.brokerAddress());

    /** Сколько неопубликованных строк копит предусловие. */
    private static final Integer ROWS = 3;

    /** Запись журнала об отказе брокера. */
    private static final String BROKER_REFUSED = "Broker refused the outbox row";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StrategiesSubstrate.register(registry,
                Map.of(StrategiesSubstrate.BROKER_ADDRESS_KEY, GATE.bootstrapServers()));
    }

    @Test
    @DisplayName("B7.4, B7.5 — Отказ брокера строк не теряет, следующий тик публикует накопленное")
    void b7_4_and_b7_5_aRefusingBrokerLosesNoRowsAndTheNextTickPublishesThemAll() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);
        List<String> written = eventIds();
        assertThat(written).hasSize(ROWS);
        Integer logMark = AppLog.mark();

        Answer refused = relayPass(REFUSAL_TIMEOUT);

        assertThat(refused.status())
                .as("исход работы наружу не транслируется")
                .isEqualTo(202);
        assertThat(marked())
                .as("проход прекратился на первом отказе: ни одна строка не помечена")
                .isEmpty();
        assertThat(eventIds())
                .as("строки остаются в базе целиком и в прежнем порядке")
                .isEqualTo(written);
        assertThat(statusOf(internalId, TENANT))
                .as("переходов статуса отказ публикации не трогает")
                .isEqualTo("DELETED");
        assertThat(AppLog.since(logMark))
                .as("отказ виден строкой журнала")
                .contains(BROKER_REFUSED);

        GATE.open();
        Wire.Mark mark = Wire.mark();
        relayPass(REFUSAL_TIMEOUT);
        awaitPublished(ROWS);

        assertThat(Wire.publishedSince(mark).stream().map(Wire.Published::eventId).toList())
                .as("уходят ВСЕ три, и в порядке записи: записанное раньше публикуется раньше")
                .isEqualTo(written);
        assertThat(marked()).isEqualTo(written);
    }

    /** Идентичности событий всех строк outbox в порядке записи. */
    private List<String> eventIds() {
        return events().stream().map(row -> String.valueOf(row.get("event_id"))).toList();
    }
}
