package com.example.auditstatistics.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.model.JournalQuery;
import com.example.auditstatistics.mapping.AuditRecordMapper;
import com.example.auditstatistics.persistence.repository.journal.AuditRecordRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для строк журнала.
 *
 * <p><b>Собственной транзакции метод не открывает.</b> Строка журнала
 * ложится той же транзакцией, что и снятие флага остановки и момент
 * последнего принятого события: они суть следствие принятого сообщения, а
 * откат обработки обязан уносить их вместе
 * (docs/components/AuditEventListener.md §«Транзакционные границы»).
 */
@Service
@RequiredArgsConstructor
public class AuditRecordDataService {

    private final AuditRecordRepository repository;
    private final AuditRecordMapper mapper;

    /**
     * Записать принятое событие. Повторная доставка того же события
     * оставляет уже лежащую строку и отказом не является — конфликт по
     * ключу дедупа поглощает сама вставка.
     */
    public void record(AuditRecord record) {
        repository.insertAbsorbingDuplicate(record.getEventId(),
                record.getTenantId(),
                record.getEventType(),
                record.getOccurredAt(),
                record.getRecordedAt(),
                record.getVersion(),
                record.getTraceContext(),
                record.getExchangeAccountInternalId(),
                record.getInstrumentInternalId(),
                record.getDealInternalId(),
                record.getStrategyInternalId(),
                record.getContent());
    }

    /**
     * Удалить порцию строк, принятых раньше названного момента.
     *
     * @return сколько строк удалено; полная порция означает, что старое
     *         осталось и проход повторяется
     */
    public Integer deleteBatchRecordedBefore(OffsetDateTime threshold, Integer batchSize) {
        return repository.deleteBatchRecordedBefore(threshold, batchSize);
    }

    /**
     * Самый ранний момент приёма среди сохранившихся строк; пусто —
     * журнал пуст.
     */
    public OffsetDateTime earliestRecordedAt() {
        return repository.earliestRecordedAt();
    }

    /**
     * Прочитать строки окна — от новых к старым, с курсорной позицией и
     * необязательными отборами по радиусам.
     *
     * <p><b>Половины курсора уезжают в запрос по отдельности</b>, потому
     * что пустой курсор означает «первая страница окна», и различает эти
     * состояния сам запрос. Половина курсора сюда не доезжает — вопрос с
     * ней отвергается раньше ({@code JournalQuery#hasPartialCursor}).
     *
     * @param limit сколько строк прочитать; вызывающий просит на одну
     *              больше страницы, чтобы узнать, дочитано ли окно
     */
    public List<AuditRecord> findPage(JournalQuery query, Integer limit) {
        return repository.findPage(query.getTenantId(),
                        query.getFrom(),
                        query.getTo(),
                        query.getCursorOccurredAt(),
                        query.getCursorEventId(),
                        query.getExchangeAccountInternalId(),
                        query.getInstrumentInternalId(),
                        query.getDealInternalId(),
                        query.getStrategyInternalId(),
                        limit).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
