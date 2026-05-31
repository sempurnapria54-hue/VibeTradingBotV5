package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.mapping.StrategyMapper;
import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import com.example.tradingbot.persistence.repository.StrategyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.domain.model.trade.strategy.StrategyStatus.ACTIVE;
import static com.example.tradingbot.util.Constant.ErrorCode.STRATEGY_NOT_FOUND;
import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class StrategyDataService {

    private final StrategyRepository strategyRepository;
    private final StrategyMapper strategyMapper;

    @Transactional
    public Strategy save(Strategy strategy) {
        StrategyEntity strategyEntity = strategyMapper.domainToData(strategy);
        StrategyEntity savedStrategyEntity = strategyRepository.save(strategyEntity);
        return strategyMapper.dataToDomain(savedStrategyEntity);
    }

    public boolean existsByInternalId(String internalId) {
        if (isNull(internalId)) {
            return false;
        }
        return strategyRepository.existsByInternalId(internalId);
    }

    public Strategy findRequiredByInternalId(String internalId) {
        return strategyRepository.findByInternalId(internalId)
                .map(strategyMapper::dataToDomain)
                .orElseThrow(() -> new RuntimeException(STRATEGY_NOT_FOUND));
    }

    public Strategy findRequiredActiveByInstrumentId(Long instrumentId) {
        Strategy strategy = findActiveByInstrumentId(instrumentId);
        if (isNull(strategy)) {
            throw new RuntimeException(STRATEGY_NOT_FOUND);
        }

        return strategy;
    }

    public Strategy findActiveByInstrumentId(Long instrumentId) {
        if (isNull(instrumentId)) {
            return null;
        }

        return strategyRepository.findFirstByInstrumentIdAndStatusOrderByVersionDesc(instrumentId, ACTIVE.name())
                .map(strategyMapper::dataToDomain)
                .orElse(null);
    }
}
