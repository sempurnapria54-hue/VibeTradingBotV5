package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B5.4} — максимум берётся по ВСЕМ строкам, включая отписанные
 * (.claude/tests/cases/statistics.md §«B5 — Полнота: нижняя граница ОДНИМ
 * операндом»).
 *
 * <p><b>Состав подписки — ВХОД клетки</b>, поэтому контекст у неё свой:
 * подписку контейнер читает при подъёме, и сузить её на живом контексте нечем.
 * Заведённых у брокера тем две, объявленных подпиской — одна: ровно то
 * состояние, в котором такт снимает признак подписки с ушедшей темы, а строку
 * её не удаляет.
 *
 * <p><b>Момент ушедшей темы ПОЗЖЕ момента оставшейся — не выдумка, а предмет
 * клетки:</b> тема присоединяется к подписке группы позже, чем её производитель
 * начал производить, и ровно на этом стои́т операнд наблюдения
 * (docs/rules/durable-consumer-reception.md §«Нижняя граница»). Исключи такую
 * строку из максимума — и граница опустится, то есть сервис пообещает о своих
 * числах больше, чем они несут; ошибка в разрешающую сторону.
 *
 * <p><b>Строка ушедшей пары ставится прямой записью, и это durable-ВХОД.</b>
 * Тропой ящика она не производится вовсе: строку заводит тик, и заводит он её
 * только темам объявленной подписки — темы, которой в подписке нет, у него в
 * составе не бывает. Форма строки объявлена домом и читается наружу (там же,
 * §«Строка состояния приёма — таблица `reception_states`»), поэтому запись по
 * колонкам говорит о том же, о чём читает ассерт.
 *
 * <p><b>Остановка приёма у ушедшей пары поставлена намеренно.</b> Без неё
 * вторая половина клетки — «область пустой ветви есть ПОДПИСАННЫЕ пары» — была
 * бы пустым утверждением: предикат непрерывности остался бы истинным при любой
 * области квантора, и разведение двух областей не предъявлялось бы ничем
 * (docs/spec/durable-reception.json, {@code pairsObservedSince} против
 * {@code pairsObserved}).
 *
 * <p><b>Клетка о ВТОРОМ операнде границы у статистики отсутствует</b> — чистки
 * фактов не существует ни в одном окружении, — и потому весь предмет границы
 * здесь держат моменты наблюдения пар
 * (docs/models/domain/other/StatisticsFact.md §«Состояние приёма и полнота
 * чисел статистики»).
 */
class UnsubscribedPairBoundBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b5-4";

    /** Насколько раньше «сейчас» наблюдается оставшаяся в подписке тема. */
    private static final Duration SUBSCRIBED_SINCE_AGO = Duration.ofHours(2);

    /** Насколько раньше «сейчас» наблюдается ушедшая тема: её момент ПОЗДНЕЙШИЙ. */
    private static final Duration DEPARTED_SINCE_AGO = Duration.ofHours(1);

    /** Запись момента наблюдения одной пары. */
    private static final String SET_PAIR_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ? and topic = ?
            """;

    /** Вставка строки ушедшей пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, true, ?)
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG,
                List.of(StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG)),
                List.of(StatisticsSubstrate.ownTopic(SLUG)),
                Map.of());
    }

    @Test
    @DisplayName("B5.4 — Максимум берётся по ВСЕМ строкам, включая отписанные")
    void theMaximumCoversDepartedRowsAsWell() {
        givenReceptionStateRows();
        OffsetDateTime departedSince = momentsAgo(DEPARTED_SINCE_AGO);
        rows.write(SET_PAIR_OBSERVED, momentsAgo(SUBSCRIBED_SINCE_AGO), consumerGroup(), subscribed());
        rows.write(OPEN_PAIR, consumerGroup(), departed(), departedSince, now());

        tick();

        assertThat(pair(departed()).get(SUBSCRIBED_COLUMN))
                .as("вход поставлен: пара ушедшей темы снята с подписки")
                .isEqualTo(Boolean.FALSE);
        assertThat(instant(pair(departed()), OBSERVED_COLUMN))
                .as("а момент её наблюдения — позднейший из двух")
                .isEqualTo(departedSince.toInstant())
                .isAfter(instant(pair(subscribed()), OBSERVED_COLUMN));
        assertThat(lowerBoundMoment().toInstant())
                .as("момент снятой с подписки пары из максимума НЕ исключается")
                .isEqualTo(departedSince.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("исключение опустило бы границу: момент оставшейся пары раньше")
                .isAfter(instant(pair(subscribed()), OBSERVED_COLUMN));
        assertThat(pair(subscribed()).get(SUBSCRIBED_COLUMN))
                .as("область пустой ветви — ПОДПИСАННЫЕ пары, и она непуста")
                .isEqualTo(Boolean.TRUE);
        assertThat(pair(departed()).get(HALTED_COLUMN))
                .as("вход второй половины поставлен: приём по снятой паре остановлен")
                .isEqualTo(Boolean.TRUE);
        assertThat(continuityClaimable())
                .as("непрерывность считается только по ПОДПИСАННЫМ: остановка снятой её не роняет")
                .isEqualTo(Boolean.TRUE);
    }

    /** Тема, оставшаяся в объявленной подписке. */
    private String subscribed() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /** Тема, которой в объявленной подписке нет: её пару такт и снимает. */
    private String departed() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }
}
