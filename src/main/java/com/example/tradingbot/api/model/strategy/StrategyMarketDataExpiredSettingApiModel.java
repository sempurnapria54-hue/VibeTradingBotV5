package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Политика шага на устаревание нужных ему рыночных данных (API). */
@Getter
@Setter
public class StrategyMarketDataExpiredSettingApiModel {

    @NotBlank
    @Schema(description = "Реакция при защищённой позиции: WAIT/BLOCK_STEP/GRACEFUL_CLOSE/KILL_SWITCH",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String protectedPositionAction;

    @NotBlank
    @Schema(description = "Реакция при незащищённой позиции: WAIT/BLOCK_STEP/GRACEFUL_CLOSE/KILL_SWITCH",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String unprotectedPositionAction;
}
