package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.integration.internal.event.ConsumerLagProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Остаток непринятого по теме, как его отдаёт клиент потребителя
 * (docs/spec/audit-journal.json, операнд {@code unconsumedRecords}).
 *
 * <p><b>Что здесь проверяется по существу.</b> Предмет операнда — не
 * число, а различение «принимать нечего» (ноль, алерт гасит) и «не
 * известно» (пусто, гасить не вправе). Поэтому обе ветви проверяются
 * парой, и рядом с ними — потемность: сумма по клиенту маскировала бы
 * остановку на одной теме молчанием другой.
 */
class ConsumerLagTest {

    private static final String CORE_TOPIC = "trading-core.facts";
    private static final String STRATEGIES_TOPIC = "strategies.facts";
    private static final String LAG = "records-lag";

    private final ConsumerLagProvider provider = new ConsumerLagProvider();

    @Test
    @DisplayName("Лаг темы — сумма по её партициям, а не по клиенту")
    void theTopicLagSumsItsOwnPartitions() {
        MessageListenerContainer container = containerWith(Map.of(
                partitionLag(CORE_TOPIC, "0"), 5.0,
                partitionLag(CORE_TOPIC, "1"), 12.0,
                partitionLag(STRATEGIES_TOPIC, "0"), 100.0));

        assertThat(provider.unconsumedRecords(List.of(container), CORE_TOPIC))
                .as("чужая тема в сумму не входит: потемность и есть предмет операнда")
                .contains(17L);
    }

    @Test
    @DisplayName("Известный ноль приезжает нулём — он гасит алерт")
    void aKnownZeroArrivesAsAZero() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(CORE_TOPIC, "0"), 0.0));

        assertThat(provider.unconsumedRecords(List.of(container), CORE_TOPIC))
                .as("ноль означает «принимать нечего»: производитель молчит, а не потребитель встал")
                .contains(0L);
    }

    @Test
    @DisplayName("Ряда по теме нет вовсе — пусто, а не ноль")
    void anAbsentSeriesIsNotAZero() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(STRATEGIES_TOPIC, "0"), 3.0));

        assertThat(provider.unconsumedRecords(List.of(container), CORE_TOPIC))
                .as("пустота означает «неизвестно» и алерт гасить не вправе")
                .isEmpty();
    }

    @Test
    @DisplayName("NaN клиента читается как пустота, а не как число")
    void aNotANumberReadsAsAbsence() {
        MessageListenerContainer container = containerWith(Map.of(partitionLag(CORE_TOPIC, "0"), Double.NaN));

        assertThat(provider.unconsumedRecords(List.of(container), CORE_TOPIC))
                .as("взятый числом, NaN сделал бы ложным всякое сравнение — то есть погасил бы алерт тише всех")
                .isEmpty();
    }

    @Test
    @DisplayName("Берётся именно records-lag, а не сводные метрики клиента")
    void onlyThePerPartitionLagIsTaken() {
        Map<MetricName, Metric> byClient = new LinkedHashMap<>();
        byClient.put(named("records-lag-max", Map.of("client-id", "c")), metric(999.0));
        byClient.put(named("records-lag-avg", Map.of("topic", CORE_TOPIC, "partition", "0")), metric(500.0));
        byClient.put(partitionLag(CORE_TOPIC, "0"), metric(5.0));

        assertThat(provider.unconsumedRecords(List.of(containerOf(byClient)), CORE_TOPIC))
                .as("сводная метрика клиента метки темы не несёт вовсе — по ней величина стала бы групповой")
                .contains(5L);
    }

    private MessageListenerContainer containerWith(Map<MetricName, Double> values) {
        Map<MetricName, Metric> byClient = new LinkedHashMap<>();
        values.forEach((name, value) -> byClient.put(name, metric(value)));
        return containerOf(byClient);
    }

    private MessageListenerContainer containerOf(Map<MetricName, Metric> byClient) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        Map<String, Map<MetricName, ? extends Metric>> byClientId = Map.of("client", byClient);
        when(container.metrics()).thenReturn(byClientId);
        return container;
    }

    private MetricName partitionLag(String topic, String partition) {
        return named(LAG, Map.of("client-id", "c", "topic", topic, "partition", partition));
    }

    private MetricName named(String name, Map<String, String> tags) {
        return new MetricName(name, "consumer-fetch-manager-metrics", "", tags);
    }

    private Metric metric(Double value) {
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn(value);
        return metric;
    }
}
