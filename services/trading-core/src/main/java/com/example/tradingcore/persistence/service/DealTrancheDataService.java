package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.mapping.DealTrancheMapper;
import com.example.tradingcore.persistence.repository.DealTrancheRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для транша.
 *
 * <p>Транши сделки читаются ЦЕЛИКОМ, включая терминальные: ноги
 * собираются их обходом, а числа риска и экспозиция считаются по ногам
 * всей сделки (docs/components/DealContextService.md §«Объёмы загрузки»).
 */
@Service
@RequiredArgsConstructor
public class DealTrancheDataService {

    private final DealTrancheRepository repository;
    private final DealTrancheMapper mapper;

    @Transactional
    public DealTranche save(DealTranche tranche) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(tranche)));
    }

    @Transactional(readOnly = true)
    public List<DealTranche> findByDealId(Long dealId) {
        return repository.findByDealIdOrderByIdAsc(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
