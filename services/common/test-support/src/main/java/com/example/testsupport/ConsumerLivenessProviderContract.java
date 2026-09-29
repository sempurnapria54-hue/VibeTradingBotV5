package com.example.testsupport;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
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
import java.util.Set;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Живость потребления, как её читает измеритель живости — группа `U17`
 * документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии</b> (`.claude/rules/carrier-levels.md`): провайдер лежит тремя
 * дословными экземплярами — у двух durable-потребителей и у периметра.
 *
 * <p><b>Предмет — третий конъюнкт:</b> связь с координатором группы. Первые
 * два — запущенность и назначение — у остановленного брокера остаются
 * истинными, и без третьего измеритель считал бы живым потребителя, которого
 * брокер уже вывел из группы. Связь — младшая из двух отметок клиента:
 * последнего сердцебиения и последней успешной ребалансировки.
 */
public abstract class ConsumerLivenessProviderContract {

    /** Срок сессии, объявленный фабрике в кейсах объявленного срока. */
    protected static final String DECLARED_SESSION_TIMEOUT_MS = "10000";

    private static final String LAST_HEARTBEAT = "last-heartbeat-seconds-ago";
    private static final String LAST_REBALANCE = "last-rebalance-seconds-ago";
    private static final String GROUP_OF_METRICS = "consumer-coordinator-metrics";
    private static final String SESSION_TIMEOUT_KEY = "session.timeout.ms";
    private static final Set<TopicPartition> ASSIGNED = Set.of(new TopicPartition("trading-core.facts", 0));

    // --- порты к своей копии ---------------------------------------------

    /**
     * Живость, как её читает копия своего дерева, собранная над фабрикой
     * потребителя с этими настройками.
     */
    protected abstract Boolean isLive(Map<String, Object> consumerSettings,
                                      Collection<MessageListenerContainer> containers);

    /** Класс провайдера своего дерева. */
    protected abstract Class<?> consumerLivenessProviderType();

    // --- U17: три конъюнкта и неизмеренное -------------------------------

    @Test
    @DisplayName("U17.1 — запущен, назначение есть, сердцебиение свежее: жив")
    void u17_1_aRunningAssignedConsumerInTouchIsLive() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, heartbeat(2.0))))).isTrue();
    }

    @Test
    @DisplayName("U17.2 — возраст сердцебиения достиг срока сессии умолчания: не жив")
    void u17_2_aHeartbeatAsOldAsTheSessionIsNotLive() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, heartbeat(45.0)))))
                .as("дольше срока сессии брокер считает участника выбывшим: назначение уже ничьё")
                .isFalse();
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, heartbeat(44.0)))))
                .as("срок сессии умолчания клиента — 45 секунд")
                .isTrue();
    }

    @Test
    @DisplayName("U17.3 — сердцебиения ещё не было (`-1`): не жив")
    void u17_3_noHeartbeatYetIsNotLive() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, heartbeat(-1.0)))))
                .as("живость не доказана, и измеритель молчит")
                .isFalse();
    }

    @Test
    @DisplayName("U17.4 — метрики сердцебиения нет вовсе: не жив")
    void u17_4_anAbsentHeartbeatMetricIsNotLive() {
        Map<MetricName, Metric> byClient = new LinkedHashMap<>();
        byClient.put(new MetricName("records-lag", "consumer-fetch-manager-metrics", "", Map.of()), metricOf(0.0));

        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, byClient)))).isFalse();
    }

    @Test
    @DisplayName("U17.5 — значение не число либо `NaN`: не жив")
    void u17_5_anUnmeasuredHeartbeatIsNotLive() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, heartbeat(Double.NaN))))).isFalse();
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED,
                Map.of(heartbeatName(), metricOf("не число")))))).isFalse();
    }

    @Test
    @DisplayName("U17.6 — контейнер остановлен при свежем сердцебиении: не жив")
    void u17_6_aStoppedContainerIsNotLive() {
        assertThat(isLive(Map.of(), List.of(container(false, ASSIGNED, heartbeat(2.0))))).isFalse();
    }

    @Test
    @DisplayName("U17.7 — назначения нет при свежем сердцебиении: не жив")
    void u17_7_anUnassignedContainerIsNotLive() {
        assertThat(isLive(Map.of(), List.of(container(true, Set.of(), heartbeat(2.0))))).isFalse();
    }

    @Test
    @DisplayName("U17.8 — контейнеров нет: не жив")
    void u17_8_noContainersIsNotLive() {
        assertThat(isLive(Map.of(), List.of())).as("потреблять некому").isFalse();
    }

    @Test
    @DisplayName("U17.9 — объявленный фабрике срок сессии заменяет умолчание")
    void u17_9_theDeclaredSessionTimeoutIsHonoured() {
        Map<String, Object> declared = Map.of(SESSION_TIMEOUT_KEY, DECLARED_SESSION_TIMEOUT_MS);

        assertThat(isLive(declared, List.of(container(true, ASSIGNED, heartbeat(12.0)))))
                .as("порог — срок сессии самого потребителя, а не умолчание клиента")
                .isFalse();
        assertThat(isLive(declared, List.of(container(true, ASSIGNED, heartbeat(8.0))))).isTrue();
    }

    @Test
    @DisplayName("U17.10 — из двух контейнеров один без связи: не жив")
    void u17_10_everyContainerMustBeLive() {
        assertThat(isLive(Map.of(), List.of(
                container(true, ASSIGNED, heartbeat(2.0)),
                container(true, ASSIGNED, heartbeat(60.0))))).isFalse();
    }

    @Test
    @DisplayName("U17.11 — у контейнера два клиента, один без связи: не жив")
    void u17_11_everyClientOfAContainerMustBeInTouch() {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);
        when(container.getAssignedPartitions()).thenReturn(ASSIGNED);
        Map<String, Map<MetricName, ? extends Metric>> byClientId = Map.of(
                "client-0", heartbeat(2.0),
                "client-1", heartbeat(60.0));
        when(container.metrics()).thenReturn(byClientId);

        assertThat(isLive(Map.of(), List.of(container))).isFalse();
    }

    @Test
    @DisplayName("U17.12 — метрик клиента нет вовсе: не жив")
    void u17_12_aContainerWithoutClientsIsNotLive() {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);
        when(container.getAssignedPartitions()).thenReturn(ASSIGNED);
        when(container.metrics()).thenReturn(Map.of());

        assertThat(isLive(Map.of(), List.of(container))).isFalse();
    }

    @Test
    @DisplayName("U17.14 — сердцебиения ещё не было, а вступление в группу свежее: жив")
    void u17_14_aFreshJoinIsAContactBeforeTheFirstHeartbeat() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, contact(-1.0, 1.0)))))
                .as("первое сердцебиение уходит через период после вступления, а вступление само есть контакт")
                .isTrue();
    }

    @Test
    @DisplayName("U17.15 — сердцебиение старое, ребалансировка свежая: берётся младшая отметка")
    void u17_15_theYoungestContactCounts() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, contact(60.0, 2.0))))).isTrue();
    }

    @Test
    @DisplayName("U17.16 — обе отметки старше срока сессии либо не было ни одной: не жив")
    void u17_16_bothContactsStaleOrAbsentIsNotLive() {
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, contact(60.0, 50.0))))).isFalse();
        assertThat(isLive(Map.of(), List.of(container(true, ASSIGNED, contact(-1.0, -1.0))))).isFalse();
    }

    @Test
    @DisplayName("U17.13 — контейнеры приходят параметром; поверхность копий одна")
    void u17_13_theContainersArriveAsAParameter() {
        List<String> fieldTypes = new ArrayList<>();
        for (Field field : consumerLivenessProviderType().getDeclaredFields()) {
            if (isFalse(field.isSynthetic()) && isFalse(Modifier.isStatic(field.getModifiers()))) {
                fieldTypes.add(field.getType().getSimpleName());
            }
        }
        List<String> publicMethods = new ArrayList<>();
        for (Method method : consumerLivenessProviderType().getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && isFalse(method.isSynthetic())) {
                publicMethods.add(method.getName());
            }
        }

        assertThat(fieldTypes)
                .as("реестр обходит вызывающий: второй обход застал бы другой состав")
                .containsExactly("Long");
        assertThat(consumerLivenessProviderType().getSimpleName()).isEqualTo("ConsumerLivenessProvider");
        assertThat(publicMethods).containsExactly("isLive");
    }

    // --- оснастка ---------------------------------------------------------

    private static MessageListenerContainer container(Boolean running, Set<TopicPartition> assigned,
                                                      Map<MetricName, ? extends Metric> byClient) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(running);
        when(container.getAssignedPartitions()).thenReturn(assigned);
        Map<String, Map<MetricName, ? extends Metric>> byClientId = Map.of("client", byClient);
        when(container.metrics()).thenReturn(byClientId);
        return container;
    }

    private static Map<MetricName, Metric> heartbeat(Double ageSeconds) {
        return Map.of(heartbeatName(), metricOf(ageSeconds));
    }

    private static Map<MetricName, Metric> contact(Double heartbeatAgeSeconds, Double rebalanceAgeSeconds) {
        return Map.of(
                heartbeatName(), metricOf(heartbeatAgeSeconds),
                new MetricName(LAST_REBALANCE, GROUP_OF_METRICS, "", Map.of("client-id", "c")),
                metricOf(rebalanceAgeSeconds));
    }

    private static MetricName heartbeatName() {
        return new MetricName(LAST_HEARTBEAT, GROUP_OF_METRICS, "", Map.of("client-id", "c"));
    }

    private static Metric metricOf(Object value) {
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn(value);
        return metric;
    }
}
