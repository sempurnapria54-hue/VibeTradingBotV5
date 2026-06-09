package com.example.tradingbot.domain.service.market;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.persistence.service.MarketStructureConfigDataService;
import com.example.tradingbot.persistence.service.MarketStructureDataService;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Раздаёт готовую структуру рынка и нужные ценовые уровни потребителям.
 * Сама уровни по свечам не ищет — их заранее считает MarketStructureJob.
 * Структура резолвится по идентичности конфигурации запрашивающей
 * настройки (config_id) и отдаётся, только если свежа по её
 * expirationDuration (referencePoint = windowEndAt). См.
 * docs/components/MarketStructureService.md, docs/rules/market-data-freshness.md.
 */
@Service
@RequiredArgsConstructor
public class MarketStructureService {

    private final MarketStructureConfigDataService configDataService;
    private final MarketStructureDataService dataService;
    private final MarketDataExpirationChecker expirationChecker;

    /** Последняя свежая структура по запрашивающей настройке (пусто — нет или устарела). */
    public Optional<MarketStructure> getLatestStructure(Long instrumentId, StrategyMarketStructureSetting setting) {
        Long configId = configDataService.resolveConfigId(setting.getTimeframe(), setting.getParams());
        return dataService.findLatest(instrumentId, configId)
                .filter(structure -> isTrue(expirationChecker.isFresh(
                        structure.getWindowEndAt(), setting.getExpirationDuration())));
    }

    /** Требуемый уровень структуры заданного типа (или ошибка, если уровня нет). */
    public MarketPriceLevel getRequiredLevel(MarketStructure structure, MarketPriceLevel.Type levelType) {
        MarketPriceLevel level = structure.findLevel(levelType);
        if (Objects.isNull(level)) {
            throw new IllegalStateException("Market price level not found: " + levelType);
        }
        return level;
    }
}
