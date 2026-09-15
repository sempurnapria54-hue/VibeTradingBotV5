package com.example.strategies.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Действие над standalone algo-order (API, actionKind = ALGO_ORDER). */
@Getter
@Setter
public class StrategyAlgoOrderActionApiModel extends StrategyActionApiModel {

    @NotBlank
    @Schema(description = "Тип условия: STOP_LOSS/TAKE_PROFIT/OCO_FULL/TRAILING_PERCENTS/TRAILING_VALUE/"
            + "PARTIAL_TAKE_PROFIT/PARTIAL_STOP_LOSS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionType;

    @Valid
    @Schema(description = "Настройки stop-loss (SL/OCO-нога)")
    private StopLossSettingsApiModel stopLossSettings;

    @Valid
    @Schema(description = "Настройки трейлинга (TRAILING_*)")
    private TrailingSettingsApiModel trailingSettings;

    /**
     * Доля закрытия позиции защитным либо выходным действием.
     *
     * <p>Диапазон {@code (0; 100]} держит не аннотация, а
     * {@code StrategyDefinitionValidator} кодом
     * {@code STRATEGY_ACTION_FRACTION_NOT_POSITIVE} — по тому же доводу,
     * что у доли аллокации входного действия.
     */
    @Schema(description = "Доля закрытия позиции, % (0; 100]")
    private BigDecimal closeFractionPercents;

    @Positive
    @Schema(description = "Порог прибыли trigger'а TP, %")
    private BigDecimal triggerProfitPercents;

    @Schema(description = "Тип trigger-цены биржи: LAST/INDEX/MARK")
    private String triggerPriceType;
}
