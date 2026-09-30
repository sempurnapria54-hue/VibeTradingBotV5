package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры расчёта структуры рынка (уровни, диапазоны, свинги).
 * Хранятся JSONB внутри JSON настройки-владельца
 * ({@link StrategyMarketStructureSetting};
 * docs/models/domain/aggregate/Strategy.md §«Настройки рыночных данных»).
 * Смысл каждого операнда держит
 * docs/models/domain/other/MarketStructure.md §«Семантика классификации
 * (как считается)».
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketStructureParams {

    /** Глубина окна расчёта структуры (баров). */
    private Integer lookbackBars;

    /** Минимальное число касаний уровня для его подтверждения. */
    private Integer minTouches;

    /** Минимальная ширина диапазона, % (уже — диапазон не признаётся). */
    private BigDecimal minRangeWidthPercents;

    /** Максимальная ширина диапазона, % (шире — диапазон не признаётся). */
    private BigDecimal maxRangeWidthPercents;

    /**
     * Буфер подтверждения пробоя, % от цены пробиваемого уровня; база
     * процента — docs/models/domain/other/MarketStructure.md §«Семантика
     * классификации (как считается)», пункт о пробое.
     */
    private BigDecimal breakoutBufferPercents;

    /** Число баров подтверждения пробоя. */
    private Integer breakoutConfirmationBars;

    /** Глубина поиска свингов (баров). */
    private Integer swingLookbackBars;

    /**
     * Порог ER (тренд vs диапазон): ER ≥ порога → тренд (D2). Null —
     * провизорный консервативный дефолт резолвера. Значение калибруется
     * на бэктест-гейте фазы 2 (числом в канон не зашивается).
     */
    private BigDecimal trendEfficiencyThreshold;

    /**
     * Множитель k волатильность-относительного толеранса кластеризации
     * уровней (толеранс = k·ATR, D3). Null — провизорный дефолт.
     * Значение калибруется на бэктест-гейте фазы 2.
     */
    private BigDecimal levelToleranceAtrMultiplier;
}
