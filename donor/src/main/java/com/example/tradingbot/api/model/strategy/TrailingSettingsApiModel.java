package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Настройки трейлинг-стопа (API). */
@Getter
@Setter
public class TrailingSettingsApiModel {

    @Positive
    @Schema(description = "Порог прибыли активации трейлинга, %; пусто — активен сразу "
            + "(ранняя активация выбивается шумом — рекомендуем порог)")
    private BigDecimal activationProfitPercents;

    @NotNull
    @Positive
    @Schema(description = "Callback трейлинга, %", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal callbackPercents;

    @PositiveOrZero
    @Schema(description = "Буфер активации, %")
    private BigDecimal activationBufferPercents;
}
