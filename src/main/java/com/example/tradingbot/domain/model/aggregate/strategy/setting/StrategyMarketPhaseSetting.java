package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Настройка расчёта фазы рынка. Живёт на уровне Strategy (одна на
 * стратегию), потому что фаза нужна до выбора StrategyDetail: одна
 * настройка описывает классификацию рынка во все MarketPhase.Type;
 * MarketPhaseJob сохраняет один актуальный результат, EntryScannerJob
 * выбирает detail по типу фазы. Каркасный реляционный узел дерева
 * (своя строка/таблица); листовые настройки и params — JSONB на его
 * строке. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyMarketPhaseSetting).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyMarketPhaseSetting extends Auditable {

    /** Технический ID узла. */
    private Long id;

    /** Доменный таймфрейм классификации фазы. */
    private TimeFrame timeframe;

    /**
     * Авторские правила классификации фазы (упорядоченный first-match-
     * список клауз «условие → фаза»). Заменяют распущенный скоринговый
     * MarketPhaseParams (docs/decisions/market-phase-conditional-classification.md).
     */
    private List<StrategyMarketPhaseRule> phaseRules;

    /** Индикаторы для расчёта фазы (destiny = MARKET_PHASE). */
    private List<StrategyIndicatorSetting> indicatorSettings;

    /** Структуры рынка для расчёта фазы (destiny = MARKET_PHASE). */
    private List<StrategyMarketStructureSetting> marketStructureSettings;

    /** Срок свежести последней рассчитанной MarketPhase. */
    private Duration expirationDuration;
}
