package com.example.auditstatistics.persistence.service;

import com.example.auditstatistics.domain.service.JournalCompletenessSource;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Операнды полноты, читаемые под ролью ЖУРНАЛА — своим подключением
 * модуля аудита (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Этим источником полноту берут СВОИ читатели журнала:</b> чистка и
 * журнальная выборка чтения. Чужого подключения им не требуется вовсе —
 * база журнала их собственная.
 *
 * <p><b>Класс делегирует уже существующим границам</b>
 * ({@link AuditRecordDataService}, {@link ReceptionStateDataService}), а
 * своих запросов не держит: тропа под ролью журнала к этим величинам уже
 * проложена, и второй её носитель разошёлся бы с первым молча.
 *
 * <p><b>Собственной транзакции методы не открывают:</b> границу называет
 * вызывающий — у чистки она общая со снятием момента разрыва.
 */
@Service
@RequiredArgsConstructor
public class OwnerJournalCompletenessSource implements JournalCompletenessSource {

    private final AuditRecordDataService auditRecordDataService;
    private final ReceptionStateDataService receptionStateDataService;

    @Override
    public OffsetDateTime earliestRecordedAt() {
        return auditRecordDataService.earliestRecordedAt();
    }

    @Override
    public OffsetDateTime latestObservedSince(String consumerGroup) {
        return receptionStateDataService.latestObservedSince(consumerGroup);
    }

    @Override
    public Long countSubscribedPairs(String consumerGroup) {
        return receptionStateDataService.countSubscribedPairs(consumerGroup);
    }

    @Override
    public Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime staleBefore) {
        return receptionStateDataService.countSubscribedPairsWithBreak(consumerGroup, staleBefore);
    }
}
