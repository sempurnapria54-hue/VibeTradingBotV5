package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.PositionEntity;
import com.example.tradingbot.persistence.repository.PositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PositionDataService {

    private final PositionRepository positionRepository;

    @Transactional
    public PositionEntity save(PositionEntity positionEntity) {
        return positionRepository.save(positionEntity);
    }

    @Transactional
    public List<PositionEntity> saveAll(List<PositionEntity> positionEntities) {
        return positionRepository.saveAll(positionEntities);
    }

    public List<PositionEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId) {
        return positionRepository.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
    }
}
