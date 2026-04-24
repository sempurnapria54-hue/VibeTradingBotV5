package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Правила расчёта цены order-action относительно выбранной базы.")
public class StrategyPricePlacementModel {

    @NotBlank(message = "Strategy placement.baseType is required")
    @Schema(description = "Базовый ценовой уровень для расчёта placement.", example = "RANGE_LOW")
    private String baseType;

    @Schema(description = "Тип рыночной цены, если `baseType = MARKET_PRICE`.", example = "MARK")
    private String marketPriceType;

    @NotBlank(message = "Strategy placement.offsetSide is required")
    @Schema(description = "Направление смещения цены относительно базы.", example = "ABOVE")
    private String offsetSide;

    @NotNull(message = "Strategy placement.percents is required")
    @Schema(description = "Процент смещения от выбранной базы.", example = "10")
    private BigDecimal percents;
}
