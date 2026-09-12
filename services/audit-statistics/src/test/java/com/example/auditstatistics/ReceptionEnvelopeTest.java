package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.service.AuditReceptionService;
import com.example.auditstatistics.integration.internal.event.AuditEventListener;
import com.example.auditstatistics.integration.internal.event.IncompleteEventException;
import com.example.auditstatistics.integration.internal.event.JournalEnvelopeReader;
import com.example.auditstatistics.integration.internal.event.ReceptionOffsetTracker;
import com.example.auditstatistics.mapping.AuditRecordMapper;
import com.example.auditstatistics.mapping.AuditRecordMapperImpl;
import com.example.auditstatistics.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Что слушатель приёма берёт с провода и на чём он останавливается
 * (docs/models/domain/other/AuditRecord.md §«Единственная ветвь, на которой
 * строки не будет, — неполный вход»; §«Почему четыре колонки идентичности, а
 * не все»).
 *
 * <p><b>Тест собирает состояние, а не подменяет предикат:</b> сообщение
 * строится настоящими заголовками и настоящим телом, а полнота входа
 * считается самой моделью (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»).
 */
class ReceptionEnvelopeTest {

    /** Перевод прочитанного сообщения в доменную строку — маппер границы. */
    private static final AuditRecordMapper MESSAGES = new AuditRecordMapperImpl();

    private static final String TOPIC = "trading-core.facts";
    private static final String TENANT = "tenant-1";
    private static final String EVENT_ID = "evt-1";
    private static final String EVENT_TYPE = "DEAL_CLOSED";
    private static final String OCCURRED_AT = "2026-09-10T03:00:00Z";
    private static final String VERSION = "2";
    private static final String CONTENT = """
            {"dealInternalId":"deal-7","instrumentInternalId":"instr-3",
             "definition":{"strategyInternalId":"nested-should-not-be-read"},
             "exchangeAccountInternalId":"acct-9"}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JournalEnvelopeReader reader = new JournalEnvelopeReader(objectMapper);
    private final AuditReceptionService receptionService = mock(AuditReceptionService.class);
    private final AuditEventListener listener =
            new AuditEventListener(reader, MESSAGES, new ReceptionOffsetTracker(), receptionService);

    @Test
    @DisplayName("Полный конверт разбирается целиком, тенант — из ключа записи")
    void aCompleteEnvelopeIsRead() {
        AuditRecord record = MESSAGES.messageToDomain(reader.read(message(fullEnvelope(),
                TENANT, CONTENT)));

        assertThat(record.getEventId()).isEqualTo(EVENT_ID);
        assertThat(record.getTenantId())
                .as("тенант едет ключом записи: одноимённого заголовка на проводе нет вовсе")
                .isEqualTo(TENANT);
        assertThat(record.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(record.getOccurredAt()).isEqualTo(OffsetDateTime.parse(OCCURRED_AT));
        assertThat(record.getVersion()).isEqualTo(Integer.valueOf(2));
        assertThat(record.getContent())
                .as("содержимое ложится как доставлено — журнал хранит факт, а не его разбор")
                .isEqualTo(CONTENT);
        assertThat(record.getRecordedAt())
                .as("момент приёма ставит код приёма, а не конверт")
                .isNotNull();
        assertThat(record.hasCompleteInput()).isTrue();
    }

    @Test
    @DisplayName("Колонки радиуса берутся с ВЕРХНЕГО уровня содержимого, вложенное не читается")
    void radiusColumnsComeFromTopLevelComponentsOnly() {
        AuditRecord record = MESSAGES.messageToDomain(reader.read(message(fullEnvelope(),
                TENANT, CONTENT)));

        assertThat(record.getDealInternalId()).isEqualTo("deal-7");
        assertThat(record.getInstrumentInternalId()).isEqualTo("instr-3");
        assertThat(record.getExchangeAccountInternalId()).isEqualTo("acct-9");
        assertThat(record.getStrategyInternalId())
                .as("идентичность внутри вложенной структуры колонки не заполняет: "
                        + "не зная формы, читатель не скажет, чья она")
                .isNull();
    }

    @Test
    @DisplayName("Пустой контекст трассировки вход не делает неполным")
    void anAbsentTraceContextIsLegal() {
        Map<String, String> headers = fullEnvelope();
        headers.remove(Constants.EventHeaders.TRACE_CONTEXT);

        AuditRecord record = MESSAGES.messageToDomain(reader.read(message(headers, TENANT,
                CONTENT)));

        assertThat(record.getTraceContext()).isNull();
        assertThat(record.hasCompleteInput())
                .as("«трассировки не было» — законное состояние, а не потеря")
                .isTrue();
    }

    /**
     * Каждое из четырёх заголовочных обязательных значений порознь роняет
     * обработку. Тенант и содержимое едут не заголовками, и у них свои
     * проверки ниже.
     */
    @ParameterizedTest(name = "без заголовка {0} обработка останавливается")
    @ValueSource(strings = {"eventId", "eventType", "occurredAt", "version"})
    @DisplayName("Отсутствие любого обязательного заголовка останавливает приём")
    void anyAbsentMandatoryHeaderHaltsReception(String absent) {
        Map<String, String> headers = fullEnvelope();
        headers.remove(absent);

        assertThatThrownBy(() -> listener.onEvent(message(headers, TENANT, CONTENT)))
                .isInstanceOf(IncompleteEventException.class);
        verify(receptionService, never()).accept(any(), anyString());
    }

    @Test
    @DisplayName("Сообщение без ключа записи остаётся без тенанта и останавливает приём")
    void anAbsentTenantHaltsReception() {
        assertThatThrownBy(() -> listener.onEvent(message(fullEnvelope(), null, CONTENT)))
                .isInstanceOf(IncompleteEventException.class);
        verify(receptionService, never()).accept(any(), anyString());
    }

    @Test
    @DisplayName("Сообщение без содержимого останавливает приём")
    void anAbsentContentHaltsReception() {
        assertThatThrownBy(() -> listener.onEvent(message(fullEnvelope(), TENANT, null)))
                .isInstanceOf(IncompleteEventException.class);
        verify(receptionService, never()).accept(any(), anyString());
    }

    @Test
    @DisplayName("Неразбираемое содержимое не подменяется пустотой, а роняет обработку")
    void unreadableContentHaltsReception() {
        assertThatThrownBy(() -> listener.onEvent(message(fullEnvelope(), TENANT, "не документ")))
                .isInstanceOf(IncompleteEventException.class);
        verify(receptionService, never()).accept(any(), anyString());
    }

    @Test
    @DisplayName("Полный вход уезжает в приём вместе с темой своей пары")
    void aCompleteEventIsAccepted() {
        listener.onEvent(message(fullEnvelope(), TENANT, CONTENT));

        verify(receptionService).accept(any(AuditRecord.class), eq(TOPIC));
    }

    /** Заголовки полного конверта — теми же именами, что ставит публикатор. */
    private Map<String, String> fullEnvelope() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(Constants.EventHeaders.EVENT_ID, EVENT_ID);
        headers.put(Constants.EventHeaders.EVENT_TYPE, EVENT_TYPE);
        headers.put(Constants.EventHeaders.OCCURRED_AT, OCCURRED_AT);
        headers.put(Constants.EventHeaders.VERSION, VERSION);
        headers.put(Constants.EventHeaders.TRACE_CONTEXT, "00-trace-span-01");
        return headers;
    }

    private ConsumerRecord<String, String> message(Map<String, String> headers, String key, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, key, value);
        headers.forEach((name, headerValue) ->
                record.headers().add(new RecordHeader(name, headerValue.getBytes(StandardCharsets.UTF_8))));
        return record;
    }
}
