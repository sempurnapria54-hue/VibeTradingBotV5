package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.persistence.service.MarketStructureDataService;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Раздаёт готовую структуру рынка и нужные ценовые уровни потребителям.
 * Сама уровни по свечам не ищет — их заранее считает
 * {@code MarketStructureJob}. Структура резолвится по <b>идентичности
 * вычисления</b> и отдаётся, только если свежа под <b>толерантность
 * запрашивающего</b> (referencePoint = {@code windowEndAt}). См.
 * docs/components/MarketStructureService.md,
 * docs/rules/market-data-freshness.md.
 */
@Service
@RequiredArgsConstructor
public class MarketStructureService {

    private final MarketStructureDataService dataService;
    private final MarketDataExpirationChecker expirationChecker;

    /** Последняя структура идентичности, свежая под толерантность читателя (пусто — нет или устарела). */
    public Optional<MarketStructure> getLatestStructure(Long instrumentId, Long marketStructureConfigId,
                                                        Duration tolerance) {
        return dataService.findLatest(instrumentId, marketStructureConfigId)
                .filter(structure -> isTrue(expirationChecker.isFresh(structure.getWindowEndAt(), tolerance)));
    }

    /** Требуемый уровень структуры заданного типа (или ошибка, если уровня нет). */
    public MarketPriceLevel getRequiredLevel(MarketStructure structure, MarketPriceLevel.Type levelType) {
        MarketPriceLevel level = structure.findLevel(levelType);
        if (isNull(level)) {
            throw new IllegalStateException("Market price level not found: " + levelType);
        }
        return level;
    }
}
