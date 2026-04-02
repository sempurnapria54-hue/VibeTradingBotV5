package com.example.tradingbot.domain.service.strategy;

import com.example.tradingbot.domain.model.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StrategyService {

    /**
     * Получить активную стратегию по инструменту.
     * <p>
     * Если активной стратегии нет, сервис должен
     * выбросить доменное исключение.
     */
    public Strategy getActiveStrategyRequired(Long instrumentId) {
        return new Strategy();
    }

}
