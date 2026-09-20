package com.example.audit.unit.reception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.audit.domain.service.AuditReceptionService;
import com.example.audit.integration.internal.event.JournalRebalanceListener;
import com.example.audit.integration.internal.event.ReceptionOffsetTracker;
import com.example.testsupport.ReceptionGapContract;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

/**
 * Копии слушателя назначения и трекера смещений в дереве {@code audit}
 * (`.claude/tests/cases/durable-reception.md`, группы `U9`, `U10`, клетки
 * `U15.1`, `U15.4`, `U16.10`).
 *
 * <p>Ожидания живут в контракте общего артефакта и объявлены один раз;
 * порты подставляют свою копию и свои границы — службу приёма и трекер.
 */
class ReceptionGapTest extends ReceptionGapContract {

    @Override
    protected ConsumerAwareRebalanceListener rebalanceListener(ReceptionSink reception, ExpectSink expect) {
        return new JournalRebalanceListener(receptionService(reception), offsetTracker(expect));
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
        return JournalRebalanceListener.class;
    }

    private AuditReceptionService receptionService(ReceptionSink reception) {
        AuditReceptionService service = mock(AuditReceptionService.class);
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

    private ReceptionOffsetTracker offsetTracker(ExpectSink expect) {
        ReceptionOffsetTracker tracker = mock(ReceptionOffsetTracker.class);
        doAnswer(invocation -> {
            expect.expect(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(tracker).expect(any(), anyLong());
        return tracker;
    }
}
