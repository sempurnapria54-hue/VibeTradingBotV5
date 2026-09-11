package com.example.auditstatistics.integration;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.util.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

/**
 * Граница «сообщение брокера → доменная строка журнала».
 *
 * <p><b>Конверт читается заголовками, тенант — ключом записи, содержимое —
 * телом</b> (docs/architecture/contracts.md §«Как конверт лежит на проводе
 * — поимённо»). Второго носителя формы конверта здесь не заводится: имена
 * лежат приватной копией, и её охрана — сквозной тест формы провода.
 *
 * <p><b>Содержимое не интерпретируется.</b> Оно ложится в строку <b>как
 * доставлено</b>; разбирается только настолько, насколько нужно колонкам
 * радиуса — поиском ОДНОИМЁННОГО компонента верхнего уровня.
 *
 * <p><b>Отсутствующий заголовок означает «значения не было»</b>, и пустая
 * строка от отсутствия отличается
 * (docs/rules/absent-value-semantics.md): пустой заголовок публикатор не
 * ставит вовсе.
 */
@Component
@RequiredArgsConstructor
public class JournalEnvelopeReader {

    private final ObjectMapper objectMapper;

    /**
     * Собрать доменную строку из сообщения.
     *
     * <p><b>Момент приёма ставится здесь</b> — его писателем объявлен код
     * приёма, а не конверт и не база.
     */
    public AuditRecord read(ConsumerRecord<String, String> record) {
        JsonNode content = contentTree(record);
        return AuditRecord.builder()
                .eventId(header(record, Constants.EventHeaders.EVENT_ID))
                .tenantId(record.key())
                .eventType(header(record, Constants.EventHeaders.EVENT_TYPE))
                .occurredAt(moment(header(record, Constants.EventHeaders.OCCURRED_AT)))
                .recordedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .version(version(header(record, Constants.EventHeaders.VERSION)))
                .traceContext(header(record, Constants.EventHeaders.TRACE_CONTEXT))
                .exchangeAccountInternalId(radius(content, Constants.RadiusFields.EXCHANGE_ACCOUNT_INTERNAL_ID))
                .instrumentInternalId(radius(content, Constants.RadiusFields.INSTRUMENT_INTERNAL_ID))
                .dealInternalId(radius(content, Constants.RadiusFields.DEAL_INTERNAL_ID))
                .strategyInternalId(radius(content, Constants.RadiusFields.STRATEGY_INTERNAL_ID))
                .content(record.value())
                .build();
    }

    /** Значение заголовка либо пусто, если заголовка на сообщении нет. */
    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return isNull(header) ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Момент из заголовка. Пустой заголовок даёт пустой момент — это
     * неполный вход, и разбирает его предикат доменной модели; <b>непустое
     * неразбираемое значение роняет обработку</b>: подменить его пустотой
     * значило бы записать строку с ложным «значения не было».
     */
    private OffsetDateTime moment(String value) {
        if (isNull(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IncompleteEventException("Момент происшествия не разбирается: " + value, e);
        }
    }

    /** Версия формы; довод о неразбираемом значении — тот же, что у момента. */
    private Integer version(String value) {
        if (isNull(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IncompleteEventException("Версия формы не разбирается: " + value, e);
        }
    }

    /**
     * Дерево содержимого.
     *
     * <p><b>Неразбираемое содержимое роняет обработку</b>, и это не
     * пропущенная ветвь: колонка объявлена документом, и такую строку
     * отвергла бы сама база. Громкая остановка обратима — молчаливый
     * пропуск потерял бы факт навсегда
     * (docs/models/domain/other/AuditRecord.md §«Пропуск неизвестного
     * класса, законный у периметра, здесь запрещён»). Наблюдаемости у
     * этого класса сегодня нет сверх остановки, и это названо
     * (.claude/work/backlog.md §«Потерянное событие при неразбираемом
     * содержимом — наблюдаемости нет»).
     */
    private JsonNode contentTree(ConsumerRecord<String, String> record) {
        if (isNull(record.value())) {
            return null;
        }
        try {
            return objectMapper.readTree(record.value());
        } catch (JsonProcessingException e) {
            throw new IncompleteEventException("Содержимое события не разбирается как документ", e);
        }
    }

    /**
     * Значение колонки радиуса: одноимённый компонент верхнего уровня,
     * если он есть и является строкой.
     *
     * <p>Компонент той же глубины, но не строковый (объект, массив), —
     * <b>не</b> идентичность, и колонка остаётся пустой: пустота её
     * объявлена значением, а не пробелом.
     */
    private String radius(JsonNode content, String name) {
        if (isNull(content)) {
            return null;
        }
        JsonNode value = content.get(name);
        return isNull(value) || isFalse(value.isTextual()) ? null : value.textValue();
    }
}
