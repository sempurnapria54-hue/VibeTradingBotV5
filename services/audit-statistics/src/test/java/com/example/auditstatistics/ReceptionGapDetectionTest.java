package com.example.auditstatistics;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.domain.service.AuditReceptionService;
import com.example.auditstatistics.integration.JournalRebalanceListener;
import com.example.auditstatistics.integration.ReceptionOffsetTracker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Обнаружение разрыва смещений — два момента и три исхода сравнения
 * (docs/components/AuditEventListener.md §«Разрыв смещений — операнд,
 * которого умолчание не даёт»;
 * docs/models/domain/other/AuditRecord.md §«Третий исход сравнения смещений
 * двигает границу, а не предикат»).
 *
 * <p><b>Почему это не проверяется отказом клиента.</b> Позиция чтения «с
 * начала темы» переставляет вышедшее за пределы смещение молча: клиент не
 * отказывает, и наблюдателем остаётся только сравнение.
 */
class ReceptionGapDetectionTest {

    private static final String TOPIC = "trading-core.facts";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final AuditReceptionService receptionService = mock(AuditReceptionService.class);
    private final ReceptionOffsetTracker offsetTracker = new ReceptionOffsetTracker();
    private final JournalRebalanceListener rebalanceListener =
            new JournalRebalanceListener(receptionService, offsetTracker);

    @Test
    @DisplayName("Зафиксированное смещение ниже наименьшего доступного — записан момент разрыва")
    void aCommittedOffsetBelowTheEarliestAvailableIsAGap() {
        rebalanceListener.onPartitionsAssigned(consumerWith(new OffsetAndMetadata(5L), 40L), List.of(PARTITION));

        verify(receptionService).noteGap(eq(TOPIC), any(OffsetDateTime.class));
        verify(receptionService, never()).restartObservation(anyString(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Зафиксированного смещения нет вовсе — наблюдение начинается заново, разрыв не пишется")
    void anAbsentCommittedOffsetRestartsObservation() {
        rebalanceListener.onPartitionsAssigned(consumerWith(null, 40L), List.of(PARTITION));

        verify(receptionService).restartObservation(eq(TOPIC), any(OffsetDateTime.class));
        verify(receptionService, never()).noteGap(anyString(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Штатное назначение не пишет ни разрыва, ни возобновления")
    void aHealthyAssignmentWritesNothing() {
        rebalanceListener.onPartitionsAssigned(consumerWith(new OffsetAndMetadata(60L), 40L), List.of(PARTITION));

        verify(receptionService, never()).noteGap(anyString(), any(OffsetDateTime.class));
        verify(receptionService, never()).restartObservation(anyString(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Второй момент: смещение доставленной записи больше ожидаемого — разрыв")
    void aDeliveredOffsetAboveTheExpectedOneIsAGap() {
        rebalanceListener.onPartitionsAssigned(consumerWith(new OffsetAndMetadata(60L), 40L), List.of(PARTITION));

        assertThat(offsetTracker.observeDelivery(recordAt(60L)))
                .as("ожидаемая запись разрывом не является")
                .isFalse();
        assertThat(offsetTracker.observeDelivery(recordAt(75L)))
                .as("пропуск между ожидаемым и доставленным есть удаление непрочитанного")
                .isTrue();
        assertThat(offsetTracker.observeDelivery(recordAt(76L)))
                .as("одна дыра объявляется один раз: ожидание сдвигается и при разрыве")
                .isFalse();
    }

    @Test
    @DisplayName("Повторная доставка разрывом не считается")
    void aRedeliveredRecordIsNotAGap() {
        rebalanceListener.onPartitionsAssigned(consumerWith(new OffsetAndMetadata(60L), 40L), List.of(PARTITION));
        offsetTracker.observeDelivery(recordAt(60L));

        assertThat(offsetTracker.observeDelivery(recordAt(60L)))
                .as("отравленное сообщение доставляется повторно по построению")
                .isFalse();
    }

    @Test
    @DisplayName("Первая запись по партиции без ожидания разрывом не объявляется")
    void theFirstRecordOfAnUntrackedPartitionIsNotAGap() {
        assertThat(offsetTracker.observeDelivery(recordAt(1_000L)))
                .as("ошибка обнаружения возможна только в запретительную сторону")
                .isFalse();
    }

    private ConsumerRecord<String, String> recordAt(Long offset) {
        return new ConsumerRecord<>(TOPIC, 0, offset, "tenant-1", "{}");
    }

    /**
     * Клиент брокера — коллаборатор границы, и он мокается законно: своя
     * проверка у него есть, а поднять брокер ради двух чисел значило бы
     * мерить не то (.claude/rules/codestyle.md §«Тесты доменных моделей»).
     */
    private Consumer<?, ?> consumerWith(OffsetAndMetadata committed, Long earliest) {
        Consumer<?, ?> consumer = mock(Consumer.class);
        when(consumer.committed(Set.of(PARTITION)))
                .thenReturn(isNull(committed) ? Map.of() : Map.of(PARTITION, committed));
        when(consumer.beginningOffsets(List.of(PARTITION))).thenReturn(Map.of(PARTITION, earliest));
        return consumer;
    }
}
