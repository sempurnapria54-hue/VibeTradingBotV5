package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.config.OutboxRelayProperties;
import com.example.tradingcore.domain.jobs.JobExecutionGuard;
import com.example.tradingcore.domain.jobs.OutboxRelayJob;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.integration.internal.event.EventPublisher;
import com.example.tradingcore.mapping.CoreEventMessageMapper;
import com.example.tradingcore.mapping.CoreEventMessageMapperImpl;
import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.service.OutboxDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Запись строки outbox и её публикация реле.
 *
 * <p><b>Что здесь проверяется по существу.</b> Порядок «сперва пометить,
 * потом опубликовать» ТЕРЯЛ бы событие при обрыве — а обратный дублирует,
 * и дубль уже закрыт дедупом потребителя по идентичности события. Проход,
 * не прекращающийся на первом отказе брокера, шлёт впустую весь остаток
 * окна: отказ у всех строк общий. Тема, приходящая параметром, позволила
 * бы положить факт в тему состояний, где компакция его потеряет.
 */
class OutboxRelayTest {

    /** Маппер domain → message: формы событий строит граница, а не домен. */
    private static final CoreEventMessageMapper EVENT_MESSAGES = new CoreEventMessageMapperImpl();

    private static final String TENANT = "tn-0001";

    private final OutboxDataService outboxDataService = mock(OutboxDataService.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);

    // --- запись ------------------------------------------------------------

    /**
     * Строка несёт конверт колонками и содержимое JSON'ом; тема выводится,
     * а не приходит параметром — род у всех классов ядра один.
     */
    @Test
    void theWrittenRowCarriesTheEnvelopeAndTheFactsTopic() {
        writer().dealOpened(TENANT, openedDeal(), "ea-1", "in-1", "st-1");

        OutboxEntity row = savedRow();
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getEventType()).isEqualTo(CoreEventType.DEAL_OPENED.name());
        assertThat(row.getTopic()).isEqualTo("trading-core.facts");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getEventId()).isNotBlank();
        assertThat(row.getOccurredAt()).isNotNull();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getPayload()).contains("\"dealInternalId\":\"dl-1\"")
                .contains("\"entryReason\":\"STRATEGY\"");
    }

    /**
     * Контекст трассировки пуст, и пустота законна: она означает
     * «трассировки не было», а не «потеряна» — инструментирование ещё не
     * подключено.
     */
    @Test
    void anAbsentTraceContextIsLegal() {
        writer().dealOpened(TENANT, openedDeal(), "ea-1", "in-1", "st-1");

        assertThat(savedRow().getTraceContext()).isNull();
    }

    // --- публикация --------------------------------------------------------

    /**
     * Порядок «сперва опубликовать, потом пометить»: обратный терял бы
     * событие при обрыве, а этот дублирует — и дубль закрыт дедупом
     * потребителя.
     */
    @Test
    void aRowIsPublishedBeforeItIsMarked() {
        when(outboxDataService.findUnpublished(any())).thenReturn(new ArrayList<>(List.of(row(1L))));

        job().tick();

        InOrder order = inOrder(eventPublisher, outboxDataService);
        order.verify(eventPublisher).publish(any());
        order.verify(outboxDataService).markPublished(eq(1L), any());
    }

    /**
     * Проход прекращается на первом отказе брокера: повторять внутри тика
     * бессмысленно — отказ у всех строк общий, — и остаток остаётся
     * непомеченным.
     */
    @Test
    void aBrokerRefusalStopsThePassAndLeavesRowsUnmarked() {
        when(outboxDataService.findUnpublished(any()))
                .thenReturn(new ArrayList<>(List.of(row(1L), row(2L))));
        doThrow(new IllegalStateException("broker is unreachable")).when(eventPublisher).publish(any());

        job().tick();

        verify(eventPublisher).publish(any());
        verify(outboxDataService, never()).markPublished(any(), any());
    }

    /**
     * Отказ самой отметки проход не рвёт: строка останется непомеченной и
     * будет опубликована повторно, а повтор безопасен по построению.
     */
    @Test
    void aFailedMarkDoesNotStopThePass() {
        when(outboxDataService.findUnpublished(any()))
                .thenReturn(new ArrayList<>(List.of(row(1L), row(2L))));
        when(outboxDataService.markPublished(eq(1L), any()))
                .thenThrow(new IllegalStateException("database is unreachable"));

        job().tick();

        verify(eventPublisher, times(2)).publish(any());
    }

    /** Выключенное реле не читает и не публикует. */
    @Test
    void aDisabledRelayDoesNothing() {
        OutboxRelayProperties disabled = new OutboxRelayProperties();
        disabled.setEnabled(Boolean.FALSE);

        job(disabled).tick();

        verify(outboxDataService, never()).findUnpublished(any());
    }

    // --- сборка ------------------------------------------------------------

    /**
     * Сделка ровно с тем, что читают утверждения о содержимом: форму строит
     * маппер за границей, и подставлять её руками здесь нечем
     * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»).
     */
    private Deal openedDeal() {
        Deal deal = new Deal();
        deal.setInternalId("dl-1");
        deal.setEntryReason(Deal.EntryReason.STRATEGY);
        return deal;
    }

    private CoreEventWriter writer() {
        return new CoreEventWriter(outboxDataService, new ObjectMapper(), EVENT_MESSAGES);
    }

    private OutboxRelayJob job() {
        return job(new OutboxRelayProperties());
    }

    private OutboxRelayJob job(OutboxRelayProperties properties) {
        return new OutboxRelayJob(properties, new JobExecutionGuard(), outboxDataService, eventPublisher);
    }

    private OutboxEntity savedRow() {
        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxDataService).save(captor.capture());
        return captor.getValue();
    }

    private OutboxEntity row(Long id) {
        OutboxEntity row = new OutboxEntity();
        row.setId(id);
        row.setEventId("ev-" + id);
        row.setTenantId(TENANT);
        row.setEventType(CoreEventType.DEAL_OPENED.name());
        row.setVersion(1);
        row.setTopic("trading-core.facts");
        row.setPayload("{}");
        return row;
    }
}
