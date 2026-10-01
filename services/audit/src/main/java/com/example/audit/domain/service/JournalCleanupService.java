package com.example.audit.domain.service;

import com.example.audit.config.JournalPersistenceConfig;
import com.example.audit.persistence.service.AuditRecordDataService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционная граница прохода чистки журнала
 * (docs/components/JournalCleanupJob.md).
 *
 * <p><b>Каждая порция удаления — своя транзакция, а не одна на весь
 * проход.</b> Одна держала бы блокировки на всём, что накопилось, и обрыв
 * посреди неё откатывал бы уже сделанное. Обрыв между порциями ничего не
 * стои́т: удаление монотонно, и следующий проход доудаляет остаток, а нижняя
 * граница полноты остаётся верной на любом числе применённых порций.
 *
 * <p><b>Строк состояния приёма проход не пишет вовсе</b>, включая момент
 * разрыва: разрыв, который удаление вынесло за нижнюю границу, перестаёт
 * давать дыру сравнением при чтении, а не правкой строки
 * (docs/rules/durable-consumer-reception.md §«Писатели величин — по роли, а
 * не по имени класса»).
 *
 * <p><b>Менеджер транзакций назван, а не подразумевается:</b> подключений
 * у процесса три, и умолчания у выбора нет намеренно
 * ({@link JournalPersistenceConfig}).
 */
@Service
@RequiredArgsConstructor
public class JournalCleanupService {

    private final AuditRecordDataService auditRecordDataService;

    /**
     * Удалить порцию строк, принятых раньше глубины хранения.
     *
     * @return сколько строк удалено — полная порция означает, что старее
     *         глубины что-то ещё осталось
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
    public Integer deleteBatchRecordedBefore(OffsetDateTime threshold, Integer batchSize) {
        return auditRecordDataService.deleteBatchRecordedBefore(threshold, batchSize);
    }
}
