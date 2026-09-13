package com.example.statistics.persistence.service;

import com.example.statistics.domain.model.DealFact;
import com.example.statistics.domain.model.IncidentFact;
import com.example.statistics.persistence.repository.facts.DealFactRepository;
import com.example.statistics.persistence.repository.facts.IncidentFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для фактов
 * (.claude/rules/codestyle.md §Слои).
 *
 * <p><b>Тропа у факта одна — вставка, поглощающая повтор.</b> Ни правки,
 * ни удаления у него не бывает: факт неизменяем, а глубины хранения у
 * фактов нет ни в одном окружении
 * (docs/models/domain/other/StatisticsFact.md §Персистентность).
 *
 * <p><b>Собственной транзакции методы не открывают:</b> границу называет
 * вызывающий — приём кладёт строку факта и величины состояния приёма одной
 * транзакцией.
 */
@Service
@RequiredArgsConstructor
public class FactDataService {

    private final DealFactRepository dealFactRepository;
    private final IncidentFactRepository incidentFactRepository;

    /** Записать сделочный факт. */
    public void record(DealFact fact) {
        dealFactRepository.insertAbsorbingDuplicate(fact.getEventId(),
                fact.getTenantId(),
                fact.getExchangeAccountInternalId(),
                fact.getStrategyInternalId(),
                fact.getResultCurrency(),
                fact.getClosedAt(),
                fact.getTookRisk(),
                fact.getGraphComplete(),
                fact.getNetResult(),
                fact.getFee(),
                fact.getFunding(),
                fact.getLiquidationPenalty(),
                fact.getPlannedRisk(),
                fact.getCloseOutcome(),
                fact.getReconciliationStatus(),
                fact.getBreakdownIncomplete(),
                fact.getRiskBenchmarkAvailability());
    }

    /** Записать факт происшествия. */
    public void record(IncidentFact fact) {
        incidentFactRepository.insertAbsorbingDuplicate(fact.getEventId(),
                fact.getTenantId(),
                fact.getExchangeAccountInternalId(),
                fact.getOccurredAt(),
                fact.getEventType(),
                fact.getHoldRung(),
                fact.getAnomalySeverity(),
                fact.getOperationCode());
    }
}
