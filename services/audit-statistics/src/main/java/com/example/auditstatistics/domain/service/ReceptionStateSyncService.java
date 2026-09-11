package com.example.auditstatistics.domain.service;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.domain.model.ReceptionPairMoments;
import com.example.auditstatistics.mapping.ReceptionStateMapper;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционная граница такта тика состояния приёма: состав строк пар
 * приводится к подписке группы
 * (docs/components/ReceptionStateJob.md §«Что тик пишет»).
 *
 * <p><b>Такт — ОДНА транзакция, а не транзакция на тему.</b> Состав пар и
 * признак подписки читаются вместе — предикат непрерывности берёт свёртку
 * по всем строкам группы, — и половина применённого такта показала бы
 * читателю состав, которого не было ни до, ни после.
 *
 * <p><b>Граница у такта своя, а не общая с приёмом.</b> Писателей у строки
 * два, и поводы у них разные: приём пишет следствие сообщения, тик —
 * свидетельство о составе и живости (docs/components/AuditEventListener.md
 * §«Транзакционные границы»). Общая транзакция связала бы откат одного с
 * откатом другого.
 *
 * <p><b>Менеджер транзакций назван, а не подразумевается:</b> подключений
 * у процесса три, и умолчания у выбора нет намеренно
 * ({@link JournalPersistenceConfig}).
 */
@Service
@RequiredArgsConstructor
public class ReceptionStateSyncService {

    private final ReceptionStateDataService receptionStateDataService;
    private final ReceptionStateMapper receptionStateMapper;

    /**
     * Привести состав строк пар к подписке группы.
     *
     * <p><b>Порядок ходов несущий.</b> Сперва заводятся строки тем,
     * которых ещё нет: иначе появившаяся тема получила бы признак
     * подписки только со следующего такта. Затем признак ставится всем
     * темам подписки и снимается у тем, из подписки ушедших; <b>строки
     * ушедших не удаляются</b> — удаление опустило бы нижнюю границу
     * полноты.
     *
     * <p><b>Пустая подписка сюда не доходит:</b> тик молчит, когда приём
     * не жив, а живой приём держит назначенные партиции — то есть хотя бы
     * одну тему. Вызов с пустым составом был бы вопросом «какие темы не в
     * подписке» без области сравнения, и его отвергает сам тик
     * (docs/components/ReceptionStateJob.md §«Тик молчит, когда приём не
     * жив»).
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
    public void syncSubscription(String consumerGroup, Collection<String> topics, OffsetDateTime moment) {
        for (String topic : topics) {
            receptionStateDataService.openPair(consumerGroup, topic, moment);
        }
        receptionStateDataService.markSubscribed(consumerGroup, topics, moment);
        receptionStateDataService.markUnsubscribed(consumerGroup, topics, moment);
    }

    /**
     * Моменты подписанных пар группы — вход рядов экспорта того же такта.
     *
     * <p><b>Чтение отдельным ходом, а не возвратом приведения состава.</b>
     * Ход, который и пишет, и отвечает, спрашивают ради ответа — и тогда
     * состав пар пришлось бы приводить всякий раз, когда нужны только
     * моменты. Транзакция у чтения своя и читающая; состав она застаёт уже
     * применённым, потому что приведение к этому моменту зафиксировано.
     *
     * <p><b>Область — подписанные пары</b>, и она та же, что у свёртки
     * алерта: у снятой темы нет ни производителя лага, ни предмета алерта
     * (docs/spec/audit-journal.json, {@code lagAlertFires}).
     */
    @Transactional(readOnly = true, transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
    public List<ReceptionPairMoments> subscribedPairMoments(String consumerGroup) {
        return receptionStateMapper.persistenceToDomain(
                receptionStateDataService.subscribedPairMoments(consumerGroup));
    }
}
