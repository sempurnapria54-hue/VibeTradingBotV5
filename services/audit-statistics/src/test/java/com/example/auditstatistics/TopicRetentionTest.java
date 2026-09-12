package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.integration.internal.event.TopicRetentionProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Срок хранения темы добывается у брокера, а не хранится копией
 * (docs/architecture/data-ownership.md §«Outbox и доставка»).
 *
 * <p><b>Что проверяется по существу — ветвь «не добыт».</b> Она не
 * подменяется ни нулём, ни умолчанием, ни прежним значением: пустой срок
 * означает, что порог не выводится, и алерт на паре срабатывает, потому
 * что измеритель не мерит (docs/concept.md, П1). Ошибка в обратную сторону
 * — молчащий алерт при неизмеренном сроке — равна бесшумной потере, против
 * которой алерт и заведён.
 */
class TopicRetentionTest {

    private static final String TOPIC = "trading-core.facts";
    private static final ConfigResource RESOURCE = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC);
    private static final String WEEK_MS = "604800000";
    /** Срок ожидания ответа брокера — тот же, что у добытчика. */
    private static final long CALL_TIMEOUT_MS = 5_000L;

    private final Admin admin = mock(Admin.class);
    private final TopicRetentionProvider provider = new TopicRetentionProvider(admin);

    @Test
    @DisplayName("Применённый брокером срок темы прочитан")
    void theAppliedRetentionOfTheTopicIsRead() {
        answerWith(KafkaFuture.completedFuture(configOf(WEEK_MS)));

        assertThat(provider.retentionMs(TOPIC))
                .as("носитель числа один: манифест применяет владелец темы, брокер отдаёт применённое")
                .contains(604_800_000L);
    }

    @Test
    @DisplayName("Вызов к брокеру отказал — срок пуст, а не подставлен умолчанием")
    void aRefusedCallLeavesTheRetentionEmpty() {
        when(admin.describeConfigs(anyCollection())).thenThrow(new KafkaException("связи с брокером нет"));

        assertThat(provider.retentionMs(TOPIC))
                .as("умолчание не бывает благоприятным: порог не выводится, и алерт срабатывает")
                .isEmpty();
    }

    @Test
    @DisplayName("Ожидание ответа не уложилось в срок — тот же исход, что у отказа")
    void aTimedOutCallLeavesTheRetentionEmpty() throws Exception {
        KafkaFuture<Config> future = future();
        when(future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .thenThrow(new ExecutionException(new KafkaException("брокер не ответил")));
        answerWith(future);

        assertThat(provider.retentionMs(TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("Брокер значения не отдал — срок пуст")
    void anAbsentEntryLeavesTheRetentionEmpty() {
        answerWith(KafkaFuture.completedFuture(new Config(List.of())));

        assertThat(provider.retentionMs(TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("Хранение без предела конечным сроком не является")
    void anInfiniteRetentionIsNotAFiniteTerm() {
        answerWith(KafkaFuture.completedFuture(configOf("-1")));

        assertThat(provider.retentionMs(TOPIC))
                .as("доля от бесконечности порогом не бывает — ветвь та же, что у неотданного значения")
                .isEmpty();
    }

    @Test
    @DisplayName("Нечисловое значение срока пустоту даёт, а тик не роняет")
    void anUnparseableRetentionLeavesTheRetentionEmpty() {
        answerWith(KafkaFuture.completedFuture(configOf("на неделю")));

        assertThat(provider.retentionMs(TOPIC)).isEmpty();
    }

    private Config configOf(String retentionMs) {
        return new Config(List.of(new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, retentionMs)));
    }

    private void answerWith(KafkaFuture<Config> future) {
        DescribeConfigsResult result = mock(DescribeConfigsResult.class);
        when(result.values()).thenReturn(Map.of(RESOURCE, future));
        when(admin.describeConfigs(anyCollection())).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private KafkaFuture<Config> future() {
        return mock(KafkaFuture.class);
    }
}
