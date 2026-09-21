package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B4.3}, {@code B4.4} и {@code B5.6} — третий исход сравнения
 * смещений, монотонность момента наблюдения и монотонность выведенной из него
 * нижней границы полноты (.claude/tests/cases/statistics.md §«B4 — Обнаружение
 * разрыва: три исхода сравнения смещений», §«B5 — Полнота: нижняя граница
 * ОДНИМ операндом»).
 *
 * <p><b>Все три стоя́т на одном положении осей и на одном состоянии группы</b>,
 * поэтому живут одним классом. Предмет у всех — тема, по которой группа
 * НИКОГДА не фиксировала смещения: в неё не кладёт записей ни одна из них, и
 * третья ветвь сравнения («смещения нет вовсе») достаётся ей на каждом
 * назначении.
 *
 * <p><b>Клетка группы {@code B5} живёт здесь, а не у своей группы, и это
 * решение о РЯБИ, а не об удобстве.</b> Назначение перетряхивается чужим
 * участником группы, и его вход и выход двигают момент наблюдения ещё долго
 * после того, как клетка закончилась: соседка, читающая границу равенством,
 * краснеет на микросекундах. Здесь назначение трогают все клетки, и наследовать
 * рябь не от кого; утверждения самой {@code B5.6} поэтому сплошь
 * МОНОТОННЫЕ — «уехало вперёд», «не опустилось», — а равенство моменту
 * назначения ни одно из них не берёт: между чтением границы и чтением строки
 * пары законно ложится ещё одно возобновление.
 *
 * <p><b>Момент наблюдения ставится прямой записью, и это durable-ВХОД, а не
 * выход.</b> Тик заводит строку моментом своего такта, то есть моментом
 * «сейчас»; клетке же нужен момент, названный ОТНОСИТЕЛЬНО назначения — раньше
 * него у одной и позже у другой. Форма строки объявлена домом и читается
 * наружу (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»), поэтому запись по колонке говорит о том же, о
 * чём читает ассерт.
 *
 * <p><b>Граница полноты у статистики считается ОДНИМ операндом</b> —
 * позднейшим моментом наблюдения пар (docs/spec/durable-reception.json,
 * {@code receptionLowerBound}), — и обе клетки читают её ровно так: чистки
 * фактов не существует, и второго операнда у границы нет вовсе.
 */
class ObservationRestartBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b4-3";

    /** Биржевой счёт — обязательный ключ сделочного зерна. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — второй компонент ключа зерна. */
    private static final String STRATEGY = "S-1";

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
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
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

    @Test
    @DisplayName("B5.6 — Граница монотонна: возобновление наблюдения её поднимает")
    void restartingTheObservationRaisesTheBound() {
        givenReceptionStateRows();
        OffsetDateTime before = momentsAgo(OBSERVED_BEFORE);
        rows.write(SET_OBSERVED, before, consumerGroup());
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("вход поставлен: по паре группа не фиксировала смещения ни разу").isNull();
        assertThat(lowerBoundMoment().toInstant())
                .as("и граница равна прежнему моменту наблюдения").isEqualTo(before.toInstant());

        reassignPartitions();
        awaitObservationRestarted(before);
        OffsetDateTime raised = lowerBoundMoment();
        givenPartitionsBack();

        assertThat(instant(pair(subject()), OBSERVED_COLUMN))
                .as("наблюдение возобновлено: момент пары уехал вперёд")
                .isAfter(before.toInstant());
        assertThat(raised.toInstant())
                .as("и граница поднялась вместе с ним: возобновление наблюдения её двигает")
                .isAfter(before.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("обратного хода у величины нет: второе назначение её не опускает")
                .isAfterOrEqualTo(raised.toInstant());
    }

    /** Тема, по которой группа не фиксировала смещения ни разу. */
    private String subject() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }

    /** Первая тема подписки: ею предъявляется возврат назначения контейнеру. */
    private String witness() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /**
     * Ждёт, пока контейнер получит партиции обратно: принятая запись первой
     * темы есть предъявление назначения — чужой участник закрыт, и принять её
     * больше некому. Своей темы клетка при этом не трогает: положенная в неё
     * запись двигала бы ровно ту величину, о неподвижности которой клетка
     * утверждает.
     */
    private void givenPartitionsBack() {
        publish(witness(), "E-BACK", DEAL_CLOSED, momentsAgo(Duration.ofMinutes(1)),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(witness());
    }

    /** Ждёт, пока момент наблюдения пары уедет вперёд от названного. */
    private void awaitObservationRestarted(OffsetDateTime observedSince) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> instant(pair(subject()), OBSERVED_COLUMN).isAfter(observedSince.toInstant()));
    }
}
