package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.mapping.DealMapper;
import com.example.tradingbot.persistence.repository.DealRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Deal}. Runtime graph
 * (orders/algoOrders/position) собирается отдельными DataService'ами по
 * deal_id, не здесь.
 */
@Service
@RequiredArgsConstructor
public class DealDataService {

    private static final List<String> TERMINAL_STATUSES = List.of(
            Deal.Status.CLOSED.name(), Deal.Status.EMERGENCY_CLOSED.name());

    private final DealRepository repository;
    private final DealMapper mapper;

    @Transactional
    public Deal save(Deal deal) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(deal)));
    }

    @Transactional(readOnly = true)
    public Deal getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public Deal getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + id));
    }

    /** Активные сделки (статус не terminal), ограниченным окном — для прохода оркестратора. */
    @Transactional(readOnly = true)
    public List<Deal> findActive(Integer limit) {
        return repository.findByStatusNotInOrderByIdAsc(TERMINAL_STATUSES, PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /** Есть ли по инструменту незавершённая сделка (gatekeeper входа). */
    @Transactional(readOnly = true)
    public Boolean existsActiveByInstrumentId(Long instrumentId) {
        return repository.existsByInstrumentIdAndStatusNotIn(instrumentId, TERMINAL_STATUSES);
    }

    /**
     * Двигает порог доказанного покрытия сделки вперёд по наблюдённому
     * моменту. Монотонность обеспечивает охрана запроса, не вызывающий.
     */
    @Transactional
    public void advanceCoverageProvenThrough(Long dealId, OffsetDateTime observedAt) {
        repository.advanceCoverageProvenThrough(dealId, observedAt);
    }

    /** Активные сделки всех инструментов биржи — для каскадного exchange-scoped kill-switch (L4). */
    @Transactional(readOnly = true)
    public List<Deal> findActiveByExchangeId(Long exchangeId) {
        return repository.findActiveByExchangeId(exchangeId, TERMINAL_STATUSES).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
