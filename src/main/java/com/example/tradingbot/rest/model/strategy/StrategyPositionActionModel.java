package com.example.tradingbot.rest.model.strategy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonTypeName("POSITION")
@Schema(name = "StrategyPositionActionModel", description = "Действие стратегии над позицией.")
public class StrategyPositionActionModel extends StrategyActionModel {

    @NotBlank(message = "Strategy position actionType is required")
    @Schema(description = "Операция над позицией. Для strategy API поддерживаются CLOSE_FULL и CLOSE_PARTIAL.", example = "CLOSE_FULL")
    private String actionType;

    @NotNull(message = "Strategy position action level is required")
    @Schema(description = "Локальный уровень действия внутри step.", example = "1")
    private Integer level;

    @Schema(description = "Доля позиции в процентах, если стратегия закрывает позицию частично.", example = "50")
    private BigDecimal closeFractionPercents;
}
