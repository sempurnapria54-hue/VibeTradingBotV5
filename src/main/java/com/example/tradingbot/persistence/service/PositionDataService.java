package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.persistence.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.tradingbot.util.Constant.ErrorCode.POSITION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PositionDataService {

    private final PositionRepository positionRepository;
    private final PositionMapper mapper;

    @Transactional
    public Position save(Position position) {
        PositionEntity data = mapper.domainToData(position);
        PositionEntity saved = positionRepository.save(data);
        return mapper.dataToDomain(saved);
    }

    public Position findByDealIdRequired(Long dealId) {
        return positionRepository.findByDealId(dealId)
                                 .map(mapper::dataToDomain)
                                 .orElseThrow(() -> new RuntimeException(POSITION_NOT_FOUND));
    }

    public Position findByIdRequired(Long id) {
        return positionRepository.findById(id)
                                 .map(mapper::dataToDomain)
                                 .orElseThrow(() -> new RuntimeException(POSITION_NOT_FOUND));
    }

    public Optional<Position> findByExternalId(String externalId) {
        return positionRepository.findByExternalId(externalId)
                                 .map(mapper::dataToDomain);
    }

    public List<Position> findByInstrumentId(Long instrumentId) {
        return positionRepository.findAllByInstrumentId(instrumentId)
                                 .stream()
                                 .map(mapper::dataToDomain)
                                 .toList();
    }

}
