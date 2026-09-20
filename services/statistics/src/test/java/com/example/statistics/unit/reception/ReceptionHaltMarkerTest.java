package com.example.statistics.unit.reception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.statistics.domain.service.StatisticsReceptionService;
import com.example.statistics.integration.internal.event.ReceptionHaltMarker;
import com.example.testsupport.ReceptionHaltMarkerContract;
import org.springframework.kafka.listener.RetryListener;

/**
 * Копия маркера остановки приёма в дереве {@code statistics}
 * (`.claude/tests/cases/durable-reception.md`, группа `U11`, клетки
 * `U15.5`, `U16.9`).
 *
 * <p>Сток подставляет ВСЕ операции службы приёма, а не одну: клетки
 * `U11.6` и `U11.7` наблюдают отсутствие вызова, и сток без этих
 * операций был бы тавтологией.
 */
class ReceptionHaltMarkerTest extends ReceptionHaltMarkerContract {

    @Override
    protected RetryListener haltMarker(ReceptionSink reception) {
        return new ReceptionHaltMarker(receptionService(reception));
    }

    @Override
    protected Class<?> haltMarkerType() {
        return ReceptionHaltMarker.class;
    }

    private StatisticsReceptionService receptionService(ReceptionSink reception) {
        StatisticsReceptionService service = mock(StatisticsReceptionService.class);
        doAnswer(invocation -> {
            reception.noteHalt(invocation.getArgument(0));
            return null;
        }).when(service).noteHalt(anyString());
        doAnswer(invocation -> {
            reception.acceptConsequence(invocation.getArgument(0));
            return null;
        }).when(service).acceptWithoutFact(anyString(), any());
        doAnswer(invocation -> {
            reception.noteGap(invocation.getArgument(0));
            return null;
        }).when(service).noteGap(anyString(), any());
        doAnswer(invocation -> {
            reception.restartObservation(invocation.getArgument(0));
            return null;
        }).when(service).restartObservation(anyString(), any());
        return service;
    }
}
