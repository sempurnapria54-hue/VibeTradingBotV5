package com.example.tradingbot.domain.service.strategy;

import com.example.tradingbot.domain.model.strategy.Strategy;
import com.example.tradingbot.persistence.service.StrategyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyDataService strategyDataService;

    /**
     * Получить активную стратегию по инструменту.
     * <p>
     * Если активной стратегии нет, сервис должен
     * выбросить доменное исключение.
     */
    public Strategy getActiveStrategyRequired(Long instrumentId) {
        return strategyDataService.findActiveRequiredByInstrumentId(instrumentId);
    }

    public Strategy save(Strategy strategy) {
        return strategyDataService.save(strategy);
    }

}
