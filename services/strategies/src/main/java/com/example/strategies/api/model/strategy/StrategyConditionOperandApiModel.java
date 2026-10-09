package com.example.strategies.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Операнд условия (API): sourceType + ссылка/значение по источнику. Набор
 * полей задан источником точно: поле чужого источника отвергает создание
 * (docs/rules/strategy-condition-contract.md §«Грамматика объявляет только
 * исполняемое»).
 *
 * <p><b>Ключ {@code priceSource} форма узнаёт только ради отказа:</b>
 * ценовой операнд — последняя цена сделки, источника цены у него нет, и
 * объявленный он отвергается валидатором при любом значении
 * (.claude/decisions/condition-grammar-executable-only.md §Цена).
 */
@Getter
@Setter
public class StrategyConditionOperandApiModel {

    @NotBlank
    @Schema(description = "Источник значения: PRICE (последняя цена сделки, полей нет)/INDICATOR/"
            + "MARKET_PHASE (полей нет)/MARKET_STRUCTURE/CONSTANT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceType;

    @Schema(description = "Ключ настройки индикатора (только для INDICATOR)")
    private String indicatorKey;

    @Schema(description = "Компонент многокомпонентного индикатора (MACD: MACD_LINE/SIGNAL_LINE/HISTOGRAM; "
            + "Stochastic: STOCH_K/STOCH_D; Bollinger: UPPER_BAND/MIDDLE_BAND/LOWER_BAND/BANDWIDTH/PERCENT_B); "
            + "обязателен для многокомпонентных, не задаётся для одно-компонентных")
    private String indicatorComponent;

    @Schema(description = "Ключ настройки структуры рынка (только для MARKET_STRUCTURE)")
    private String structureKey;

    @Schema(description = "Не объявляется: источника цены у операнда нет, и объявленный он отвергается "
            + "(STRATEGY_CONDITION_FIELD_NOT_READ); источник рыночной цены объявляет только размещение")
    private String priceSource;

    @Schema(description = "Тип литерала: NUMBER/PERCENT/ENUM (только для CONSTANT)")
    private String valueType;

    @Schema(description = "Литерал-значение, интерпретируется по valueType (только для CONSTANT)")
    private String value;
}
