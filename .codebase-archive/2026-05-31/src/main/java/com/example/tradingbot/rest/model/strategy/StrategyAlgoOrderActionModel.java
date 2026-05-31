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
@JsonTypeName("ALGO_ORDER")
@Schema(name = "StrategyAlgoOrderActionModel", description = "Standalone algo-order действие стратегии.")
public class StrategyAlgoOrderActionModel extends StrategyActionModel {

    @NotBlank(message = "Strategy algo actionType is required")
    @Schema(description = "Операция над algo-order. Для strategy API поддерживаются CREATE, AMEND и CANCEL.", example = "CREATE")
    private String actionType;

    @NotBlank(message = "Strategy algo conditionType is required")
    @Schema(description = "Тип algo-condition на уровне доменной модели.", example = "OCO_FULL")
    private String conditionType;

    @NotNull(message = "Strategy algo action level is required")
    @Schema(description = "Локальный уровень действия внутри step.", example = "1")
    private Integer level;

    @Valid
    @Schema(description = "Настройки stop-loss, если они участвуют в данном algo-action.")
    private StopLossSettingsModel stopLossSettings;

    @Valid
    @Schema(description = "Настройки trailing, если действие описывает trailing-защиту.")
    private TrailingSettingsModel trailingSettings;

    @Schema(description = "Доля позиции в процентах, которую закрывает действие.", example = "100")
    private BigDecimal closeFractionPercents;

    @Schema(description = "Порог профита в процентах, при котором срабатывает TP-компонент.", example = "3.0")
    private BigDecimal triggerProfitPercents;

    @Schema(description = "Тип рыночной цены для trigger-based TP-компонента.", example = "MARK")
    private String triggerPriceType;
}
