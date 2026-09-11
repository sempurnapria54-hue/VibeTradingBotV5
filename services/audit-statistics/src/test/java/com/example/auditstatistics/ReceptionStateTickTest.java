package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.jobs.ReceptionStateJob;
import com.example.auditstatistics.domain.model.PairLagOperands;
import com.example.auditstatistics.domain.model.ReceptionPairMoments;
import com.example.auditstatistics.domain.service.ReceptionStateSyncService;
import com.example.auditstatistics.integration.ConsumerLagProvider;
import com.example.auditstatistics.integration.TopicRetentionProvider;
import com.example.auditstatistics.metrics.JournalReceptionMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Тик состояния приёма: состав пар, живость и ряды экспорта операндов
 * алерта (docs/components/ReceptionStateJob.md).
 *
 * <p><b>Что здесь проверяется по существу.</b> Тик — измеритель, и
 * измеритель, не проверяющий живость наблюдаемого, мерит собственные часы:
 * строка обновляется, возраст мал, предикат свежести истинен — а о приёме
 * не известно ничего. Поэтому молчание при неживом приёме проверяется
 * наравне с записью при живом, а <b>ряды экспорта на всякой молчащей
 * тропе обязаны пропасть</b>: оставшись, они утверждали бы измеренное там,
 * где ничего не измерялось.
 *
 * <p><b>Операнды разведены и проверяются раздельно:</b> назначенные
 * партиции — операнд живости, объявленная подписка — операнд состава. Тема,
 * отданная другой реплике той же группы, у этой пуста, и состав по
 * назначению снял бы ей признак подписки при живой подписке.
 *
 * <p>Семантику самих записей (что заведение не трогает величин приёма, что
 * момент наблюдения вторым тактом не переписывается, что строка ушедшей
 * темы не удаляется) держит SQL, и проверена она живым прогоном —
 * .claude/work/progress/phase-2-step-10-code-pass-k4.md.
 */
class ReceptionStateTickTest {

    private static final String GROUP = "audit-statistics.journal";
    private static final String CORE_TOPIC = "trading-core.facts";
    private static final String STRATEGIES_TOPIC = "strategies.facts";
    private static final Long WEEK_MS = 604_800_000L;

    private static final String THRESHOLD_SERIES = "audit_journal_reception_lag_alert_threshold_ms";
    private static final String UNCONSUMED_SERIES = "audit_journal_reception_unconsumed_records";

    private final KafkaListenerEndpointRegistry listenerRegistry = mock(KafkaListenerEndpointRegistry.class);
    private final ReceptionStateSyncService syncService = mock(ReceptionStateSyncService.class);
    private final TopicRetentionProvider retentionProvider = mock(TopicRetentionProvider.class);
    private final ConsumerLagProvider lagProvider = mock(ConsumerLagProvider.class);
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final JournalReceptionMetrics metrics = new JournalReceptionMetrics(registry);
    private final ReceptionProperties properties = properties();
    private final ReceptionStateJob job = new ReceptionStateJob(
            properties, listenerRegistry, syncService, retentionProvider, lagProvider, metrics);

    @Test
    @DisplayName("Подписка из двух тем — состав пар из двух тем")
    void aSubscriptionOfTwoTopicsSyncsTwoPairs() {
        liveContainer(List.of(CORE_TOPIC, STRATEGIES_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));

        job.tick();

        assertThat(syncedTopics())
                .as("строка состояния — на КАЖДУЮ тему подписки, иначе пара без строки не роняет конъюнкции")
                .containsExactlyInAnyOrder(CORE_TOPIC, STRATEGIES_TOPIC);
    }

    @Test
    @DisplayName("Состав берётся из объявленной подписки, а не из назначенных партиций")
    void theCompositionComesFromTheDeclaredSubscription() {
        liveContainer(List.of(CORE_TOPIC, STRATEGIES_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));

        job.tick();

        assertThat(syncedTopics())
                .as("тема, отданная другой реплике группы, у этой пуста — по назначению она получила бы "
                        + "«не подписана» при живой подписке")
                .contains(STRATEGIES_TOPIC);
    }

    @Test
    @DisplayName("Контейнер не запущен — тик не пишет ничего и рядов не отдаёт")
    void aStoppedContainerWritesNothing() {
        givenMeasuredPair(CORE_TOPIC);
        container(Boolean.FALSE, List.of(CORE_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));

        job.tick();

        verify(syncService, never()).syncSubscription(anyString(), any(), any(OffsetDateTime.class));
        assertThat(exportedSeries())
                .as("ряды величины пропадают целиком, а не остаются от прошлого такта")
                .isEmpty();
    }

    @Test
    @DisplayName("Назначенных партиций нет — тик молчит: группа развалилась либо связи с брокером нет")
    void anEmptyAssignmentWritesNothing() {
        container(Boolean.TRUE, List.of(CORE_TOPIC), List.of());

        job.tick();

        verify(syncService, never()).syncSubscription(anyString(), any(), any(OffsetDateTime.class));
        assertThat(exportedSeries()).isEmpty();
    }

    @Test
    @DisplayName("Контейнеров нет вовсе — тик молчит")
    void noContainersAtAllWriteNothing() {
        when(listenerRegistry.getListenerContainers()).thenReturn(List.of());

        job.tick();

        verify(syncService, never()).syncSubscription(anyString(), any(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Выключатель снят — тик не пишет и рядов не отдаёт")
    void theSwitchOffStopsTheTick() {
        givenMeasuredPair(CORE_TOPIC);
        properties.setStateTickEnabled(Boolean.FALSE);
        liveContainer(List.of(CORE_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));

        job.tick();

        verify(syncService, never()).syncSubscription(anyString(), any(), any(OffsetDateTime.class));
        assertThat(exportedSeries()).isEmpty();
    }

    @Test
    @DisplayName("Порог — доля срока СВОЕЙ темы; тема без добытого срока ряда порога не получает")
    void theThresholdIsAFractionOfTheOwnTopicRetention() {
        liveContainer(List.of(CORE_TOPIC, STRATEGIES_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));
        givenPairs(CORE_TOPIC, STRATEGIES_TOPIC);
        when(retentionProvider.retentionMs(CORE_TOPIC)).thenReturn(Optional.of(WEEK_MS));
        when(retentionProvider.retentionMs(STRATEGIES_TOPIC)).thenReturn(Optional.empty());

        job.tick();

        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC))
                .as("половина оставляет на реакцию столько же времени, сколько ушло на накопление")
                .isEqualTo(WEEK_MS.doubleValue() / 2);
        assertThat(sample(THRESHOLD_SERIES, STRATEGIES_TOPIC))
                .as("пустой порог не подменяется нулём: алерт на такой паре срабатывает, а не молчит")
                .isNull();
    }

    @Test
    @DisplayName("Срок не добыт — прежнее значение порога не остаётся")
    void aRefusedFetchLeavesNoPreviousThreshold() {
        liveContainer(List.of(CORE_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));
        givenPairs(CORE_TOPIC);
        when(retentionProvider.retentionMs(CORE_TOPIC)).thenReturn(Optional.of(WEEK_MS));
        job.tick();
        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC)).isEqualTo(WEEK_MS.doubleValue() / 2);

        when(retentionProvider.retentionMs(CORE_TOPIC)).thenReturn(Optional.empty());
        job.tick();

        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC))
                .as("порог от прошлого такта утверждал бы измеренное там, где ничего не измерялось")
                .isNull();
    }

    @Test
    @DisplayName("Доля берётся из конфигурации, а не из константы кода")
    void theFractionComesFromConfiguration() {
        properties.setLagAlertThresholdFraction(0.25);
        liveContainer(List.of(CORE_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));
        givenPairs(CORE_TOPIC);
        when(retentionProvider.retentionMs(CORE_TOPIC)).thenReturn(Optional.of(WEEK_MS));

        job.tick();

        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC))
                .as("назначенная внутри исполнителя, доля перестала бы быть калибруемой")
                .isEqualTo(WEEK_MS.doubleValue() / 4);
    }

    @Test
    @DisplayName("Остаток непринятого спрашивается у клиента ПОТЕМНО")
    void theRemainderIsAskedPerTopic() {
        liveContainer(List.of(CORE_TOPIC, STRATEGIES_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));
        givenPairs(CORE_TOPIC, STRATEGIES_TOPIC);
        when(lagProvider.unconsumedRecords(any(), eq(CORE_TOPIC))).thenReturn(Optional.of(17L));
        when(lagProvider.unconsumedRecords(any(), eq(STRATEGIES_TOPIC))).thenReturn(Optional.empty());

        job.tick();

        assertThat(sample(UNCONSUMED_SERIES, CORE_TOPIC)).isEqualTo(17.0);
        assertThat(sample(UNCONSUMED_SERIES, STRATEGIES_TOPIC))
                .as("сумма по группе маскировала бы остановку на одной теме молчанием другой")
                .isNull();
    }

    @Test
    @DisplayName("Отказ посреди такта уносит ряды и уходит наружу")
    void aFailureMidTickTakesTheSeriesAway() {
        givenMeasuredPair(CORE_TOPIC);
        liveContainer(List.of(CORE_TOPIC), List.of(new TopicPartition(CORE_TOPIC, 0)));
        when(syncService.subscribedPairMoments(GROUP)).thenThrow(new IllegalStateException("база недоступна"));

        assertThatThrownBy(job::tick).isInstanceOf(IllegalStateException.class);

        assertThat(exportedSeries())
                .as("проглоченный отказ оставил бы наблюдателю картину прошлого такта под видом нынешнего")
                .isEmpty();
    }

    /** Темы, с которыми такт позвал приведение состава. */
    private Collection<String> syncedTopics() {
        ArgumentCaptor<Collection<String>> topics = ArgumentCaptor.captor();
        verify(syncService).syncSubscription(eq(GROUP), topics.capture(), any(OffsetDateTime.class));
        return topics.getValue();
    }

    /** Строки состояния, которые такт застанет после приведения состава. */
    private void givenPairs(String... topics) {
        List<ReceptionPairMoments> pairs = Arrays.stream(topics)
                .map(topic -> new ReceptionPairMoments(topic, OffsetDateTime.now(ZoneOffset.UTC), null))
                .toList();
        when(syncService.subscribedPairMoments(GROUP)).thenReturn(pairs);
    }

    /** Ряды прошлого такта, которые молчащая тропа обязана унести. */
    private void givenMeasuredPair(String topic) {
        metrics.replaceWith(List.of(new PairLagOperands(
                topic, OffsetDateTime.now(ZoneOffset.UTC), WEEK_MS / 2, 17L)));
    }

    /** Строки экспозиции, относящиеся к рядам приёма. */
    private List<String> exportedSeries() {
        return Arrays.stream(registry.scrape().split("\n"))
                .filter(line -> line.startsWith("audit_journal_reception_"))
                .toList();
    }

    /** Значение ряда по метке темы; пусто — ряда в экспозиции нет. */
    private Double sample(String series, String topic) {
        for (String line : registry.scrape().split("\n")) {
            if (line.startsWith(series + "{") && line.contains("topic=\"" + topic + "\"")) {
                return Double.valueOf(line.substring(line.lastIndexOf(' ') + 1).trim());
            }
        }
        return null;
    }

    private void liveContainer(List<String> declaredTopics, List<TopicPartition> assigned) {
        container(Boolean.TRUE, declaredTopics, assigned);
    }

    private void container(Boolean running, List<String> declaredTopics, List<TopicPartition> assigned) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(running);
        when(container.getAssignedPartitions()).thenReturn(assigned);
        when(container.getContainerProperties())
                .thenReturn(new ContainerProperties(declaredTopics.toArray(new String[0])));
        when(listenerRegistry.getListenerContainers()).thenReturn(List.of(container));
    }

    private ReceptionProperties properties() {
        ReceptionProperties reception = new ReceptionProperties();
        reception.setGroupId(GROUP);
        return reception;
    }
}
