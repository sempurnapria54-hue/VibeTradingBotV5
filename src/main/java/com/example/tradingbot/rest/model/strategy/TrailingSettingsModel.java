package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Настройки trailing-защиты.")
public class TrailingSettingsModel {

    @Schema(description = "После какого профита можно включить trailing. Если null, trailing активируется сразу.", example = "2.0")
    private BigDecimal activationProfitPercents;

    @NotNull(message = "Strategy trailingSettings.callbackPercents is required")
    @Schema(description = "Расстояние trailing от экстремума.", example = "0.70")
    private BigDecimal callbackPercents;

    @Schema(description = "Дополнительный буфер после активации trailing.", example = "0.10")
    private BigDecimal activationBufferPercents;
}
