package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.model.deal.position.PositionEntity;
import com.example.tradingbot.persistence.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Position findByExternalId(String externalId) {
        return positionRepository.findByExternalId(externalId)
                                 .map(mapper::dataToDomain)
                                 .orElse(null);
    }

    public List<Position> findByInstrumentId(Long instrumentId) {
        return positionRepository.findAllByInstrumentId(instrumentId)
                                 .stream()
                                 .map(mapper::dataToDomain)
                                 .collect(Collectors.toList());
    }

    public List<Position> findAllByInstrumentIdAndStatuses(Long instrumentId, Set<String> statuses) {
        return positionRepository.findAllByInstrumentIdAndStatuses(instrumentId, statuses)
                                 .stream()
                                 .map(mapper::dataToDomain)
                                 .collect(Collectors.toList());
    }

}
