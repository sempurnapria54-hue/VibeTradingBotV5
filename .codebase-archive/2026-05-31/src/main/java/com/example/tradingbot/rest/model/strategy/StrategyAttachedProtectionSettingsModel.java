package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Настройки attached-защиты для entry order.")
public class StrategyAttachedProtectionSettingsModel {

    @NotBlank(message = "Strategy attachedProtection.attachedType is required")
    @Schema(description = "Тип attached-защиты. По текущей доменной модели используется ATTACHED_STOP_LOSS.", example = "ATTACHED_STOP_LOSS")
    private String attachedType;

    @Valid
    @NotNull(message = "Strategy attachedProtection.stopLossSettings is required")
    @Schema(description = "Настройки attached stop-loss.")
    private StopLossSettingsModel stopLossSettings;
}
