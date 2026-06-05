package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Действие над ordinary order (API, actionKind = ORDER). */
@Getter
@Setter
public class StrategyOrderActionApiModel extends StrategyActionApiModel {

    @NotBlank
    @Schema(description = "Бизнес-тип ордера: ENTRY/ENTRY_ATTACHED_STOP_LOSS",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderType;

    @NotBlank
    @Schema(description = "Торговое направление: LONG/SHORT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String direction;

    @Positive
    @DecimalMax("100")
    @Schema(description = "Доля расчётного объёма, % (0; 100]")
    private BigDecimal allocationPercents;

    @Schema(description = "Намерение reduce-only: ордер только уменьшает позицию")
    private Boolean positionReducingOnly;

    @Valid
    @Schema(description = "Правило расчёта цены размещения; пусто — market-like вход")
    private StrategyPricePlacementApiModel placement;

    @Valid
    @Schema(description = "Attached-защита (обязательна для ENTRY_ATTACHED_STOP_LOSS)")
    private StrategyAttachedProtectionSettingsApiModel attachedProtection;
}
