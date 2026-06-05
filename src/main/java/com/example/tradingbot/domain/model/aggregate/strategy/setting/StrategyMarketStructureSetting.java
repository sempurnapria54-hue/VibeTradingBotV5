package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Объявление расчёта структуры рынка в стратегии. Хранится JSONB
 * внутри строки контейнера (StrategyMarketPhaseSetting /
 * StrategyDetail), своей строки/таблицы не имеет; адресуется по
 * {@code key} (операнд условия {@code structureKey}, «мягкие» ссылки
 * JSON-листьев placement/stopLossSettings). У структуры, в отличие от
 * индикатора, таймфрейм — прямое поле настройки (провизорная
 * асимметрия — docs/decisions/strategy-tree-persistence.md). См.
 * docs/models/domain/aggregate/Strategy.md
 * (§StrategyMarketStructureSetting).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMarketStructureSetting {

    /** Стабильный ключ настройки — по нему ссылается операнд market-structure. */
    private String key;

    /** Доменный таймфрейм серии, по которой считается структура. */
    private TimeFrame timeframe;

    /** Тип рассчитываемой структуры рынка. */
    private MarketStructure.Type structureType;

    /** Параметры расчёта структуры. */
    private MarketStructureParams params;

    /** Назначение результата расчёта внутри стратегии. */
    private Destiny destiny;

    /** Срок свежести рассчитанной структуры (MarketDataExpirationChecker). */
    private Duration expirationDuration;
}
