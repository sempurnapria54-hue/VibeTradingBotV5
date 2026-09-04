package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Настройки расчёта stop-loss (API). Дистанция не ограничена сотней:
 * для ATR_PERCENT кодировка 150 = 1.5 ATR легальна.
 */
@Getter
@Setter
public class StopLossSettingsApiModel {

    @NotBlank
    @Schema(description = "Способ расчёта: ENTRY_PRICE_PERCENT/ATR_PERCENT/MARKET_STRUCTURE_BUFFER_PERCENT",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String calculationType;

    @Positive
    @Schema(description = "Дистанция, % по способу расчёта (ATR_PERCENT: 150 = 1.5 ATR). "
            + "Не объявляется при BREAKEVEN — уровень безубытка дистанции не имеет; "
            + "обязательность по способу расчёта проверяет валидатор, а не аннотация")
    private BigDecimal distancePercents;

    @NotBlank
    @Schema(description = "Тип trigger-цены биржи: LAST/INDEX/MARK", requiredMode = Schema.RequiredMode.REQUIRED)
    private String triggerPriceType;

    @Schema(description = "Ключ настройки ATR-индикатора (только для ATR_PERCENT)")
    private String indicatorKey;

    @Schema(description = "Ключ настройки структуры рынка (только для MARKET_STRUCTURE_BUFFER_PERCENT)")
    private String structureKey;
}
