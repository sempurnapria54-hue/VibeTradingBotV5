package com.example.audit.persistence.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Операнды полноты, читаемые своим подключением под ролью владельца
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Источник один, и интерфейса у него больше нет.</b> Прежде их было
 * два — своё подключение и кросс-подключение к журналу под ролью
 * агрегатов, — и форма выносилась затем, чтобы выбор подключения
 * принадлежал читателю, а не свёртке. Раздел сервиса снял второе
 * подключение вместе с его единственным читателем
 * (.claude/decisions/audit-statistics-split.md); форма без второй
 * реализации была бы носителем без предмета
 * (.claude/rules/design-simplicity.md).
 *
 * <p><b>Класс делегирует уже существующим границам</b>
 * ({@link AuditRecordDataService}, {@link ReceptionStateDataService}), а
 * своих запросов не держит: тропа к этим величинам уже проложена, и второй
 * её носитель разошёлся бы с первым молча.
 *
 * <p><b>Собственной транзакции методы не открывают:</b> границу называет
 * вызывающий — у чистки она общая со снятием момента разрыва.
 */
@Service
@RequiredArgsConstructor
public class JournalCompletenessSource {

    private final AuditRecordDataService auditRecordDataService;
    private final ReceptionStateDataService receptionStateDataService;

    public OffsetDateTime earliestRecordedAt() {
        return auditRecordDataService.earliestRecordedAt();
    }

    public OffsetDateTime latestObservedSince(String consumerGroup) {
        return receptionStateDataService.latestObservedSince(consumerGroup);
    }

    public Long countSubscribedPairs(String consumerGroup) {
        return receptionStateDataService.countSubscribedPairs(consumerGroup);
    }

    public Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime staleBefore) {
        return receptionStateDataService.countSubscribedPairsWithBreak(consumerGroup, staleBefore);
    }
}
