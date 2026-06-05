package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Параметры индикатора OBV в API (indicatorType = OBV). */
@Getter
@Setter
public class ObvParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Schema(description = "Включён ли расчёт OBV", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
}
