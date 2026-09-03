package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.mapping.DealCashFlowMapper;
import com.example.tradingbot.persistence.repository.DealCashFlowRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link DealCashFlow}
 * (docs/models/domain/other/DealCashFlow.md). Дедуп повторной добычи —
 * предикат по ключу идемпотентности (exchange_id, external_bill_id);
 * сам ключ держит схема (docs/rules/idempotency-via-unique.md).
 */
@Service
@RequiredArgsConstructor
public class DealCashFlowDataService {

    private final DealCashFlowRepository repository;
    private final DealCashFlowMapper mapper;

    @Transactional
    public DealCashFlow save(DealCashFlow flow) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(flow)));
    }

    /** Движение уже сохранено этой биржей — повторная добыча строку не заводит. */
    @Transactional(readOnly = true)
    public Boolean exists(Long exchangeId, String externalBillId) {
        return repository.existsByExchangeIdAndExternalBillId(exchangeId, externalBillId);
    }

    /** Строки разбивки сделки — операнды предиката остановки звена и догона курса. */
    @Transactional(readOnly = true)
    public List<DealCashFlow> findByDeal(Long dealId) {
        return repository.findByDealId(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
