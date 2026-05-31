package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.mapping.DealMapper;
import com.example.tradingbot.persistence.model.deal.DealEntity;
import com.example.tradingbot.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public Optional<Deal> findLatestByInstrumentId(Long instrumentId) {
        return dealRepository.findTopByInstrumentIdOrderByIdDesc(instrumentId)
                             .map(mapper::dataToDomain);
    }

    public List<Deal> findByInstrumentId(Long instrumentId) {
        return dealRepository.findAllByInstrumentId(instrumentId)
                             .stream()
                             .map(mapper::dataToDomain)
                             .toList();
    }

    public List<Deal> findAllByInstrumentIdAndStatuses(Long instrumentId, Set<String> statuses) {
        return dealRepository.findAllByInstrumentIdAndStatuses(instrumentId, statuses)
                             .stream()
                             .map(mapper::dataToDomain)
                             .toList();
    }
}
