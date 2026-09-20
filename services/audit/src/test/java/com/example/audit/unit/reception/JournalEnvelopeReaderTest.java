package com.example.audit.unit.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.audit.exception.IncompleteEventException;
import com.example.audit.integration.internal.event.JournalEnvelopeReader;
import com.example.audit.integration.internal.event.model.AuditEventMessage;
import com.example.audit.util.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Чтец конверта журнала: заголовки, тенант ключом, содержимое и колонки
 * радиуса — группа `U7` документа
 * `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Группа своя у дерева, и это объявленное расхождение копий:</b>
 * радиус и операнды содержимого у двух потребителей разные
 * (docs/models/domain/other/AuditRecord.md §«Почему четыре колонки
 * идентичности, а не все»). У статистики — группа `U8`.
 *
 * <p><b>Имена заголовков берутся из констант своего дерева, а не из своей
 * копии:</b> копий имён на проводе и так пять, и шестая жила бы в тесте
 * (docs/architecture/contracts.md §«Носителя имён в общей библиотеке не
 * заводится, и это решение»).
 *
 * <p><b>Отсутствие обязательного заголовка чтеца НЕ роняет:</b> неполноту
 * входа разбирает предикат доменной модели, и его кейсы — у чёрного ящика
 * сервиса. Здесь наблюдается собственный выход чтеца.
 */
class JournalEnvelopeReaderTest {

    private static final String TOPIC = "trading-core.facts";
    private static final String TENANT = "tenant-1";
    private static final String EVENT_ID = "evt-1";
    private static final String EVENT_TYPE = "DEAL_CLOSED";
    private static final String OCCURRED_AT = "2026-09-10T03:00:00Z";
    private static final String VERSION = "2";
    private static final String TRACE_CONTEXT = "00-4bf92f-00f067aa0ba902b7-01";

    private static final String CONTENT = """
            {"dealInternalId":"deal-7","instrumentInternalId":"instr-3",
             "exchangeAccountInternalId":"acct-9","strategyInternalId":"strat-5"}""";

    private final JournalEnvelopeReader reader = new JournalEnvelopeReader(new ObjectMapper());

    @Test
    @DisplayName("U7.1 — полный конверт: пять значений, тенант из ключа, четыре колонки радиуса")
    void u7_1_aCompleteEnvelopeIsReadWhole() {
        AuditEventMessage message = reader.read(record(fullEnvelope(), TENANT, CONTENT));

        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
        assertThat(message.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(message.getOccurredAt())
                .isEqualTo(OffsetDateTime.of(2026, 9, 10, 3, 0, 0, 0, ZoneOffset.UTC));
        assertThat(message.getVersion()).isEqualTo(2);
        assertThat(message.getTraceContext()).isEqualTo(TRACE_CONTEXT);
        assertThat(message.getTenantId())
                .as("тенант приезжает ключом записи, он же ключ партиции")
                .isEqualTo(TENANT);
        assertThat(message.getDealInternalId()).isEqualTo("deal-7");
        assertThat(message.getInstrumentInternalId()).isEqualTo("instr-3");
        assertThat(message.getExchangeAccountInternalId()).isEqualTo("acct-9");
        assertThat(message.getStrategyInternalId()).isEqualTo("strat-5");
        assertThat(message.getContent())
                .as("содержимое ложится дословно: чтец его не интерпретирует")
                .isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("U7.2 — момента приёма в форме прочитанного нет: его ставит перевод в домен")
    void u7_2_theReceptionMomentIsNotPartOfWhatWasRead() {
        assertThat(fieldNames())
                .as("поле, которого на проводе не было, было бы ложью о содержимом записи")
                .doesNotContain("recordedAt", "receivedAt", "acceptedAt");
    }

    @Test
    @DisplayName("U7.3 — заголовка контекста трассировки нет: поле пусто, отказа нет")
    void u7_3_anAbsentTraceContextIsLegal() {
        AuditEventMessage message = reader.read(record(without(Constants.EventHeaders.TRACE_CONTEXT),
                TENANT, CONTENT));

        assertThat(message.getTraceContext()).isNull();
        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("U7.4 — заголовка `eventId` нет: поле пусто, чтец не отказывает")
    void u7_4_anAbsentEventIdDoesNotStopTheReader() {
        AuditEventMessage message = reader.read(record(without(Constants.EventHeaders.EVENT_ID),
                TENANT, CONTENT));

        assertThat(message.getEventId())
                .as("неполноту разбирает предикат доменной модели, а не чтец")
                .isNull();
    }

    @Test
    @DisplayName("U7.5 — заголовка `eventType` нет: поле пусто, чтец не отказывает")
    void u7_5_anAbsentEventTypeDoesNotStopTheReader() {
        assertThat(reader.read(record(without(Constants.EventHeaders.EVENT_TYPE), TENANT, CONTENT))
                .getEventType()).isNull();
    }

    @Test
    @DisplayName("U7.6 — ключа записи нет: тенант пуст, чтец не отказывает")
    void u7_6_anAbsentRecordKeyLeavesTheTenantEmpty() {
        assertThat(reader.read(record(fullEnvelope(), null, CONTENT)).getTenantId()).isNull();
    }

    @Test
    @DisplayName("U7.7 — `occurredAt` не разбирается как момент: отказ, а не подмена пустотой")
    void u7_7_anUnparseableMomentStopsTheReader() {
        assertThatThrownBy(() -> reader.read(record(with(Constants.EventHeaders.OCCURRED_AT, "вчера"),
                TENANT, CONTENT)))
                .as("ложное «значения не было» записало бы строку, которой на проводе не было")
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("U7.8 — `occurredAt` отсутствует: поле пусто, отказа нет")
    void u7_8_anAbsentMomentIsADifferentPathFromAnUnparseableOne() {
        assertThat(reader.read(record(without(Constants.EventHeaders.OCCURRED_AT), TENANT, CONTENT))
                .getOccurredAt()).isNull();
    }

    @Test
    @DisplayName("U7.9 — `version` не разбирается как число: отказ")
    void u7_9_anUnparseableVersionStopsTheReader() {
        assertThatThrownBy(() -> reader.read(record(with(Constants.EventHeaders.VERSION, "две"),
                TENANT, CONTENT)))
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("U7.10 — тела записи нет: содержимое и радиус пусты, отказа нет")
    void u7_10_anAbsentBodyLeavesTheContentEmptyWithoutFailing() {
        AuditEventMessage message = reader.read(record(fullEnvelope(), TENANT, null));

        assertThat(message.getContent()).isNull();
        assertThat(message.getDealInternalId()).isNull();
        assertThat(message.getInstrumentInternalId()).isNull();
        assertThat(message.getExchangeAccountInternalId()).isNull();
        assertThat(message.getStrategyInternalId()).isNull();
    }

    @Test
    @DisplayName("U7.11 — тело не разбирается как документ: отказ")
    void u7_11_anUnparseableBodyStopsTheReader() {
        assertThatThrownBy(() -> reader.read(record(fullEnvelope(), TENANT, "не документ")))
                .as("громкая остановка обратима, молчаливый пропуск потерял бы факт")
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("U7.12 — идентичность на вложенном уровне: колонка радиуса пуста")
    void u7_12_aNestedIdentityIsNotRead() {
        String nested = "{\"definition\":{\"strategyInternalId\":\"nested\"}}";

        assertThat(reader.read(record(fullEnvelope(), TENANT, nested)).getStrategyInternalId())
                .as("читается только одноимённый компонент ВЕРХНЕГО уровня")
                .isNull();
    }

    @Test
    @DisplayName("U7.15 — документ без компонентов радиуса: все четыре колонки пусты")
    void u7_15_aDocumentWithoutRadiusLeavesAllFourColumnsEmpty() {
        AuditEventMessage message = reader.read(record(fullEnvelope(), TENANT, "{\"any\":\"thing\"}"));

        assertThat(message.getDealInternalId()).isNull();
        assertThat(message.getInstrumentInternalId()).isNull();
        assertThat(message.getExchangeAccountInternalId()).isNull();
        assertThat(message.getStrategyInternalId()).isNull();
        assertThat(message.getContent()).isEqualTo("{\"any\":\"thing\"}");
        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("U7.16 — пробелы и переносы строк: содержимое едет байт в байт")
    void u7_16_theContentIsNotNormalized() {
        String spaced = "{\n  \"dealInternalId\" :  \"deal-7\"  \n}";

        assertThat(reader.read(record(fullEnvelope(), TENANT, spaced)).getContent()).isEqualTo(spaced);
    }

    @Test
    @DisplayName("U7.17 — два заголовка с одним именем: берётся последний")
    void u7_17_theLastHeaderWins() {
        List<RecordHeader> headers = new ArrayList<>(headersOf(fullEnvelope()));
        headers.add(new RecordHeader(Constants.EventHeaders.EVENT_ID, "evt-2".getBytes(StandardCharsets.UTF_8)));

        assertThat(reader.read(recordOf(headers, TENANT, CONTENT)).getEventId())
                .as("конвенция чтения заголовка одна на все пять")
                .isEqualTo("evt-2");
    }

    @Test
    @DisplayName("U7.18 — `eventId` с ПУСТЫМ значением: отдаётся пустая строка, а не пустота")
    void u7_18_anEmptyHeaderValueIsNotAnAbsentOne() {
        assertThat(reader.read(record(with(Constants.EventHeaders.EVENT_ID, ""), TENANT, CONTENT))
                .getEventId())
                .as("отсутствие и пустая строка — разные входы, и подмены одного другим чтец не делает")
                .isEmpty();
    }

    @Test
    @DisplayName("U7.19 — значение заголовка в не-ASCII читается без потерь")
    void u7_19_aNonAsciiHeaderValueSurvivesTheRead() {
        assertThat(reader.read(record(with(Constants.EventHeaders.EVENT_TYPE, "СДЕЛКА_ЗАКРЫТА"),
                TENANT, CONTENT)).getEventType()).isEqualTo("СДЕЛКА_ЗАКРЫТА");
    }

    @Test
    @DisplayName("U7.20 — ни темы, ни смещения, ни партиции в форме прочитанного нет")
    void u7_20_theDeliveryCoordinatesAreNotPartOfTheContent() {
        assertThat(fieldNames())
                .as("их предмет — тропа приёма, а не содержимое записи")
                .doesNotContain("topic", "offset", "partition");
    }

    @Test
    @DisplayName("U7.21 — отказ на моменте несёт неразбираемое значение и причину")
    void u7_21_theMomentFailureCarriesItsInputAndCause() {
        assertThatThrownBy(() -> reader.read(record(with(Constants.EventHeaders.OCCURRED_AT, "вчера"),
                TENANT, CONTENT)))
                .hasMessageContaining("вчера")
                .as("причина — разбор библиотеки, и она не теряется по дороге")
                .hasCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("U7.22 — отказ на версии несёт неразбираемое значение и причину")
    void u7_22_theVersionFailureCarriesItsInputAndCause() {
        assertThatThrownBy(() -> reader.read(record(with(Constants.EventHeaders.VERSION, "две"),
                TENANT, CONTENT)))
                .hasMessageContaining("две")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("U7.23 — отказ на содержимом несёт причину, а тела в сообщение не кладёт")
    void u7_23_theContentFailureCarriesItsCauseWithoutTheBody() {
        String body = "не документ, а строка с секретом supersecret";

        assertThatThrownBy(() -> reader.read(record(fullEnvelope(), TENANT, body)))
                .hasMessageNotContaining("supersecret")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    // --- оснастка ---------------------------------------------------------

    private static Map<String, String> fullEnvelope() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(Constants.EventHeaders.EVENT_ID, EVENT_ID);
        headers.put(Constants.EventHeaders.EVENT_TYPE, EVENT_TYPE);
        headers.put(Constants.EventHeaders.OCCURRED_AT, OCCURRED_AT);
        headers.put(Constants.EventHeaders.VERSION, VERSION);
        headers.put(Constants.EventHeaders.TRACE_CONTEXT, TRACE_CONTEXT);
        return headers;
    }

    private static Map<String, String> without(String header) {
        Map<String, String> headers = fullEnvelope();
        headers.remove(header);
        return headers;
    }

    private static Map<String, String> with(String header, String value) {
        Map<String, String> headers = fullEnvelope();
        headers.put(header, value);
        return headers;
    }

    private static List<RecordHeader> headersOf(Map<String, String> headers) {
        List<RecordHeader> built = new ArrayList<>();
        headers.forEach((name, value) ->
                built.add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8))));
        return built;
    }

    private static ConsumerRecord<String, String> record(Map<String, String> headers, String key, String body) {
        return recordOf(headersOf(headers), key, body);
    }

    private static ConsumerRecord<String, String> recordOf(List<RecordHeader> headers, String key, String body) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 12L, key, body);
        headers.forEach(header -> record.headers().add(header));
        return record;
    }

    private static List<String> fieldNames() {
        List<String> names = new ArrayList<>();
        for (Field field : AuditEventMessage.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        return names;
    }
}
