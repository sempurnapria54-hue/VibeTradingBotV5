package com.example.tradingbot.rest.model.strategy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonTypeName("ORDER")
@Schema(name = "StrategyOrderActionModel", description = "Обычный order action стратегии.")
public class StrategyOrderActionModel extends StrategyActionModel {

    @NotBlank(message = "Strategy order actionType is required")
    @Schema(description = "Операция над ордером. Для strategy API поддерживаются CREATE, AMEND и CANCEL.", example = "CREATE")
    private String actionType;

    @NotBlank(message = "Strategy orderType is required")
    @Schema(description = "Доменный тип ордера.", example = "ENTRY_ATTACHED_STOP_LOSS")
    private String orderType;

    @NotBlank(message = "Strategy order direction is required")
    @Schema(description = "Нормализованное торговое направление стратегии.", example = "LONG")
    private String direction;

    @Schema(description = "Доля расчётного объёма сценария в процентах.", example = "100")
    private BigDecimal allocationPercents;

    @NotNull(message = "Strategy order action level is required")
    @Schema(description = "Локальный уровень действия внутри step.", example = "1")
    private Integer level;

    @Valid
    @Schema(description = "Правила позиционирования цены ордера.")
    private StrategyPricePlacementModel placement;

    @Valid
    @Schema(description = "Attached-защита, если entry order создаётся сразу с attached stop-loss.")
    private StrategyAttachedProtectionSettingsModel attachedProtection;
}
