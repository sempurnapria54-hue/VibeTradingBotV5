package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Настройки attached-защиты входного ордера (API). */
@Getter
@Setter
public class StrategyAttachedProtectionSettingsApiModel {

    @NotBlank
    @Schema(description = "Тип attached-защиты: ATTACHED_STOP_LOSS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String attachedType;

    @Valid
    @NotNull
    @Schema(description = "Настройки расчёта attached stop-loss", requiredMode = Schema.RequiredMode.REQUIRED)
    private StopLossSettingsApiModel stopLossSettings;
}
