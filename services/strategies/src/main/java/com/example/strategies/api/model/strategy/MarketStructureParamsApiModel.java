package com.example.strategies.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Параметры расчёта структуры рынка в API. */
@Getter
@Setter
public class MarketStructureParamsApiModel {

    /**
     * Глубина окна расчёта структуры.
     *
     * <p>Обязательность и положительность держит не аннотация, а
     * {@code StrategyDefinitionValidator} кодами
     * {@code STRATEGY_STRUCTURE_LOOKBACK_NOT_DECLARED} и
     * {@code STRATEGY_STRUCTURE_LOOKBACK_NOT_POSITIVE}: реджект создания
     * объявлен доком (docs/rules/strategy-validation.md), а Bean Validation
     * отвечает до тела обработчика — с аннотацией именованный код был бы
     * недостижим.
     */
    @Schema(description = "Глубина окна расчёта структуры, баров (> 0)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer lookbackBars;

    @Positive
    @Schema(description = "Минимальное число касаний уровня для подтверждения")
    private Integer minTouches;

    @PositiveOrZero
    @Schema(description = "Минимальная ширина диапазона, %")
    private BigDecimal minRangeWidthPercents;

    @PositiveOrZero
    @Schema(description = "Максимальная ширина диапазона, %")
    private BigDecimal maxRangeWidthPercents;

    @PositiveOrZero
    @Schema(description = "Буфер подтверждения пробоя, % от ширины диапазона")
    private BigDecimal breakoutBufferPercents;

    @PositiveOrZero
    @Schema(description = "Число баров подтверждения пробоя")
    private Integer breakoutConfirmationBars;

    @Positive
    @Schema(description = "Глубина поиска свингов, баров")
    private Integer swingLookbackBars;

    @PositiveOrZero
    @Schema(description = "Порог ER тренд/диапазон (ER ≥ порога → тренд); пусто — провизорный дефолт, "
            + "калибровка на бэктесте фазы 2")
    private BigDecimal trendEfficiencyThreshold;

    @PositiveOrZero
    @Schema(description = "Множитель k толеранса уровней (толеранс = k·ATR); пусто — провизорный дефолт, "
            + "калибровка на бэктесте фазы 2")
    private BigDecimal levelToleranceAtrMultiplier;
}
