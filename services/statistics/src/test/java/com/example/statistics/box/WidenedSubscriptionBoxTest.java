package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B12.3} — подписка приходит конфигурацией, и форма значения
 * есть скаляр через запятую (.claude/tests/cases/statistics.md §«B12 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Штатная подписка статистики объявлена ОДНОЙ темой, поэтому клетка
 * РАСШИРЯЕТ её, а не сужает.</b> Операнды объявленных зёрен несёт сегодня
 * один сосед (docs/models/domain/other/StatisticsFact.md §«Признак несомого
 * класса»), и на одном имени разделитель значения не наблюдаем вовсе: скаляр
 * без запятой разбирается в перечень из одного имени при любом разборе, в том
 * числе при отсутствии разбора.
 *
 * <p><b>Обе темы заводятся у брокера, и этим клетка отличается от соседки по
 * форме.</b> {@code B3.5} разводит объявленную подписку с назначением —
 * вторая её тема у брокера отсутствует, — и потому предъявить, что контейнер
 * ЧИТАЕТ каждое названное имя, она не может по построению. Здесь предмет
 * ровно этот: запись, положенная в каждую из двух названных тем, доезжает до
 * своего факта.
 *
 * <p><b>«Перечень разбирается ровно одним читателем» предъявляется
 * СХОЖДЕНИЕМ двух его потребителей.</b> Перечень читают слушатель (выражением
 * {@code @KafkaListener}) и тик состояния приёма (объявленной подпиской
 * контейнера); второй разбор того же ключа развёл бы их молча. Клетка
 * сверяет, что множество тем, по которым группа фиксирует смещение, и
 * множество тем строк состояния — одно и то же множество названных имён.
 *
 * <p><b>Своя группа и свои темы обязательны</b> по общему признаку класса со
 * своим положением осей: смещение есть состояние на брокере, общее всем
 * контекстам одного имени группы.
 */
class WidenedSubscriptionBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b12-3";

    /** Возраст события, с которым клетка ходит в темы. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Сколько фактов обязано лечь: по одному на каждую названную тему. */
    private static final Long EXPECTED_FACTS = 2L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
    }

    @Test
    @DisplayName("B12.3 — Подписка приходит конфигурацией, и форма значения — скаляр через запятую")
    void theSubscriptionArrivesByConfigurationAsACommaSeparatedScalar() {
        assertThat(subscription())
                .as("значение ключа разобрано в ДВА имени: разделитель прочитан")
                .containsExactly(first(), second());

        publish(first(), "E-B12-3-FIRST", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        publish(second(), "E-B12-3-SECOND", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        awaitDealFactCount(EXPECTED_FACTS);
        awaitConsumed(first());
        awaitConsumed(second());
        givenReceptionStateRows();

        assertThat(dealFacts())
                .as("контейнер подписан на КАЖДУЮ названную тему: запись каждой доехала "
                        + "до своего факта")
                .extracting(row -> row.get("event_id"))
                .containsExactlyInAnyOrder("E-B12-3-FIRST", "E-B12-3-SECOND");
        assertThat(pairs())
                .as("строк состояния столько, сколько тем в перечне")
                .hasSize(subscription().size());
        assertThat(pairs().stream().map(row -> row.get(TOPIC_COLUMN)).toList())
                .as("множество тем, по которым фиксируется смещение, и множество тем строк "
                        + "состояния — одно: перечень разбирает один читатель, и второй его "
                        + "копии не заводится")
                .containsExactlyInAnyOrderElementsOf(subscription());
        assertThat(subscription())
                .allSatisfy(topic -> assertThat(Wire.committedOffset(consumerGroup(), topic))
                        .as("смещение зафиксировано по теме %s", topic)
                        .isEqualTo(Wire.endOffset(topic)));
    }

    /** Первая названная тема подписки. */
    private static String first() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /** Вторая названная тема подписки: без неё разделитель не наблюдаем. */
    private static String second() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }
}
