package com.example.tradingbot.domain.service.market;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.persistence.service.MarketPhaseDataService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Раздаёт актуальную фазу рынка потребителям (EntryScannerJob выбирает
 * StrategyDetail по MarketPhase.Type). Сама фазу не считает — её заранее
 * считает MarketPhaseJob. Актуальная фаза = последняя по candle_timestamp
 * для запрашивающей настройки; отдаётся, только если свежа по её
 * expirationDuration (referencePoint = candleTimestamp). См.
 * docs/components/MarketPhaseService.md, docs/rules/market-data-freshness.md.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseService {

    private final MarketPhaseDataService dataService;
    private final MarketDataExpirationChecker expirationChecker;

    /** Актуальная свежая фаза по запрашивающей настройке (пусто — нет или устарела). */
    public Optional<MarketPhase> getLatestPhase(Long instrumentId, StrategyMarketPhaseSetting setting) {
        return dataService.findLatest(instrumentId, setting.getId())
                .filter(phase -> isTrue(expirationChecker.isFresh(
                        phase.getCandleTimestamp(), setting.getExpirationDuration())));
    }
}
