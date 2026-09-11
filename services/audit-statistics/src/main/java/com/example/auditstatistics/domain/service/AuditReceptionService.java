package com.example.auditstatistics.domain.service;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционные границы приёма события в журнал
 * (docs/components/AuditEventListener.md §«Транзакционные границы»).
 *
 * <p><b>Критерий деления один и механический:</b> одной транзакцией со
 * строкой журнала ложится то, что есть <b>следствие</b> принятого
 * сообщения; свидетельство о самом ходе приёма ложится отдельной. Иначе
 * откат обработки уносит свидетельство ровно тогда, когда оно нужно.
 *
 * <p><b>Отсюда методов четыре, а не один:</b> приём — одна транзакция;
 * постановка флага остановки, момент разрыва и возобновление наблюдения —
 * каждое своей. Два последних лежат <b>вне обработки сообщения вовсе</b>:
 * их повод — назначение партиций.
 *
 * <p><b>Менеджер транзакций назван у каждого метода.</b> Умолчания у
 * выбора нет намеренно: подключений три, и второй менеджер приедет с
 * читателями базы агрегатов ({@link JournalPersistenceConfig}).
 */
@Service
@RequiredArgsConstructor
public class AuditReceptionService {

    private final ReceptionProperties properties;
    private final AuditRecordDataService auditRecordDataService;
    private final ReceptionStateDataService receptionStateDataService;

    /**
     * Принять событие: строка журнала, момент последнего принятого события
     * и снятие флага остановки — <b>одной</b> транзакцией.
     *
     * <p>Повторная доставка проходит той же тропой и остаётся принятой:
     * конфликт по ключу дедупа поглощает вставка, а снятие флага на
     * повторе законно — повторно доставленное уже в журнале.
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
    public void accept(AuditRecord record, String topic) {
        auditRecordDataService.record(record);
        receptionStateDataService.markAccepted(properties.getGroupId(), topic, record.getOccurredAt());
    }

    /**
     * Отметить остановку приёма по паре — <b>отдельной</b> транзакцией.
     *
     * <p>{@code REQUIRES_NEW} здесь несущий, а не оборонительный: ход
     * зовётся из обработчика ошибок, и транзакция обработки к этому моменту
     * помечена на откат. Флаг, положенный в неё, откатился бы вместе с ней
     * — то есть не появился бы ровно в том случае, ради которого заведён
     * (docs/models/domain/other/AuditRecord.md §«Отдельная транзакция у
     * флага остановки — не деталь реализации, а условие существования
     * флага»).
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void noteHalt(String topic) {
        receptionStateDataService.markHalted(properties.getGroupId(), topic);
    }

    /** Отметить обнаруженный разрыв в смещениях по паре. */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void noteGap(String topic, OffsetDateTime moment) {
        receptionStateDataService.markGap(properties.getGroupId(), topic, moment);
    }

    /**
     * Наблюдение по паре началось заново: зафиксированного смещения группы
     * не осталось, и доказать непрерывность с прежнего момента нечем.
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void restartObservation(String topic, OffsetDateTime moment) {
        receptionStateDataService.restartObservation(properties.getGroupId(), topic, moment);
    }
}
