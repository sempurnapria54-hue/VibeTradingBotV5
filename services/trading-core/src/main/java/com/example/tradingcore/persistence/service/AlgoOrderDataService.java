package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.mapping.AlgoOrderMapper;
import com.example.tradingcore.persistence.repository.AlgoOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для отдельной условной заявки. Дерево
 * условия и перечень порождённых заявок едут навесом JSONB через маппер.
 */
@Service
@RequiredArgsConstructor
public class AlgoOrderDataService {

    private final AlgoOrderRepository repository;
    private final AlgoOrderMapper mapper;

    @Transactional
    public AlgoOrder save(AlgoOrder algoOrder) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(algoOrder)));
    }

    /** Условная заявка по идентификатору; нет — исключение. */
    @Transactional(readOnly = true)
    public AlgoOrder getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("AlgoOrder not found: " + id));
    }

    /** Отдельная условная заявка по нашему клиентскому идентификатору; пусто — строки нет. */
    @Transactional(readOnly = true)
    public Optional<AlgoOrder> findByInternalId(String internalId) {
        return repository.findByInternalId(internalId).map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<AlgoOrder> findByDealId(Long dealId) {
        return repository.findByDealId(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
