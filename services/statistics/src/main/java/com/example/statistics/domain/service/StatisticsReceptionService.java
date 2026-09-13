package com.example.statistics.domain.service;

import com.example.statistics.config.ReceptionProperties;
import com.example.statistics.config.StatisticsPersistenceConfig;
import com.example.statistics.domain.model.DealFact;
import com.example.statistics.domain.model.IncidentFact;
import com.example.statistics.persistence.service.FactDataService;
import com.example.statistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционные границы приёма события статистикой
 * (docs/rules/durable-consumer-reception.md).
 *
 * <p><b>Критерий деления один и механический:</b> одной транзакцией со
 * строкой факта ложится то, что есть <b>следствие</b> принятого сообщения;
 * свидетельство о самом ходе приёма ложится отдельной. Иначе откат
 * обработки уносит свидетельство ровно тогда, когда оно нужно.
 *
 * <p><b>Ветвей приёма три, а не две.</b> Событие несомого класса кладёт
 * факт своего зерна; событие класса, признаку не отвечающего, факта не
 * порождает — и всё же двигает момент последнего принятого события: оно
 * <b>принято</b>, и молчание об этом сделало бы возраст ложным
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»).
 */
@Service
@RequiredArgsConstructor
public class StatisticsReceptionService {

    private final ReceptionProperties properties;
    private final FactDataService factDataService;
    private final ReceptionStateDataService receptionStateDataService;

    /**
     * Принять сделочный факт: строка факта, момент последнего принятого
     * события и снятие флага остановки — <b>одной</b> транзакцией.
     */
    @Transactional(transactionManager = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER)
    public void accept(DealFact fact, String topic) {
        factDataService.record(fact);
        receptionStateDataService.markAccepted(properties.getGroupId(), topic, fact.getClosedAt());
    }

    /** Принять факт происшествия — той же одной транзакцией. */
    @Transactional(transactionManager = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER)
    public void accept(IncidentFact fact, String topic) {
        factDataService.record(fact);
        receptionStateDataService.markAccepted(properties.getGroupId(), topic, fact.getOccurredAt());
    }

    /**
     * Принять событие, не ставшее фактом: следствия нет, но момент
     * последнего принятого двигается.
     */
    @Transactional(transactionManager = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER)
    public void acceptWithoutFact(String topic, OffsetDateTime occurredAt) {
        receptionStateDataService.markAccepted(properties.getGroupId(), topic, occurredAt);
    }

    /**
     * Отметить остановку приёма по паре — <b>отдельной</b> транзакцией.
     *
     * <p>{@code REQUIRES_NEW} здесь несущий, а не оборонительный: ход
     * зовётся из обработчика ошибок, и транзакция обработки к этому моменту
     * помечена на откат. Флаг, положенный в неё, откатился бы вместе с ней
     * — то есть не появился бы ровно в том случае, ради которого заведён.
     */
    @Transactional(transactionManager = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void noteHalt(String topic) {
        receptionStateDataService.markHalted(properties.getGroupId(), topic);
    }

    /** Отметить обнаруженный разрыв в смещениях по паре. */
    @Transactional(transactionManager = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void noteGap(String topic, OffsetDateTime moment) {
        receptionStateDataService.markGap(properties.getGroupId(), topic, moment);
    }

    /**
     * Наблюдение по паре началось заново: зафиксированного смещения группы
     * не осталось, и доказать непрерывность с прежнего момента нечем.
     */
    @Transactional(transactionManager = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void restartObservation(String topic, OffsetDateTime moment) {
        receptionStateDataService.restartObservation(properties.getGroupId(), topic, moment);
    }
}
