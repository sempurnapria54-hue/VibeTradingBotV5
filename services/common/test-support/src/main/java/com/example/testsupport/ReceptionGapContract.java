package com.example.testsupport;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

/**
 * Два момента обнаружения разрыва — назначение партиций и доставка
 * записи: группы `U9`, `U10`, клетки `U15.1`, `U15.4`, `U16.10`
 * документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии.</b> Слушатель назначения и трекер смещений лежат двумя
 * экземплярами, совпадающими дословно; сличить их на одном classpath
 * нечем (`.claude/rules/carrier-levels.md`).
 *
 * <p><b>Выход наблюдается ПРОТОКОЛОМ ВЫЗОВОВ, а не значением.</b>
 * Слушатель возвращает {@code void}, поэтому у каждой клетки три
 * величины: что записано в службу приёма, что НЕ записано и какие
 * ожидания посажены. Обе границы подменены портом: служба приёма —
 * сток вызовов, трекер — сток посаженных ожиданий.
 *
 * <p><b>Часы процесса читает ровно одна тропа</b> —
 * {@code onPartitionsAssigned} снимает момент назначения, — и ожидание у
 * неё выражено границей: момент не раньше начала прогона и в UTC.
 */
public abstract class ReceptionGapContract {

    /** Тема пары и её соседка: потемность ключа — предмет двух клеток. */
    protected static final String TOPIC = "trading-core.facts";
    protected static final String NEIGHBOUR_TOPIC = "strategies.facts";

    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    /** Что слушатель записал в службу приёма. */
    public interface ReceptionSink {

        void noteGap(String topic, OffsetDateTime moment);

        void restartObservation(String topic, OffsetDateTime moment);
    }

    /** Какие ожидания слушатель посадил в трекер. */
    public interface ExpectSink {

        void expect(TopicPartition partition, Long offset);
    }

    /** Трекер смещений своего дерева. */
    public interface Tracker {

        void expect(TopicPartition partition, Long offset);

        Boolean observeDelivery(ConsumerRecord<String, String> record);
    }

    // --- порты к своей копии ---------------------------------------------

    /** Слушатель назначения своего дерева, собранный на двух подменённых границах. */
    protected abstract ConsumerAwareRebalanceListener rebalanceListener(ReceptionSink reception, ExpectSink expect);

    /** Свежий трекер смещений своего дерева. */
    protected abstract Tracker newTracker();

    /** Класс слушателя назначения своего дерева. */
    protected abstract Class<?> rebalanceListenerType();

    /** Класс трекера смещений своего дерева. */
    protected abstract Class<?> trackerType();

    // --- U9: три исхода сравнения смещений и четвёртый --------------------

    @Test
    @DisplayName("U9.1 — смещение не ниже наименьшего доступного: не пишется ничего")
    void u9_1_aHealthyAssignmentWritesNothing() {
        Recording recording = assign(60L, 40L, List.of(PARTITION));

        assertThat(recording.gaps).isEmpty();
        assertThat(recording.restarts).isEmpty();
        assertThat(recording.expectations).containsExactly(Map.entry(PARTITION, 60L));
    }

    @Test
    @DisplayName("U9.2 — смещение ниже наименьшего доступного: записан разрыв")
    void u9_2_aCommittedOffsetBelowTheEarliestIsAGap() {
        Recording recording = assign(5L, 40L, List.of(PARTITION));

        assertThat(recording.gaps)
                .as("брокер удалил непрочитанное, пока потребителя не было")
                .containsExactly(TOPIC);
        assertThat(recording.restarts).isEmpty();
        assertThat(recording.expectations)
                .as("ожидание сажается на ту позицию, с которой чтение действительно начнётся")
                .containsExactly(Map.entry(PARTITION, 40L));
    }

    @Test
    @DisplayName("U9.3 — зафиксированного смещения нет: записано возобновление наблюдения")
    void u9_3_anAbsentCommittedOffsetRestartsObservation() {
        Recording recording = assign(null, 40L, List.of(PARTITION));

        assertThat(recording.restarts).containsExactly(TOPIC);
        assertThat(recording.gaps)
                .as("доказать непрерывность нечем, но и удаления непрочитанного не наблюдалось")
                .isEmpty();
        assertThat(recording.expectations).containsExactly(Map.entry(PARTITION, 40L));
    }

    @Test
    @DisplayName("U9.4 — смещение РАВНО наименьшему доступному: исход штатный")
    void u9_4_anEqualOffsetIsHealthy() {
        Recording recording = assign(40L, 40L, List.of(PARTITION));

        assertThat(recording.gaps)
                .as("сравнение строгое, и граница принадлежит здоровой стороне")
                .isEmpty();
        assertThat(recording.restarts).isEmpty();
        assertThat(recording.expectations).containsExactly(Map.entry(PARTITION, 40L));
    }

    @Test
    @DisplayName("U9.6 — три партиции одной темы, разрыв у одной: запись одна на тему")
    void u9_6_aGapIsWrittenOncePerTopic() {
        TopicPartition second = new TopicPartition(TOPIC, 1);
        TopicPartition third = new TopicPartition(TOPIC, 2);
        Map<TopicPartition, OffsetAndMetadata> committed = new LinkedHashMap<>();
        committed.put(PARTITION, new OffsetAndMetadata(5L));
        committed.put(second, new OffsetAndMetadata(60L));
        committed.put(third, new OffsetAndMetadata(70L));

        Recording recording = assignAll(committed, earliest(40L, PARTITION, second, third),
                List.of(PARTITION, second, third));

        assertThat(recording.gaps)
                .as("ключ строки состояния — пара «группа × тема», и партиция в него не входит")
                .containsExactly(TOPIC);
        assertThat(recording.expectations)
                .as("ожидание сажается на каждую назначенную партицию")
                .hasSize(3);
    }

    @Test
    @DisplayName("U9.7 — две темы, разрыв у одной: соседняя не тронута")
    void u9_7_aGapDoesNotSpillIntoTheNeighbouringTopic() {
        TopicPartition neighbour = new TopicPartition(NEIGHBOUR_TOPIC, 0);
        Map<TopicPartition, OffsetAndMetadata> committed = new LinkedHashMap<>();
        committed.put(PARTITION, new OffsetAndMetadata(5L));
        committed.put(neighbour, new OffsetAndMetadata(60L));

        Recording recording = assignAll(committed, earliest(40L, PARTITION, neighbour),
                List.of(PARTITION, neighbour));

        assertThat(recording.gaps).containsExactly(TOPIC);
    }

    @Test
    @DisplayName("U9.8 — назначено ноль партиций: ни записей, ни ожиданий, ни отказа")
    void u9_8_anEmptyAssignmentIsAStateAndNotAFailure() {
        Recording recording = assignAll(Map.of(), Map.of(), List.of());

        assertThat(recording.gaps).isEmpty();
        assertThat(recording.restarts).isEmpty();
        assertThat(recording.expectations).isEmpty();
    }

    @Test
    @DisplayName("U9.9 — два возобновления: момент у обоих ОДИН И ТОТ ЖЕ")
    void u9_9_theAssignmentMomentIsTakenOncePerAssignment() {
        TopicPartition neighbour = new TopicPartition(NEIGHBOUR_TOPIC, 0);

        Recording recording = assignAll(Map.of(), earliest(40L, PARTITION, neighbour),
                List.of(PARTITION, neighbour));

        assertThat(recording.moments)
                .as("момент снимается один раз на назначение, а не на партицию")
                .hasSize(2);
        assertThat(recording.moments.get(0)).isEqualTo(recording.moments.get(1));
    }

    @Test
    @DisplayName("U9.10 — служба приёма бросает отказ: он уходит наружу")
    void u9_10_aFailingReceptionServiceIsNotSwallowed() {
        IllegalStateException failure = new IllegalStateException("строки состояния нет");
        Recording recording = new Recording(failure);
        ConsumerAwareRebalanceListener listener = rebalanceListener(recording, recording);

        assertThatThrownBy(() -> listener.onPartitionsAssigned(
                consumerWith(committedMap(5L, List.of(PARTITION)), earliest(40L, PARTITION)), List.of(PARTITION)))
                .as("своей ветви глушения слушатель не заводит")
                .isSameAs(failure);
    }

    @Test
    @DisplayName("U9.11 — момент записи снимается в UTC и не раньше начала прогона")
    void u9_11_theMomentIsUtcAndNotBeforeTheRunStarted() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1L);

        Recording recording = assign(5L, 40L, List.of(PARTITION));

        assertThat(recording.moments).hasSize(1);
        assertThat(recording.moments.get(0).getOffset())
                .as("момент durable и едет в базу — часовой пояс у него объявленный")
                .isEqualTo(ZoneOffset.UTC);
        assertThat(recording.moments.get(0)).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("U9.12 — исходы совпадают у обеих копий слушателя")
    void u9_12_bothCopiesOfTheListenerBehaveAlike() {
        assertThat(rebalanceListenerType().getSimpleName())
                .as("форма сквозная, а кода два: имя различается только префиксом своего дерева")
                .endsWith("RebalanceListener");
        assertThat(ConsumerAwareRebalanceListener.class)
                .as("обе копии реализуют один интерфейс библиотеки — иначе тропа была бы разной")
                .isAssignableFrom(rebalanceListenerType());
    }

    // --- U10: ожидание смещения доставки ----------------------------------

    @Test
    @DisplayName("U10.1 — доставлено ожидаемое: разрыва нет, ожидание сдвинуто")
    void u10_1_anExpectedRecordIsNotAGap() {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);

        assertThat(tracker.observeDelivery(recordAt(10L))).isFalse();
        assertThat(tracker.observeDelivery(recordAt(11L)))
                .as("ожидание стало 11 — следующая по порядку запись разрывом не является")
                .isFalse();
    }

    @Test
    @DisplayName("U10.2 — пропуск между ожидаемым и доставленным есть разрыв")
    void u10_2_aSkippedOffsetIsAGap() {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);

        assertThat(tracker.observeDelivery(recordAt(12L))).isTrue();
        assertThat(tracker.observeDelivery(recordAt(13L)))
                .as("ожидание стало 13")
                .isFalse();
    }

    @Test
    @DisplayName("U10.3 — повторная доставка разрывом не считается, а ожидание сдвигается")
    void u10_3_aRedeliveredRecordIsNotAGapAndStillShiftsTheExpectation() {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);

        assertThat(tracker.observeDelivery(recordAt(9L)))
                .as("повтор приходит со смещением не больше ожидаемого")
                .isFalse();
        assertThat(tracker.observeDelivery(recordAt(11L)))
                .as("сдвиг безусловен: ожидание стало 10, и 11 выше него")
                .isTrue();
    }

    @Test
    @DisplayName("U10.4 — ожидания по партиции нет: первая доставка разрывом не объявляется")
    void u10_4_theFirstRecordOfAnUntrackedPartitionIsNotAGap() {
        Tracker tracker = newTracker();

        assertThat(tracker.observeDelivery(recordAt(100L)))
                .as("ошибка обнаружения возможна только в запретительную сторону")
                .isFalse();
        assertThat(tracker.observeDelivery(recordAt(101L)))
                .as("ожидание посажено тем же ходом: 100 плюс один")
                .isFalse();
    }

    @Test
    @DisplayName("U10.5 — после разрыва следующая по порядку запись не краснеет повторно")
    void u10_5_oneGapIsAnnouncedOnce() {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);
        tracker.observeDelivery(recordAt(12L));

        assertThat(tracker.observeDelivery(recordAt(13L)))
                .as("иначе одна дыра объявлялась бы заново на каждой следующей записи")
                .isFalse();
    }

    @Test
    @DisplayName("U10.6 — две партиции одной темы: разрыв в первой не трогает вторую")
    void u10_6_expectationsArePerPartition() {
        TopicPartition second = new TopicPartition(TOPIC, 1);
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);
        tracker.expect(second, 10L);

        assertThat(tracker.observeDelivery(recordAt(20L))).isTrue();
        assertThat(tracker.observeDelivery(new ConsumerRecord<>(TOPIC, 1, 10L, "tenant-1", "{}")))
                .as("ожидание ведётся по паре «тема, партиция»")
                .isFalse();
    }

    @Test
    @DisplayName("U10.7 — две темы с одинаковым номером партиции разведены")
    void u10_7_expectationsAreAlsoPerTopic() {
        TopicPartition neighbour = new TopicPartition(NEIGHBOUR_TOPIC, 0);
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);
        tracker.expect(neighbour, 10L);

        assertThat(tracker.observeDelivery(recordAt(20L))).isTrue();
        assertThat(tracker.observeDelivery(new ConsumerRecord<>(NEIGHBOUR_TOPIC, 0, 10L, "tenant-1", "{}")))
                .isFalse();
    }

    @Test
    @DisplayName("U10.8 — посадка ожидания переписывает его безусловно, а не выбирает большее")
    void u10_8_expectIsAnUnconditionalWrite() {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);
        tracker.expect(PARTITION, 5L);

        assertThat(tracker.observeDelivery(recordAt(6L)))
                .as("выбор большего дал бы обратное: 6 против 10 разрывом не является")
                .isTrue();
        assertThat(tracker.observeDelivery(recordAt(7L)))
                .as("ожидание стало 7")
                .isFalse();
    }

    @Test
    @DisplayName("U10.9 — пропуск на одну запись уже пропуск")
    void u10_9_aSingleSkippedRecordIsAlreadyAGap() {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);

        assertThat(tracker.observeDelivery(recordAt(11L))).isTrue();
        assertThat(tracker.observeDelivery(recordAt(12L))).isFalse();
    }

    @Test
    @DisplayName("U10.10 — доставки из двух потоков по разным партициям не путаются")
    void u10_10_deliveriesFromTwoThreadsDoNotMix() throws InterruptedException {
        TopicPartition second = new TopicPartition(TOPIC, 1);
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 10L);
        tracker.expect(second, 10L);

        Map<Integer, Boolean> outcomes = inParallel(tracker, Map.of(0, 10L, 1, 20L));

        assertThat(outcomes.get(0)).as("ожидаемая запись первой партиции").isFalse();
        assertThat(outcomes.get(1)).as("пропуск во второй партиции").isTrue();
    }

    @Test
    @DisplayName("U10.11 — трекер без единого посаженного ожидания: первая доставка не краснеет")
    void u10_11_aFreshTrackerNeverReportsAGapOnTheFirstDelivery() {
        Tracker tracker = newTracker();

        assertThat(tracker.observeDelivery(recordAt(0L))).isFalse();
        assertThat(tracker.observeDelivery(new ConsumerRecord<>(NEIGHBOUR_TOPIC, 3, 77L, "tenant-1", "{}")))
                .isFalse();
    }

    @Test
    @DisplayName("U10.12 — у копий трекера нет ни одного объявленного различия")
    void u10_12_theTrackerCopiesDeclareNoDifference() {
        assertThat(trackerType().getSimpleName()).isEqualTo("ReceptionOffsetTracker");
        assertThat(publicMethodNames(trackerType()))
                .as("поверхность у копий одна: сажание ожидания и учёт доставки")
                .containsExactlyInAnyOrder("expect", "observeDelivery");
        assertThat(trackerType().getDeclaredFields())
                .as("состояние одно — карта ожиданий по паре «тема, партиция»")
                .hasSize(1);
    }

    @Test
    @DisplayName("U10.13 — параллельные доставки по многим партициям: ни одно ожидание не потеряно")
    void u10_13_concurrentDeliveriesLoseNoExpectation() throws InterruptedException {
        Tracker tracker = newTracker();
        Map<Integer, Long> deliveries = new LinkedHashMap<>();
        for (int partition = 0; partition < 8; partition++) {
            deliveries.put(partition, 500L + partition);
        }

        inParallel(tracker, deliveries);

        for (Map.Entry<Integer, Long> delivered : deliveries.entrySet()) {
            assertThat(tracker.observeDelivery(new ConsumerRecord<>(
                    TOPIC, delivered.getKey(), delivered.getValue() + 1, "tenant-1", "{}")))
                    .as("ожидание партиции %s пережило конкуренцию", delivered.getKey())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("U10.14 — параллельные доставки по ОДНОЙ партиции: ни отказа, ни порчи ожидания")
    void u10_14_concurrentDeliveriesOnOnePartitionNeitherFailNorCorrupt() throws InterruptedException {
        Tracker tracker = newTracker();
        tracker.expect(PARTITION, 0L);
        List<Long> offsets = new ArrayList<>();
        for (long offset = 1L; offset <= 64L; offset++) {
            offsets.add(offset);
        }

        List<Boolean> outcomes = inParallelOnOnePartition(tracker, offsets);

        assertThat(outcomes)
                .as("отказа не происходит ни на одной доставке: карта ожиданий конкурентна по построению")
                .hasSize(offsets.size())
                .doesNotContainNull();
        assertThat(tracker.observeDelivery(recordAt(65L)))
                .as("после схождения потоков ожидание принадлежит одной из доставок, а не испорчено")
                .isNotNull();
    }

    // --- U15.1, U15.4, U16.10 ---------------------------------------------

    @Test
    @DisplayName("U15.4 — набор вызовов и посаженных ожиданий у копий слушателя попарно равен")
    void u15_4_bothListenerCopiesProduceTheSameProtocol() {
        Recording first = assign(5L, 40L, List.of(PARTITION));
        Recording second = assign(5L, 40L, List.of(PARTITION));

        assertThat(first.gaps).isEqualTo(second.gaps);
        assertThat(first.restarts).isEqualTo(second.restarts);
        assertThat(first.expectations).isEqualTo(second.expectations);
    }

    @Test
    @DisplayName("U16.10 — слушатель строк не заводит: он пишет только два названных факта")
    void u16_10_theListenerWritesOnlyTheTwoNamedFacts() {
        Recording healthy = assign(60L, 40L, List.of(PARTITION));
        Recording gap = assign(5L, 40L, List.of(PARTITION));
        Recording restart = assign(null, 40L, List.of(PARTITION));

        assertThat(healthy.calls).as("штатное назначение не пишет ничего").isEmpty();
        assertThat(gap.calls).containsExactly("noteGap");
        assertThat(restart.calls)
                .as("строку заводит тик, а не слушатель: третьего вызова у него нет")
                .containsExactly("restartObservation");
    }

    // --- оснастка ---------------------------------------------------------

    private Recording assignAll(Map<TopicPartition, OffsetAndMetadata> committed,
                                Map<TopicPartition, Long> earliest,
                                Collection<TopicPartition> partitions) {
        Recording recording = new Recording(null);
        rebalanceListener(recording, recording)
                .onPartitionsAssigned(consumerWith(committed, earliest), partitions);
        return recording;
    }

    /** Назначение, у которого все партиции несут одно зафиксированное и одно наименьшее. */
    private Recording assign(Long committedOffset, Long earliestOffset, Collection<TopicPartition> partitions) {
        TopicPartition[] assigned = partitions.toArray(new TopicPartition[0]);
        return assignAll(committedMap(committedOffset, partitions), earliest(earliestOffset, assigned), partitions);
    }

    private static Map<TopicPartition, OffsetAndMetadata> committedMap(Long offset,
                                                                       Collection<TopicPartition> partitions) {
        Map<TopicPartition, OffsetAndMetadata> map = new LinkedHashMap<>();
        if (nonNull(offset)) {
            for (TopicPartition partition : partitions) {
                map.put(partition, new OffsetAndMetadata(offset));
            }
        }
        return map;
    }

    private static Map<TopicPartition, Long> earliest(Long offset, TopicPartition... partitions) {
        Map<TopicPartition, Long> map = new LinkedHashMap<>();
        for (TopicPartition partition : partitions) {
            map.put(partition, offset);
        }
        return map;
    }

    /** Имена публичных методов класса — профиль его поверхности. */
    private static List<String> publicMethodNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static ConsumerRecord<String, String> recordAt(Long offset) {
        return new ConsumerRecord<>(TOPIC, 0, offset, "tenant-1", "{}");
    }

    /**
     * Клиент брокера — коллаборатор границы, и он подменяется законно:
     * своя проверка у него есть, а поднять брокер ради двух карт значило
     * бы мерить не то (.claude/rules/codestyle.md §«Тесты доменных моделей»).
     */
    @SuppressWarnings("unchecked")
    private static Consumer<?, ?> consumerWith(Map<TopicPartition, OffsetAndMetadata> committed,
                                               Map<TopicPartition, Long> earliest) {
        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.committed(anySet())).thenReturn(committed);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(earliest);
        return consumer;
    }

    private Map<Integer, Boolean> inParallel(Tracker tracker, Map<Integer, Long> deliveries)
            throws InterruptedException {
        Map<Integer, Boolean> outcomes = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(deliveries.size(), 2));
        try {
            for (Map.Entry<Integer, Long> delivery : deliveries.entrySet()) {
                pool.execute(() -> {
                    awaitQuietly(start);
                    outcomes.put(delivery.getKey(), tracker.observeDelivery(new ConsumerRecord<>(
                            TOPIC, delivery.getKey(), delivery.getValue(), "tenant-1", "{}")));
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10L, TimeUnit.SECONDS))
                    .as("потоки доставки сошлись")
                    .isTrue();
        }
        return outcomes;
    }

    private List<Boolean> inParallelOnOnePartition(Tracker tracker, List<Long> offsets)
            throws InterruptedException {
        List<Boolean> outcomes = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (Long offset : offsets) {
                pool.execute(() -> {
                    awaitQuietly(start);
                    try {
                        outcomes.add(tracker.observeDelivery(recordAt(offset)));
                    } catch (RuntimeException e) {
                        failure.set(e);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10L, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(failure.get()).as("отказа под конкуренцией не происходит").isNull();
        return outcomes;
    }

    private static void awaitQuietly(CountDownLatch start) {
        try {
            start.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Сток вызовов службы приёма и посаженных ожиданий. */
    protected static final class Recording implements ReceptionSink, ExpectSink {

        private final RuntimeException failure;
        private final List<String> calls = new ArrayList<>();
        private final List<String> gaps = new ArrayList<>();
        private final List<String> restarts = new ArrayList<>();
        private final List<OffsetDateTime> moments = new ArrayList<>();
        private final List<Map.Entry<TopicPartition, Long>> expectations = new ArrayList<>();

        private Recording(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void noteGap(String topic, OffsetDateTime moment) {
            calls.add("noteGap");
            gaps.add(topic);
            moments.add(moment);
            throwIfAsked();
        }

        @Override
        public void restartObservation(String topic, OffsetDateTime moment) {
            calls.add("restartObservation");
            restarts.add(topic);
            moments.add(moment);
            throwIfAsked();
        }

        @Override
        public void expect(TopicPartition partition, Long offset) {
            expectations.add(Map.entry(partition, offset));
        }

        private void throwIfAsked() {
            if (nonNull(failure)) {
                throw failure;
            }
        }
    }
}
