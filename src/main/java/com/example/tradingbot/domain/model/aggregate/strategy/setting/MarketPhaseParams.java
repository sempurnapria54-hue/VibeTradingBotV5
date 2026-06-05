package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры алгоритма классификации фазы рынка. Это params
 * реляционного контейнера {@link StrategyMarketPhaseSetting} (JSONB-
 * колонка params на его строке), не часть JSON-массивов листовых
 * настроек. См. docs/models/domain/aggregate/Strategy.md
 * (§MarketPhaseParams), docs/decisions/strategy-tree-persistence.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketPhaseParams {

    /** Алгоритм классификации фазы. */
    private AlgorithmType algorithmType;

    /** Минимальный балл, с которого фаза признаётся трендом. */
    private BigDecimal minTrendScore;

    /** Минимальный балл, с которого фаза признаётся диапазоном. */
    private BigDecimal minRangeScore;

    /** Число баров подтверждения смены фазы. */
    private Integer confirmationBars;

    /** Источник данных алгоритма классификации фазы рынка. */
    public enum AlgorithmType {

        /** Только структура рынка. */
        STRUCTURE_ONLY,

        /** Только индикаторы. */
        INDICATORS_ONLY,

        /** Структура рынка и индикаторы вместе. */
        STRUCTURE_AND_INDICATORS
    }
}
