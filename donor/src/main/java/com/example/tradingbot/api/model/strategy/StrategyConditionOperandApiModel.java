package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Операнд условия (API): sourceType + ссылка/значение по источнику.
 * Заполняются только поля своего источника.
 */
@Getter
@Setter
public class StrategyConditionOperandApiModel {

    @NotBlank
    @Schema(description = "Источник значения: PRICE/INDICATOR/MARKET_PHASE/MARKET_STRUCTURE/POSITION/"
            + "ORDER/ALGO_ORDER/BALANCE/TIME/CONSTANT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceType;

    @Schema(description = "Ключ настройки индикатора (только для INDICATOR)")
    private String indicatorKey;

    @Schema(description = "Компонент многокомпонентного индикатора (MACD: MACD_LINE/SIGNAL_LINE/HISTOGRAM; "
            + "Stochastic: STOCH_K/STOCH_D; Bollinger: UPPER_BAND/MIDDLE_BAND/LOWER_BAND/BANDWIDTH/PERCENT_B); "
            + "обязателен для многокомпонентных, не задаётся для одно-компонентных")
    private String indicatorComponent;

    @Schema(description = "Ключ настройки структуры рынка (только для MARKET_STRUCTURE)")
    private String structureKey;

    @Schema(description = "Источник цены: LAST_PRICE/MARK_PRICE/INDEX_PRICE/BEST_BID_PRICE/"
            + "BEST_ASK_PRICE/MID_PRICE (только для PRICE)")
    private String priceSource;

    @Schema(description = "Тип литерала: NUMBER/PERCENT/ENUM/BOOLEAN (только для CONSTANT)")
    private String valueType;

    @Schema(description = "Литерал-значение, интерпретируется по valueType (только для CONSTANT)")
    private String value;
}
