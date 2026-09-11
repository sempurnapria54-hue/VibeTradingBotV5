package com.example.auditstatistics.persistence.service;

import com.example.auditstatistics.persistence.repository.journal.JournalReceptionStateRepository;
import com.example.auditstatistics.persistence.repository.journal.ReceptionPairMomentRow;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для строки состояния приёма.
 *
 * <p><b>Ходы разведены по писателю, а не по числу</b>
 * (docs/models/domain/other/AuditRecord.md, таблица писателей): величины
 * самого приёма пишут слушатель и его обработчик ошибок — все их ходы суть
 * обновления, и пока строки нет, обновление не находит цели и не делает
 * ничего; состав пар и живость пишет тик состояния приёма — он один
 * <b>заводит</b> строку и он же ведёт признак подписки с моментом
 * обновления.
 *
 * <p><b>Транзакции методы не открывают:</b> границы называет вызывающий —
 * одна транзакция у приёма, отдельная у флага остановки, у момента разрыва
 * и у возобновления наблюдения, своя у такта тика.
 */
@Service
@RequiredArgsConstructor
public class ReceptionStateDataService {

    private final JournalReceptionStateRepository repository;

    /** Событие принято: флаг остановки снят, момент подвинут вперёд. */
    public void markAccepted(String consumerGroup, String topic, OffsetDateTime occurredAt) {
        repository.markAccepted(consumerGroup, topic, occurredAt);
    }

    /** Приём по паре остановлен отказом обработки. */
    public void markHalted(String consumerGroup, String topic) {
        repository.markHalted(consumerGroup, topic);
    }

    /** По паре обнаружен разрыв в смещениях. */
    public void markGap(String consumerGroup, String topic, OffsetDateTime moment) {
        repository.markGap(consumerGroup, topic, moment);
    }

    /** Наблюдение по паре началось заново с названного момента. */
    public void restartObservation(String consumerGroup, String topic, OffsetDateTime moment) {
        repository.restartObservation(consumerGroup, topic, moment);
    }

    /** Тема появилась в подписке: строка пары заводится, если её ещё нет. */
    public void openPair(String consumerGroup, String topic, OffsetDateTime moment) {
        repository.openPair(consumerGroup, topic, moment);
    }

    /** Темы подписки: признак подписки — истина, момент обновления — такт. */
    public void markSubscribed(String consumerGroup, Collection<String> topics, OffsetDateTime moment) {
        repository.markSubscribed(consumerGroup, topics, moment);
    }

    /** Темы вне подписки: признак снимается, строка остаётся жить. */
    public void markUnsubscribed(String consumerGroup, Collection<String> topics, OffsetDateTime moment) {
        repository.markUnsubscribed(consumerGroup, topics, moment);
    }

    /**
     * Позднейший момент наблюдения по строкам группы — включая снятые с
     * подписки; пусто — строк у группы нет.
     */
    public OffsetDateTime latestObservedSince(String consumerGroup) {
        return repository.latestObservedSince(consumerGroup);
    }

    /**
     * Сколько пар группа наблюдает сейчас — область квантора обоих её
     * предикатов; область — только подписанные пары.
     */
    public Long countSubscribedPairs(String consumerGroup) {
        return repository.countSubscribedPairs(consumerGroup);
    }

    /**
     * Сколько подписанных пар не утверждают непрерывность: ноль означает,
     * что дыры нет ни на одной.
     */
    public Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime staleBefore) {
        return repository.countSubscribedPairsWithBreak(consumerGroup, staleBefore);
    }

    /**
     * Моменты подписанных пар группы: тема, момент последнего принятого
     * события и момент наблюдения — вход возраста, который сервис отдаёт
     * рядом наблюдателю.
     */
    public List<ReceptionPairMomentRow> subscribedPairMoments(String consumerGroup) {
        return repository.subscribedPairMoments(consumerGroup);
    }

    /**
     * Чистка вынесла разрыв за нижнюю границу полноты: момент разрыва
     * гаснет. Иных величин строки ход не трогает.
     */
    public void clearGapsBefore(String consumerGroup, OffsetDateTime lowerBound) {
        repository.clearGapsBefore(consumerGroup, lowerBound);
    }
}
