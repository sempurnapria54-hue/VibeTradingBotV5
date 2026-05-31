package com.example.tradingbot.domain.service.market;

import com.example.tradingbot.domain.model.trade.market.MarketPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketPhaseService {

    public MarketPhase getMarketPhase(Long instrumentId) {
        return new MarketPhase();
    }
}
