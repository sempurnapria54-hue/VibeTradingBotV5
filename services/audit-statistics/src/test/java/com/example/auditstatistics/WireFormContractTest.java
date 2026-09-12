package com.example.auditstatistics;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.integration.internal.event.JournalEnvelopeReader;
import com.example.auditstatistics.mapping.AuditRecordMapper;
import com.example.auditstatistics.mapping.AuditRecordMapperImpl;
import com.example.auditstatistics.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сквозная проверка формы провода: сообщение, собранное <b>построенным
 * публикатором</b>, разбирается потребителем журнала по объявленным именам.
 *
 * <p><b>Зачем эта охрана существует.</b> Носителя имён полей конверта в
 * общей библиотеке не заводится намеренно — он был бы вторым носителем
 * формы (docs/architecture/contracts.md §«Носителя имён в общей библиотеке
 * не заводится, и это решение»). Цена решения названа там же: имена лежат
 * приватными копиями у каждой стороны, энфорсера соответствия у них нет, и
 * охраной объявлен <b>сквозной тест</b>, а не чтение. Потребитель журнала —
 * пятая копия, и опечатку в ней ловит именно этот тест.
 *
 * <p><b>Имена берутся у публикатора, а не пишутся рядом.</b> Тест читает
 * исходники обоих построенных публикаторов и достаёт из них два факта:
 * какими литералами названы заголовки и какое поле строки outbox едет
 * каждым. Список, написанный здесь руками, был бы шестой копией и разошёлся
 * бы с публикатором молча.
 *
 * <p><b>Что тест НЕ мерит, и это названо.</b> Он не поднимает брокера:
 * сериализация значений в байты и обратно — предмет клиента, у которого
 * своя проверка. Мерятся <b>имена, размещение и форма значений</b>: заголовки
 * с именами полей, тенант ключом записи, момент — ISO-8601, версия —
 * десятичным числом.
 */
class WireFormContractTest {

    /** Перевод прочитанного сообщения в доменную строку — маппер границы. */
    private static final AuditRecordMapper MESSAGES = new AuditRecordMapperImpl();

    /** Построенные публикаторы: у каждого своя приватная копия имён. */
    private static final List<Path> PUBLISHERS = List.of(
            Path.of("..", "..", "services", "trading-core", "src", "main", "java", "com", "example",
                    "tradingcore", "integration", "internal", "event", "EventPublisher.java"),
            Path.of("..", "..", "services", "strategies", "src", "main", "java", "com", "example",
                    "strategies", "integration", "internal", "event", "EventPublisher.java"));

    /** Объявление литерала имени заголовка у публикатора. */
    private static final Pattern HEADER_CONSTANT =
            Pattern.compile("(HEADER_[A-Z_]+)\\s*=\\s*\"([A-Za-z]+)\"");

    /** Постановка заголовка: какое поле строки outbox им едет. */
    private static final Pattern HEADER_PUT =
            Pattern.compile("header\\(record,\\s*(HEADER_[A-Z_]+),\\s*[^;]*?row\\.(get[A-Za-z]+)\\(\\)");

    /** Ключ записи: им едет тенант, и одноимённого заголовка на проводе нет. */
    private static final Pattern RECORD_KEY = Pattern.compile(
            "new ProducerRecord<>\\(\\s*row\\.getTopic\\(\\),\\s*row\\.getTenantId\\(\\)", Pattern.DOTALL);

    private static final String TOPIC = "trading-core.facts";
    private static final String TENANT = "tenant-1";
    private static final String EVENT_ID = "evt-1";
    private static final String EVENT_TYPE = "DEAL_CLOSED";
    private static final Integer VERSION = 3;
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-09-10T03:00:00Z");
    private static final String TRACE_CONTEXT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    private static final String CONTENT = "{\"dealInternalId\":\"deal-7\"}";

    private final JournalEnvelopeReader reader = new JournalEnvelopeReader(new ObjectMapper());

    @Test
    @DisplayName("Оба построенных публикатора кладут одни и те же заголовки одними и теми же полями")
    void bothPublishersAgreeOnTheWireForm() throws IOException {
        Map<String, String> first = wireForm(PUBLISHERS.get(0));
        Map<String, String> second = wireForm(PUBLISHERS.get(1));

        assertThat(first)
                .as("копии имён у публикаторов разошлись — потребитель не может быть верен обоим")
                .isEqualTo(second);
        assertThat(first).isNotEmpty();
    }

    @Test
    @DisplayName("Потребитель знает ровно те имена заголовков, которые ставит публикатор")
    void theConsumerKnowsExactlyTheNamesThePublisherWrites() throws IOException {
        Map<String, String> published = wireForm(PUBLISHERS.get(0));

        assertThat(Constants.EventHeaders.ALL)
                .as("приватная копия имён у потребителя обязана совпадать с копией публикатора")
                .containsExactlyInAnyOrderElementsOf(published.keySet());
    }

    @Test
    @DisplayName("Тенант едет ключом записи, а не заголовком")
    void theTenantTravelsAsTheRecordKey() throws IOException {
        for (Path publisher : PUBLISHERS) {
            String source = Files.readString(publisher, StandardCharsets.UTF_8);

            assertThat(RECORD_KEY.matcher(source).find())
                    .as("ключом записи обязан быть тенант: потребитель берёт его оттуда — %s", publisher)
                    .isTrue();
            assertThat(wireForm(publisher))
                    .as("одноимённого заголовка на проводе нет вовсе — %s", publisher)
                    .doesNotContainKey("tenantId");
        }
    }

    @Test
    @DisplayName("Сообщение, собранное публикатором, разбирается потребителем целиком")
    void aMessageBuiltByThePublisherIsReadByTheConsumer() throws IOException {
        ConsumerRecord<String, String> message = messageBuiltAs(wireForm(PUBLISHERS.get(0)));

        AuditRecord record = MESSAGES.messageToDomain(reader.read(message));

        assertThat(record.hasCompleteInput())
                .as("вход, собранный построенным публикатором, обязан быть полным")
                .isTrue();
        assertThat(record.getEventId()).isEqualTo(EVENT_ID);
        assertThat(record.getTenantId()).isEqualTo(TENANT);
        assertThat(record.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(record.getOccurredAt())
                .as("момент едет ISO-8601 со смещением — той формой, которую печатает OffsetDateTime")
                .isEqualTo(OCCURRED_AT);
        assertThat(record.getVersion())
                .as("версия едет десятичным числом")
                .isEqualTo(VERSION);
        assertThat(record.getTraceContext()).isEqualTo(TRACE_CONTEXT);
        assertThat(record.getContent()).isEqualTo(CONTENT);
    }

    /**
     * Форма провода публикатора: имя заголовка → поле строки outbox,
     * которое им едет.
     */
    private Map<String, String> wireForm(Path publisher) throws IOException {
        String source = Files.readString(publisher, StandardCharsets.UTF_8);
        Map<String, String> literals = new LinkedHashMap<>();
        Matcher constants = HEADER_CONSTANT.matcher(source);
        while (constants.find()) {
            literals.put(constants.group(1), constants.group(2));
        }
        Map<String, String> form = new LinkedHashMap<>();
        Matcher puts = HEADER_PUT.matcher(source);
        while (puts.find()) {
            String literal = literals.get(puts.group(1));
            if (isNull(literal)) {
                fail("публикатор ставит заголовок без объявленного литерала: " + puts.group(1));
            }
            form.put(literal, puts.group(2));
        }
        return form;
    }

    /** Сообщение, собранное теми же именами и теми же полями, что у публикатора. */
    private ConsumerRecord<String, String> messageBuiltAs(Map<String, String> wireForm) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, TENANT, CONTENT);
        wireForm.forEach((name, field) ->
                record.headers().add(new RecordHeader(name, valueOf(field).getBytes(StandardCharsets.UTF_8))));
        return record;
    }

    /**
     * Значение поля строки outbox в той форме, в которой его печатает
     * публикатор. Поле, которого здесь нет, роняет тест намеренно: публикатор
     * завёл заголовок, о котором потребитель не знает.
     */
    private String valueOf(String field) {
        return switch (field) {
            case "getEventId" -> EVENT_ID;
            case "getEventType" -> EVENT_TYPE;
            case "getVersion" -> String.valueOf(VERSION);
            case "getOccurredAt" -> String.valueOf(OCCURRED_AT);
            case "getTraceContext" -> TRACE_CONTEXT;
            default -> fail("публикатор кладёт на провод поле, которого потребитель не разбирает: " + field);
        };
    }
}
