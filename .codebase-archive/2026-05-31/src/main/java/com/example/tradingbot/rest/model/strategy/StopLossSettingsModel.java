package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Настройки расчёта stop-loss для strategy-layer.")
public class StopLossSettingsModel {

    @NotBlank(message = "Strategy stopLossSettings.calculationType is required")
    @Schema(description = "Тип расчёта stop-loss. В актуальной модели поддерживаются ENTRY_PRICE_PERCENT, ATR_PERCENT, MARKET_STRUCTURE_BUFFER_PERCENT.", example = "ATR_PERCENT")
    private String calculationType;

    @NotNull(message = "Strategy stopLossSettings.distancePercents is required")
    @Schema(description = "Универсальное процентное расстояние для расчёта SL.", example = "150")
    private BigDecimal distancePercents;

    @NotBlank(message = "Strategy stopLossSettings.triggerPriceType is required")
    @Schema(description = "Тип рыночной цены для trigger-based stop-loss.", example = "MARK")
    private String triggerPriceType;
}
