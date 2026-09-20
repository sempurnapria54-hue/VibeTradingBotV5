package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B4.3} и {@code B4.4} — третий исход сравнения смещений и
 * монотонность момента наблюдения (.claude/tests/cases/audit.md §«B4 —
 * Обнаружение разрыва: три исхода сравнения смещений»).
 *
 * <p><b>Обе стоя́т на одном положении осей и на одном состоянии группы</b>,
 * поэтому живут одним классом. Предмет у обеих — тема, по которой группа
 * НИКОГДА не фиксировала смещения: в неё не кладёт записей ни одна из них,
 * и третья ветвь сравнения («смещения нет вовсе») достаётся ей на каждом
 * назначении.
 *
 * <p><b>Момент наблюдения ставится прямой записью, и это durable-ВХОД, а
 * не выход.</b> Тик заводит строку моментом своего такта, то есть моментом
 * «сейчас»; клетке же нужен момент, названный ОТНОСИТЕЛЬНО назначения —
 * раньше него у одной и позже у другой. Форма строки объявлена домом и
 * читается наружу (docs/rules/durable-consumer-reception.md §«Строка
 * состояния приёма — таблица `reception_states`»), поэтому запись по
 * колонке говорит о том же, о чём читает ассерт.
 *
 * <p><b>Момент ПОЗЖЕ назначения — не выдумка клетки, а названный домом
 * повод:</b> часы разошлись либо строка заведена позже назначения
 * (.claude/tests/cases/audit.md, {@code B4.4}).
 */
class ObservationRestartBoxTest extends AuditBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b4-3";

    /** Насколько раньше назначения наблюдается пара у клетки о возобновлении. */
    private static final Duration OBSERVED_BEFORE = Duration.ofMinutes(10);

    /** Насколько позже назначения наблюдается пара у клетки о монотонности. */
    private static final Duration OBSERVED_AHEAD = Duration.ofMinutes(10);

    /** Запись момента наблюдения по всем строкам группы. */
    private static final String SET_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ?
            """;

    /** Запись момента наблюдения одной пары. */
    private static final String SET_PAIR_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ? and topic = ?
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG, Map.of());
    }

    @Test
    @DisplayName("B4.3 — Смещения нет вовсе: наблюдение начинается заново")
    void withoutAnyCommittedOffsetTheObservationStartsAnew() {
        givenReceptionStateRows();
        OffsetDateTime observedSince = momentsAgo(OBSERVED_BEFORE);
        rows.write(SET_OBSERVED, observedSince, consumerGroup());
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("вход поставлен: зафиксированного смещения по паре не осталось").isNull();
        OffsetDateTime boundBefore = lowerBoundMoment();

        reassignPartitions();
        awaitObservationRestarted(observedSince);

        assertThat(instant(pair(subject()), OBSERVED_COLUMN))
                .as("момент наблюдения переписан моментом назначения и больше прежнего")
                .isAfter(observedSince.toInstant());
        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("момент разрыва пуст: разрыв границей не подменяется").isNull();
        assertThat(continuityClaimable())
                .as("предикат непрерывности по паре истинен").isEqualTo(Boolean.TRUE);
        assertThat(lowerBoundMoment())
                .as("нижняя граница полноты двинулась вперёд").isAfter(boundBefore);
    }

    @Test
    @DisplayName("B4.4 — Момент наблюдения двигается только вперёд")
    void theObservationMomentMovesForwardOnly() {
        givenReceptionStateRows();
        OffsetDateTime ahead = now().plus(OBSERVED_AHEAD).truncatedTo(ChronoUnit.MILLIS);
        rows.write(SET_PAIR_OBSERVED, ahead, consumerGroup(), subject());
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("вход поставлен: назначение придёт без зафиксированного смещения").isNull();
        OffsetDateTime boundBefore = lowerBoundMoment();

        reassignPartitions();
        givenPartitionsBack();

        assertThat(instant(pair(subject()), OBSERVED_COLUMN))
                .as("момент наблюдения остался прежним: назад он не переписывается")
                .isEqualTo(ahead.toInstant());
        assertThat(lowerBoundMoment())
                .as("нижняя граница полноты не опустилась").isEqualTo(boundBefore);
    }

    /** Тема, по которой группа не фиксировала смещения ни разу. */
    private String subject() {
        return AuditSubstrate.ownStrategyTopic(SLUG);
    }

    /** Соседняя тема: ею предъявляется возврат назначения контейнеру. */
    private String witness() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }

    /**
     * Ждёт, пока контейнер получит партиции обратно: принятая запись
     * соседней темы есть предъявление назначения — чужой участник закрыт,
     * и принять её больше некому. Своей темы клетка при этом не трогает:
     * положенная в неё запись двигала бы ровно ту величину, о
     * неподвижности которой клетка утверждает.
     */
    private void givenPartitionsBack() {
        publish(witness(), "E-BACK", momentsAgo(Duration.ofMinutes(1)), Bodies.reference());
        awaitConsumed(witness());
    }

    /** Ждёт, пока момент наблюдения пары уедет вперёд от названного. */
    private void awaitObservationRestarted(OffsetDateTime observedSince) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> instant(pair(subject()), OBSERVED_COLUMN).isAfter(observedSince.toInstant()));
    }

    /**
     * Нижняя граница полноты как точка шкалы.
     *
     * <p>Читается она ЧЕРЕЗ ПОВЕРХНОСТЬ — предмет величины в том, что
     * журнал объявляет о себе читателю, — а разбирается здесь потому, что
     * обе клетки утверждают о НАПРАВЛЕНИИ её движения, а не о самом факте
     * правки.
     */
    private OffsetDateTime lowerBoundMoment() {
        Object value = lowerBound();
        if (Objects.isNull(value)) {
            throw new AssertionError("Нижняя граница полноты отсутствует: обещать нечего");
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }
}
