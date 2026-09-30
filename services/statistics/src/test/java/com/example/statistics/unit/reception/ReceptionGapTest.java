package com.example.statistics.unit.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statistics.domain.service.StatisticsReceptionService;
import com.example.statistics.integration.internal.event.ReceptionRebalanceListener;
import com.example.statistics.integration.internal.event.ReceptionOffsetTracker;
import com.example.testsupport.ReceptionGapContract;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

/**
 * Копии слушателя назначения и трекера смещений в дереве {@code statistics}
 * (`.claude/tests/cases/durable-reception.md`, группы `U9`, `U10`, клетки
 * `U15.1`, `U15.4`, `U16.10`).
 *
 * <p>Ожидания живут в контракте общего артефакта и объявлены один раз;
 * порты подставляют свою копию и свои границы — службу приёма и трекер.
 *
 * <p><b>Клетка `U9.5` стоит у копии, а не в контракте, и это названный
 * остаток.</b> Её ожидание общее для обеих копий и принадлежит контракту
 * наравне с соседями по группе, а заход, её написавший, общего артефакта не
 * правил. Тела клетки у двух деревьев совпадают дословно; перенос в контракт
 * снимает эту копию.
 */
class ReceptionGapTest extends ReceptionGapContract {

    private static final TopicPartition ASSIGNED = new TopicPartition(TOPIC, 0);

    // --- U9.5: наименьшего доступного брокер не отдал ----------------------

    @Test
    @DisplayName("U9.5 — наименьшего доступного нет, смещение есть: ожидание на смещении, граница стоит")
    void u9_5_withoutTheEarliestTheCommittedOffsetSeatsTheExpectation() {
        Protocol protocol = assignWithoutEarliest(Map.of(ASSIGNED, new OffsetAndMetadata(60L)));

        assertThat(protocol.gaps)
                .as("сравнивать не с чем: разрыв, случившийся до назначения, объявит доставка")
                .isEmpty();
        assertThat(protocol.restarts)
                .as("момент наблюдения не двигается — разрыв границей не подменяется")
                .isEmpty();
        assertThat(protocol.expectations)
                .as("второй момент обнаружения получает операнд: ожидание на зафиксированном смещении")
                .containsExactly(Map.entry(ASSIGNED, 60L));
    }

    @Test
    @DisplayName("U9.5 — наименьшего доступного нет и смещения нет: наблюдение заново, ожидания нет")
    void u9_5_withoutTheEarliestAndTheCommittedOffsetObservationRestarts() {
        Protocol protocol = assignWithoutEarliest(Map.of());

        assertThat(protocol.restarts)
                .as("исход тот же, что при отсутствующем смещении: наименьшее доступное ему не нужно")
                .containsExactly(TOPIC);
        assertThat(protocol.gaps).isEmpty();
        assertThat(protocol.expectations)
                .as("позиции назначение не знает вовсе — ожидание посадит первая доставка")
                .isEmpty();
    }

    @Override
    protected ConsumerAwareRebalanceListener rebalanceListener(ReceptionSink reception, ExpectSink expect) {
        return new ReceptionRebalanceListener(receptionService(reception), offsetTracker(expect));
    }

    @Override
    protected Tracker newTracker() {
        ReceptionOffsetTracker tracker = new ReceptionOffsetTracker();
        return new Tracker() {

            @Override
            public void expect(TopicPartition partition, Long offset) {
                tracker.expect(partition, offset);
            }

            @Override
            public Boolean observeDelivery(ConsumerRecord<String, String> record) {
                return tracker.observeDelivery(record);
            }
        };
    }

    @Override
    protected Class<?> trackerType() {
        return ReceptionOffsetTracker.class;
    }

    @Override
    protected Class<?> rebalanceListenerType() {
        return ReceptionRebalanceListener.class;
    }

    private StatisticsReceptionService receptionService(ReceptionSink reception) {
        StatisticsReceptionService service = mock(StatisticsReceptionService.class);
        doAnswer(invocation -> {
            reception.noteGap(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(service).noteGap(anyString(), any());
        doAnswer(invocation -> {
            reception.restartObservation(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(service).restartObservation(anyString(), any());
        return service;
    }

    /** Назначение одной партиции, по которой брокер наименьшего доступного не отдал. */
    @SuppressWarnings("unchecked")
    private Protocol assignWithoutEarliest(Map<TopicPartition, OffsetAndMetadata> committed) {
        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.committed(anySet())).thenReturn(committed);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of());
        Protocol protocol = new Protocol();
        rebalanceListener(protocol, protocol).onPartitionsAssigned(consumer, List.of(ASSIGNED));
        return protocol;
    }

    private ReceptionOffsetTracker offsetTracker(ExpectSink expect) {
        ReceptionOffsetTracker tracker = mock(ReceptionOffsetTracker.class);
        doAnswer(invocation -> {
            expect.expect(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(tracker).expect(any(), anyLong());
        return tracker;
    }

    /** Сток записей в службу приёма и посаженных ожиданий клетки `U9.5`. */
    private static final class Protocol implements ReceptionSink, ExpectSink {

        private final List<String> gaps = new ArrayList<>();
        private final List<String> restarts = new ArrayList<>();
        private final List<Map.Entry<TopicPartition, Long>> expectations = new ArrayList<>();

        @Override
        public void noteGap(String topic, OffsetDateTime moment) {
            gaps.add(topic);
        }

        @Override
        public void restartObservation(String topic, OffsetDateTime moment) {
            restarts.add(topic);
        }

        @Override
        public void expect(TopicPartition partition, Long offset) {
            expectations.add(Map.entry(partition, offset));
        }
    }
}
