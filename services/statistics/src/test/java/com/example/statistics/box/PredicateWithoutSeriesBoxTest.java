package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B6.7} — предикат живёт записью, а не метрикой
 * (.claude/tests/cases/statistics.md §«B6 — Полнота: предикат
 * непрерывности»).
 *
 * <p><b>Ряды уносятся НЕЖИВЫМ ПРИЁМОМ, и повод назван самим кейсом.</b>
 * Соседний повод — снятый выключатель тика — дал бы то же отсутствие рядов,
 * но вместе с ними не дал бы и строк: их единственный писатель тот же тик,
 * и ставить их пришлось бы прямой записью. Неживой приём оставляет строки
 * заведёнными ТРОПОЙ и уносит только ряды — ровно то состояние, которое
 * клетка и разделяет.
 *
 * <p><b>Назначение отнимается снятием темы у брокера</b>, как у клетки о
 * неживом приёме: темы, которой у брокера нет, партиций нет тоже, и
 * назначение пустеет само. Контекст поэтому закрывается вместе с классом —
 * переиспользовать его некому.
 *
 * <p><b>Предмет здесь ДРУГОЙ, чем у клетки {@code B3.6} того же положения
 * оси.</b> Та спрашивает, что тик при неживом приёме молчит и уносит ряды;
 * эта — что обе величины полноты считаются по КОЛОНКАМ строк, и отсутствие
 * рядов на них не влияет ни в одну сторону. Носитель операндов объявлен
 * durable-строкой именно поэтому: метрика ротируется, и сослаться при
 * разборе можно только на запись (docs/concept.md, П3;
 * docs/rules/durable-consumer-reception.md §«Предикат непрерывности»).
 *
 * <p><b>Вторая половина клетки — та же выдача при УСТАРЕВШЕЙ строке.</b> Ею
 * предъявляется обратное направление того же утверждения: ложь приходит по
 * колонке, а не по наблюдателю — рядов не было ни до, ни после, а ответ
 * переменился.
 *
 * <p><b>Возраст строки ставится прямой записью, и это durable-вход.</b>
 * Единственный писатель момента обновления — тик, и ставит он момент своего
 * такта; состарить строку тропой ящика нечем, тем более при тике, который
 * молчит. Форма строки объявлена домом и читается наружу
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»).
 */
@DirtiesContext
class PredicateWithoutSeriesBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b6-7";

    /** Запись момента обновления строки пары. */
    private static final String SET_UPDATED = """
            update reception_states set updated_at = ? where consumer_group = ? and topic = ?
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B6.7 — Предикат живёт записью, а не метрикой")
    void thePredicateLivesInTheRowRatherThanInTheSeries() {
        givenReceptionStateRows();
        tick();
        assertThat(receptionRowCount()).as("ряды выпущены прошлым тактом").isPositive();

        Wire.deleteTopics(subscription());
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> {
                    tick();
                    return Objects.equals(receptionRowCount(), 0L);
                });

        assertThat(pairs())
                .as("вход поставлен: рядов экспорта нет, а строка состояния цела").hasSize(1);
        assertThat(instant(pair(topic()), UPDATED_COLUMN))
                .as("и свежа по моменту обновления: третий конъюнкт не гасится")
                .isAfter(momentsAgo(StatisticsSubstrate.STATE_MAX_AGE).toInstant());
        OffsetDateTime boundBefore = lowerBoundMoment();
        assertThat(continuityClaimable())
                .as("предикат считается по строкам состояния и отдаётся: отсутствие рядов на "
                        + "него не влияет")
                .isEqualTo(Boolean.TRUE);
        assertThat(boundBefore.toInstant())
                .as("и граница выведена из колонки момента наблюдения той же строки")
                .isEqualTo(instant(pair(topic()), OBSERVED_COLUMN));

        rows.write(SET_UPDATED, momentsAgo(StatisticsSubstrate.STATE_MAX_AGE.plusSeconds(1)),
                consumerGroup(), topic());

        assertThat(receptionRowCount())
                .as("рядов не было ни до, ни после: ответ переменился не из-за них").isZero();
        assertThat(continuityClaimable())
                .as("устаревшая строка роняет предикат независимо от того, что показывает "
                        + "наблюдатель: ложь приходит по КОЛОНКЕ")
                .isEqualTo(Boolean.FALSE);
        assertThat(lowerBoundMoment())
                .as("граница при этом цела: устаревание строки её не двигает")
                .isEqualTo(boundBefore);
    }
}
