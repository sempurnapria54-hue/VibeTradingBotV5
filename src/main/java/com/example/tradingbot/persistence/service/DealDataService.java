package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.mapping.DealMapper;
import com.example.tradingbot.persistence.model.DealEntity;
import com.example.tradingbot.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.example.tradingbot.util.Constant.ErrorCode.DEAL_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class DealDataService {

    private final DealRepository dealRepository;
    private final DealMapper mapper;

    public Deal save(Deal deal) {
        DealEntity dealEntity = mapper.domainToData(deal);
        DealEntity saved = dealRepository.save(dealEntity);
        return mapper.dataToDomain(saved);
    }

    public Deal findRequiredByInternalId(String internalId) {
        return dealRepository.findByInternalId(internalId)
                             .map(mapper::dataToDomain)
                             .orElseThrow(() -> new RuntimeException(DEAL_NOT_FOUND));
    }

    public Deal findRequiredById(Long id) {
        return dealRepository.findById(id)
                             .map(mapper::dataToDomain)
                             .orElseThrow(() -> new RuntimeException(DEAL_NOT_FOUND));
    }
}
