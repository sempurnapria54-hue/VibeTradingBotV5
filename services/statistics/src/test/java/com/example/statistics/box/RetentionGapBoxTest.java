package com.example.statistics.box;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B4.2} — смещение ниже наименьшего доступного: пишется момент
 * разрыва (.claude/tests/cases/statistics.md).
 *
 * <p><b>Класс у неё свой, потому что вход её — СОСТОЯНИЕ ТЕМЫ на брокере.</b>
 * Записи темы удаляются безвозвратно, и соседняя клетка, читающая ту же тему,
 * наблюдала бы исход этой.
 *
 * <p><b>Вход ставится двумя ходами, и оба объявлены.</b> Зафиксированное
 * смещение оказывается НИЖЕ наименьшего доступного, когда конец темы ушёл
 * дальше зафиксированного: управляющая запись транзакции занимает своё
 * смещение, потребителю не отдаётся и потому в зафиксированное не входит
 * ({@link Wire#publishInTransaction}), а удаление записей ниже конца темы
 * поднимает наименьшее доступное над ним ({@link Wire#deleteRecordsBefore}). В
 * проде то же состояние производит срок хранения — величина ОКРУЖЕНИЯ, ждать
 * которой прогон не может; наблюдаемое у обоих поводов одно, и клетка
 * утверждает о нём.
 *
 * <p><b>Вторая пара получает своё смещение ДО ребалансировки, и это несущая
 * часть клетки.</b> Без него её ветвь на том же назначении — третья («смещения
 * нет вовсе»), и момент наблюдения второй пары уехал бы вперёд, подняв нижнюю
 * границу полноты: клетка о неподвижности границы краснела бы по причине,
 * которой не ставила.
 *
 * <p><b>Ложь предиката даёт РАЗРЫВ, а не остановка.</b> Флаг остановки у пары
 * лежит ложью и после назначения: приём по ней не ломали ни разу.
 */
class RetentionGapBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b4-2";

    /** Биржевой счёт — обязательный ключ сделочного зерна. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — второй компонент ключа зерна. */
    private static final String STRATEGY = "S-1";

    /** Возраст события, которым клетка ходит в темы. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Сколько фактов лежит к моменту ребалансировки: по одному на тему. */
    private static final Long PUBLISHED = 2L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
    }

    @Test
    @DisplayName("B4.2 — Смещение ниже наименьшего доступного: пишется момент разрыва")
    void anOffsetBelowTheEarliestAvailableYieldsTheGapMoment() {
        givenReceptionStateRows();
        publish(neighbour(), "E-N", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(neighbour());
        Wire.publishInTransaction(subject(), TENANT,
                envelope("E-1", DEAL_CLOSED, momentsAgo(EVENT_AGE)),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitDealFactCount(PUBLISHED);
        Long committed = Wire.committedOffset(consumerGroup(), subject());
        Wire.deleteRecordsBefore(subject(), Wire.endOffset(subject()));
        assertThat(committed)
                .as("вход поставлен: зафиксированное группой смещение ниже наименьшего доступного")
                .isLessThan(Wire.earliestOffset(subject()));
        Object observedBefore = pair(subject()).get(OBSERVED_COLUMN);
        Object boundBefore = lowerBound();

        reassignPartitions();
        awaitGap(subject());

        assertThat(pair(subject()).get(OBSERVED_COLUMN))
                .as("момент наблюдения не двинут: исходы не перекрываются, и различает их наличие смещения")
                .isEqualTo(observedBefore);
        assertThat(pair(subject()).get(HALTED_COLUMN))
                .as("приём по паре не останавливали: предикат роняет разрыв, а не остановка")
                .isEqualTo(Boolean.FALSE);
        assertThat(pair(neighbour()).get(GAP_COLUMN))
                .as("у второй пары разрыва нет").isNull();
        assertThat(lowerBound()).as("нижняя граница полноты не сдвинулась").isEqualTo(boundBefore);
        assertThat(continuityClaimable())
                .as("выдача чтения несёт границу и ложный предикат вместе").isEqualTo(Boolean.FALSE);
    }

    /** Тема, чьи записи клетка отнимает у непрочитавшего потребителя. */
    private String subject() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /** Вторая тема: ею наблюдается радиус разрыва и неподвижность границы. */
    private String neighbour() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }

    /** Ждёт момента разрыва у названной пары. */
    private void awaitGap(String topic) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> nonNull(pair(topic).get(GAP_COLUMN)));
    }
}
