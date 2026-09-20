package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Остаток непринятого по теме, как его отдаёт клиент потребителя — группа
 * `U13` и клетки `U15.3`, `U16.7` документа
 * `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии</b> (`.claude/rules/carrier-levels.md`): провайдер лежит двумя
 * дословными экземплярами.
 *
 * <p><b>Предмет операнда — не число, а РАЗЛИЧЕНИЕ трёх состояний:</b>
 * измерено, измерено и равно нулю, не измерено. Первые два обязаны
 * приехать числом, третье — пустотой: ноль на месте неизвестности гасил
 * бы алерт ровно там, где измеритель не мерит.
 */
public abstract class ConsumerLagProviderContract {

    /** Тема пары и её соседка: величина потемная, и это предмет двух клеток. */
    protected static final String TOPIC = "trading-core.facts";
    protected static final String NEIGHBOUR_TOPIC = "strategies.facts";

    private static final String RECORDS_LAG = "records-lag";
    private static final String GROUP_OF_METRICS = "consumer-fetch-manager-metrics";

    // --- порты к своей копии ---------------------------------------------

    /** Остаток непринятого, как его считает копия своего дерева. */
    protected abstract Optional<Long> unconsumedRecords(Collection<MessageListenerContainer> containers,
                                                        String topic);

    /** Класс провайдера своего дерева. */
    protected abstract Class<?> consumerLagProviderType();

    // --- U13: потемность, ноль против пустоты, `NaN` -----------------------

    @Test
    @DisplayName("U13.1 — лаг темы суммируется по её партициям, а не по клиенту")
    void u13_1_theTopicLagSumsItsOwnPartitions() {
        MessageListenerContainer container = containerWith(Map.of(
                partitionLag(TOPIC, "0"), 5.0,
                partitionLag(TOPIC, "1"), 7.0,
                partitionLag(NEIGHBOUR_TOPIC, "0"), 100.0));

        assertThat(unconsumedRecords(List.of(container), TOPIC))
                .as("сумма по группе маскировала бы остановку на одной теме молчанием другой")
                .contains(12L);
    }

    @Test
    @DisplayName("U13.2 — известный ноль приезжает нулём и гасит алерт")
    void u13_2_aKnownZeroArrivesAsAZero() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(TOPIC, "0"), 0.0));

        assertThat(unconsumedRecords(List.of(container), TOPIC))
                .as("ноль означает «принимать нечего»: производитель молчит, а не потребитель встал")
                .contains(0L);
    }

    @Test
    @DisplayName("U13.3 — ряда по своей теме нет вовсе: пусто, а не ноль")
    void u13_3_anAbsentSeriesIsNotAZero() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(NEIGHBOUR_TOPIC, "0"), 3.0));

        assertThat(unconsumedRecords(List.of(container), TOPIC))
                .as("пустота означает «неизвестно» и алерт гасить не вправе")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.4 — единственная партиция со значением `NaN`: пусто")
    void u13_4_aNotANumberIsNotTakenAsAValue() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(TOPIC, "0"), Double.NaN));

        assertThat(unconsumedRecords(List.of(container), TOPIC))
                .as("взятый числом, `NaN` сделал бы ложным всякое сравнение — то есть погасил бы алерт тише всех")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.5 — `NaN` у одной партиции и 5 у другой: отдаётся 5")
    void u13_5_anUnmeasuredPartitionDoesNotZeroTheMeasuredOne() {
        MessageListenerContainer container = containerWith(Map.of(
                partitionLag(TOPIC, "0"), Double.NaN,
                partitionLag(TOPIC, "1"), 5.0));

        assertThat(unconsumedRecords(List.of(container), TOPIC)).contains(5L);
    }

    @Test
    @DisplayName("U13.6 — бесконечное значение читается той же ветвью, что `NaN`")
    void u13_6_anInfiniteValueIsTheSameBranchAsNotANumber() {
        MessageListenerContainer container = containerWith(Map.of(
                partitionLag(TOPIC, "0"), Double.POSITIVE_INFINITY));

        assertThat(unconsumedRecords(List.of(container), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U13.7 — сводные метрики клиента не берутся: только попартиционная")
    void u13_7_onlyThePerPartitionMetricIsTaken() {
        Map<MetricName, Double> byClient = new LinkedHashMap<>();
        byClient.put(named("records-lag-max", Map.of("client-id", "c")), 999.0);
        byClient.put(named("records-lag-avg", Map.of("topic", TOPIC, "partition", "0")), 500.0);

        assertThat(unconsumedRecords(List.of(containerWith(byClient)), TOPIC))
                .as("сумма по группе маскировала бы остановку на одной теме")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.8 — у метрики `records-lag` нет метки темы: значение не берётся")
    void u13_8_aMetricWithoutATopicTagIsNotTaken() {
        MessageListenerContainer container = containerWith(Map.of(
                named(RECORDS_LAG, Map.of("client-id", "c")), 42.0));

        assertThat(unconsumedRecords(List.of(container), TOPIC))
                .as("без метки темы величина не потемная")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.9 — два контейнера со своими партициями: суммируются оба")
    void u13_9_everyPassedContainerIsWalked() {
        MessageListenerContainer first = containerWith(Map.of(partitionLag(TOPIC, "0"), 5.0));
        MessageListenerContainer second = containerWith(Map.of(partitionLag(TOPIC, "1"), 7.0));

        assertThat(unconsumedRecords(List.of(first, second), TOPIC)).contains(12L);
    }

    @Test
    @DisplayName("U13.10 — контейнеров ноль: пусто")
    void u13_10_anEmptyContainerListYieldsAbsence() {
        assertThat(unconsumedRecords(List.of(), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U13.11 — значение метрики не число: не берётся")
    void u13_11_aNonNumericValueIsNotTaken() {
        MessageListenerContainer container = containerOf(Map.of(
                partitionLag(TOPIC, "0"), metricOf("не число")));

        assertThat(unconsumedRecords(List.of(container), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U13.12 — дробное значение: берётся целая часть")
    void u13_12_aFractionalValueIsTruncated() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(TOPIC, "0"), 5.7));

        assertThat(unconsumedRecords(List.of(container), TOPIC))
                .as("величина мерится в смещениях — дробных смещений не бывает")
                .contains(5L);
    }

    @Test
    @DisplayName("U13.13 — перечень контейнеров приходит параметром, а не из реестра")
    void u13_13_theContainersArriveAsAParameter() {
        List<String> fields = new ArrayList<>();
        for (Field field : consumerLagProviderType().getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                fields.add(field.getName());
            }
        }

        assertThat(fields)
                .as("второго обходчика подписки не заводится: он застал бы другой состав контейнеров")
                .isEmpty();
    }

    @Test
    @DisplayName("U13.14 — у копий провайдера нет ни одного объявленного различия")
    void u13_14_theProviderCopiesDeclareNoDifference() {
        assertThat(consumerLagProviderType().getSimpleName()).isEqualTo("ConsumerLagProvider");
        assertThat(publicMethodNames(consumerLagProviderType()))
                .as("поверхность у копий одна: остаток непринятого по названной теме")
                .containsExactly("unconsumedRecords");
    }

    // --- U15.3, U16.7 -----------------------------------------------------

    @Test
    @DisplayName("U15.3 — три состояния величины у копий совпадают")
    void u15_3_bothCopiesAgreeOnAllThreeStates() {
        assertThat(unconsumedRecords(List.of(containerWith(Map.of(partitionLag(TOPIC, "0"), 5.0))), TOPIC))
                .contains(5L);
        assertThat(unconsumedRecords(List.of(containerWith(Map.of(partitionLag(TOPIC, "0"), 0.0))), TOPIC))
                .contains(0L);
        assertThat(unconsumedRecords(List.of(containerWith(Map.of())), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U16.7 — к брокеру провайдер не ходит: величина берётся у клиента потребителя")
    void u16_7_theProviderNeverReachesTheBroker() {
        List<String> parameterTypes = new ArrayList<>();
        for (Method method : consumerLagProviderType().getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                parameterTypes.add(parameter.getName());
            }
        }

        assertThat(parameterTypes)
                .as("второй административный вызов дал бы остаток по ЗАФИКСИРОВАННОМУ смещению")
                .noneMatch(name -> name.contains("admin") || name.contains("Admin"));
    }

    // --- оснастка ---------------------------------------------------------

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

    private static MessageListenerContainer containerWith(Map<MetricName, Double> values) {
        Map<MetricName, Metric> byClient = new LinkedHashMap<>();
        values.forEach((name, value) -> byClient.put(name, metricOf(value)));
        return containerOf(byClient);
    }

    private static MessageListenerContainer containerOf(Map<MetricName, Metric> byClient) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        Map<String, Map<MetricName, ? extends Metric>> byClientId = Map.of("client", byClient);
        when(container.metrics()).thenReturn(byClientId);
        return container;
    }

    private static MetricName partitionLag(String topic, String partition) {
        return named(RECORDS_LAG, Map.of("client-id", "c", "topic", topic, "partition", partition));
    }

    private static MetricName named(String name, Map<String, String> tags) {
        return new MetricName(name, GROUP_OF_METRICS, "", tags);
    }

    private static Metric metricOf(Object value) {
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn(value);
        return metric;
    }
}
