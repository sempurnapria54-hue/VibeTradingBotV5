package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Объявление расчёта структуры рынка в стратегии. Собственная реляционная
 * строка strategy-scope (таблица strategy_market_structure_settings,
 * UNIQUE(strategy_id, key)); адресуется по {@code key} (операнд условия
 * {@code structureKey}, «мягкие» ссылки JSON-листьев placement/stopLossSettings).
 * Результат расчёта MarketStructure ссылается на её {@code id}
 * (owner-ключевание, трек D). ER/ATR-входы резолвера — «мягкие» ссылки по
 * {@code key} на индикаторные настройки стратегии. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyMarketStructureSetting).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMarketStructureSetting {

    /** Технический ID настройки (strategy-scope-строка; цель FK результата расчёта). */
    private Long id;

    /** Стабильный ключ настройки — по нему ссылается операнд market-structure. */
    private String key;

    /** Доменный таймфрейм серии, по которой считается структура. */
    private TimeFrame timeframe;

    /**
     * Ключ настройки каталожного ER-индикатора стратегии, который резолвер
     * потребляет как готовый вход (fork-A). Null — резолвер использует
     * внутренний прокси нетто/суммарного хода.
     */
    private String efficiencyRatioKey;

    /**
     * Ключ настройки каталожного ATR-индикатора стратегии для
     * волатильность-относительного толеранса кластеризации уровней (D3).
     * Null — резолвер откатывается на долю цены.
     */
    private String atrKey;

    /** Параметры расчёта структуры. */
    private MarketStructureParams params;

    /** Назначение результата расчёта внутри стратегии. */
    private Destiny destiny;

    /** Срок свежести рассчитанной структуры (MarketDataExpirationChecker). */
    private Duration expirationDuration;
}
