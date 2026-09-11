package com.example.auditstatistics.domain.service;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционные границы прохода чистки журнала
 * (docs/components/JournalCleanupJob.md).
 *
 * <p><b>Границ у прохода две, а не одна, и это несущее разведение.</b>
 * Каждая порция удаления — своя транзакция: одна на весь проход держала бы
 * блокировки на всём, что накопилось, и обрыв посреди неё откатывал бы уже
 * сделанное. Обрыв между порциями ничего не стои́т: удаление монотонно, и
 * следующий проход доудаляет остаток, а нижняя граница полноты остаётся
 * верной на любом числе применённых порций.
 *
 * <p><b>Снятие момента разрыва идёт СВОЕЙ транзакцией, и граница читается
 * внутри неё.</b> Граница, посчитанная до транзакции, описывала бы
 * состояние базы, которого на момент правки уже нет, — и погасила бы
 * момент разрыва по числу, которого никто не видел.
 *
 * <p><b>Менеджер транзакций назван, а не подразумевается:</b> подключений
 * у процесса три, и умолчания у выбора нет намеренно
 * ({@link JournalPersistenceConfig}).
 */
@Service
@RequiredArgsConstructor
public class JournalCleanupService {

    private final AuditRecordDataService auditRecordDataService;
    private final ReceptionStateDataService receptionStateDataService;
    private final JournalCompletenessService journalCompletenessService;
    private final OwnerJournalCompletenessSource journalCompletenessSource;

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

    /**
     * Погасить момент разрыва у пар, чей разрыв чистка вынесла за нижнюю
     * границу полноты.
     *
     * <p><b>Границы нет — не гаснет ничего.</b> Строк состояния у группы
     * нет ни одной, значит нет и пар, у которых был бы момент разрыва:
     * пустая граница здесь не «ноль», по которому гасить нечего, а
     * отсутствие самого вопроса.
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
    public void clearGapsOutsideLowerBound(String consumerGroup) {
        journalCompletenessService.lowerBound(journalCompletenessSource, consumerGroup)
                .ifPresent(bound -> receptionStateDataService.clearGapsBefore(consumerGroup, bound));
    }
}
