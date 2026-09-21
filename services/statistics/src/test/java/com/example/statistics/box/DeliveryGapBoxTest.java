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
 * Клетки группы {@code B4}, у которых момент обнаружения разрыва — ДОСТАВКА
 * записи, а не назначение партиций (.claude/tests/cases/statistics.md §«B4 —
 * Обнаружение разрыва: три исхода сравнения смещений»).
 *
 * <p><b>Все четыре берут одно положение осей и живут одним классом:</b> приём у
 * них жив, назначение не меняется, и вход подаётся записью в тему — ровно то,
 * ради чего заведён второй момент сравнения
 * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва — сравнение
 * смещений, и моментов у него два»).
 *
 * <p><b>Подписка у контекста объявлена ДВУМЯ темами, и без второй клетка о
 * радиусе не выразима вовсе:</b> утверждение «момент лёг у своей пары, а не у
 * группы» на одной паре сказать нечем. Вторая тема — ось КОНФИГУРАЦИИ, а не
 * второй производитель ({@link StatisticsSubstrate#ownSecondTopic}).
 *
 * <p><b>Смещение доставленной записи поднимается УПРАВЛЯЮЩЕЙ записью
 * транзакции</b> ({@link Wire#publishInTransaction}): она занимает своё
 * смещение и потребителю не отдаётся, поэтому следующая обычная приезжает на
 * единицу дальше ожидаемого. Замена повода объявлена у самого хода.
 *
 * <p><b>Порядок «момент разрыва положен ДО приёма» этими клетками не
 * наблюдается, и это названное ограничение, а не пропуск.</b> Сделочный факт
 * момента приёма не хранит вовсе — его ось времени есть момент происшествия из
 * конверта (.claude/tests/cases/statistics.md, {@code B1.2}), — и сравнить два
 * момента здесь не с чем. Границу транзакций предъявляет клетка {@code B4.8}:
 * момент разрыва лежит там, где обработка откатилась целиком.
 */
class DeliveryGapBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b4-5";

    /** Биржевой счёт — обязательный ключ сделочного зерна. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — второй компонент ключа зерна. */
    private static final String STRATEGY = "S-1";

    /** Возраст события, которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Сутки окна, в полночь которых кладут событие клетки о пересчёте. */
    private static final Integer BUCKET_DAYS_BACK = 1;

    /** Идентичности записей, идущих подряд за записью с пропуском. */
    private static final List<String> AFTER_GAP = List.of("E-N1", "E-N2", "E-N3");

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
    }

    @Test
    @DisplayName("B4.5 — Разрыв на доставке: смещение записи больше ожидаемого")
    void aRecordDeliveredAboveTheExpectedOffsetIsAGap() {
        givenReceptionStateRows();
        givenExpectationRaised(1L);

        publish(subject(), "E-GAP", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN)).as("момент разрыва поставлен").isNotNull();
        assertThat(dealFacts())
                .as("факт самой записи лёг: разрыв приёму не мешает — затравка и запись с пропуском")
                .hasSize(2);
        assertThat(pair(subject()).get(HALTED_COLUMN))
                .as("приём при этом не останавливался").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable()).as("предикат непрерывности ложен").isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B4.7 — Одна дыра объявляется один раз")
    void aSingleHoleIsDeclaredExactlyOnce() {
        givenReceptionStateRows();
        givenExpectationRaised(1L);
        publish(subject(), "E-GAP", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(subject());
        Object gapMoment = pair(subject()).get(GAP_COLUMN);
        assertThat(gapMoment).as("момент разрыва поставлен записью с пропуском").isNotNull();

        for (String eventId : AFTER_GAP) {
            publish(subject(), eventId, DEAL_CLOSED, momentsAgo(Duration.ofMinutes(1)),
                    Bodies.dealClosed(ACCOUNT, STRATEGY));
        }
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("идущие следом записи момента не переписывают: ожидание сдвигается и при разрыве")
                .isEqualTo(gapMoment);
        assertThat(dealFacts())
                .as("легли все: затравка, запись с пропуском и три следом").hasSize(5);
    }

    @Test
    @DisplayName("B4.9 — Момент разрыва не гаснет никогда, и это объявлено верным")
    void theGapMomentNeverFades() {
        givenReceptionStateRows();
        givenExpectationRaised(1L);
        publish(subject(), "E-GAP", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(subject());
        Object gapMoment = pair(subject()).get(GAP_COLUMN);
        assertThat(gapMoment).as("момент разрыва поставлен").isNotNull();

        for (String eventId : AFTER_GAP) {
            publish(subject(), eventId, DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                    Bodies.dealClosed(ACCOUNT, STRATEGY));
        }
        awaitConsumed(subject());
        tick();
        tick();
        recompute();
        recompute();

        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("момент разрыва на месте: гасящего писателя у величины нет ни одного")
                .isEqualTo(gapMoment);
        assertThat(continuityClaimable())
                .as("предикат непрерывности ложен и после того, как приём догнал тему")
                .isEqualTo(Boolean.FALSE);
        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows()).as("страница агрегатов собрана").isNotEmpty();
        assertThat(page.completeness().get("continuityClaimable"))
                .as("и несёт эту ложь вместе с числами").isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B4.10 — Разрыв на одной теме предикат соседней не роняет")
    void aGapOnOneTopicDoesNotBreakTheNeighbouringPair() {
        givenReceptionStateRows();
        publish(neighbour(), "E-NEIGHBOUR", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(neighbour());
        String neighbourVersion = pairVersion(neighbour());

        givenExpectationRaised(2L);
        publish(subject(), "E-GAP", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN)).as("момент разрыва поставлен у своей пары").isNotNull();
        assertThat(pair(neighbour()).get(GAP_COLUMN)).as("у второй он пуст").isNull();
        assertThat(pair(neighbour()).get(HALTED_COLUMN))
                .as("и приём по ней не остановлен").isEqualTo(Boolean.FALSE);
        assertThat(pairVersion(neighbour()))
                .as("строка второй пары не тронута вовсе").isEqualTo(neighbourVersion);
        assertThat(continuityClaimable())
                .as("групповой предикат ложен: дыра хоть на одной теме есть дыра в принятом")
                .isEqualTo(Boolean.FALSE);
    }

    /** Тема, в которую клетки кладут записи с пропуском. */
    private String subject() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /** Вторая тема подписки: ею наблюдается радиус разрыва. */
    private String neighbour() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }

    /**
     * Поднимает КОНЕЦ темы над ожидаемым смещением, не двигая самого ожидания:
     * запись едет транзакцией, и её коммит кладёт управляющую запись, которой
     * потребитель не видит.
     *
     * <p>Конец обработки здесь наблюдается ФАКТОМ, а не смещением: равенство
     * «конец темы = зафиксированное смещение» на теме с управляющей записью не
     * наступает, пока не придёт следующая обычная.
     *
     * @param factsAfter сколько фактов лежит, когда затравка принята
     */
    private void givenExpectationRaised(Long factsAfter) {
        Wire.publishInTransaction(subject(), TENANT,
                envelope("E-SEED", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK)),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitDealFactCount(factsAfter);
    }
}
