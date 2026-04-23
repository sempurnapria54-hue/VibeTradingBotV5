package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStatus;
import com.example.tradingbot.mapping.StrategyMapper;
import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import com.example.tradingbot.persistence.repository.StrategyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.STRATEGY_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class StrategyDataService {

    private final StrategyRepository strategyRepository;
    private final StrategyMapper strategyMapper;

    @Transactional
    public Strategy save(Strategy strategy) {
        StrategyEntity strategyEntity = strategyMapper.domainToData(strategy);
        StrategyEntity savedStrategyEntity = strategyRepository.save(strategyEntity);

        return strategyRepository.findDetailedById(savedStrategyEntity.getId())
                                 .map(strategyMapper::dataToDomain)
                                 .orElseThrow(() -> new RuntimeException(STRATEGY_NOT_FOUND));
    }

    public Strategy findActiveRequiredByInstrumentId(Long instrumentId) {
        return strategyRepository.findFirstByInstrumentIdAndStatusOrderByVersionDesc(
                                         instrumentId,
                                         StrategyStatus.ACTIVE.name()
                                 )
                                 .map(strategyMapper::dataToDomain)
                                 .orElseThrow(() -> new RuntimeException(STRATEGY_NOT_FOUND));
    }
}
