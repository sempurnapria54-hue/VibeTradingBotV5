package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Правило расчёта цены размещения ордера (API); market-like вход — null. */
@Getter
@Setter
public class StrategyPricePlacementApiModel {

    @NotBlank
    @Schema(description = "База цены: RANGE_LOW/RANGE_HIGH/SWING_LOW/SWING_HIGH/SUPPORT/RESISTANCE/"
            + "ENTRY_PRICE/MARKET_PRICE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String baseType;

    @Schema(description = "Источник рыночной цены (только для MARKET_PRICE)")
    private String priceSource;

    @Schema(description = "Ключ настройки структуры рынка (для структурных baseType)")
    private String structureKey;

    @Schema(description = "Сторона смещения от базы: ABOVE/BELOW")
    private String offsetSide;

    @PositiveOrZero
    @Schema(description = "Величина смещения, % (якорь процента — STRAT-Q4)")
    private BigDecimal percents;
}
