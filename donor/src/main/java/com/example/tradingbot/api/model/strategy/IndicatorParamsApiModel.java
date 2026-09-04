package com.example.tradingbot.api.model.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * База параметров индикатора в API. Подтип определяется полем
 * indicatorType настройки-владельца (Jackson EXTERNAL_PROPERTY, в
 * объекте params не дублируется). Используется в запросе и ответе.
 */
@Getter
@Setter
@JsonSubTypes({
        @JsonSubTypes.Type(value = AtrParamsApiModel.class, name = "ATR"),
        @JsonSubTypes.Type(value = EmaParamsApiModel.class, name = "EMA"),
        @JsonSubTypes.Type(value = RsiParamsApiModel.class, name = "RSI"),
        @JsonSubTypes.Type(value = MacdParamsApiModel.class, name = "MACD"),
        @JsonSubTypes.Type(value = ObvParamsApiModel.class, name = "OBV"),
        @JsonSubTypes.Type(value = StochasticParamsApiModel.class, name = "STOCHASTIC"),
        @JsonSubTypes.Type(value = BollingerBandsParamsApiModel.class, name = "BOLLINGER_BANDS"),
        @JsonSubTypes.Type(value = EfficiencyRatioParamsApiModel.class, name = "EFFICIENCY_RATIO")
})
public abstract class IndicatorParamsApiModel {

    @NotBlank
    @Schema(description = "Доменный таймфрейм серии индикатора (имя TimeFrame, например FIVE_MINUTES)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String timeframe;

    @Positive
    @Schema(description = "Явный override warmup-глубины в барах; пусто — выводится из типа и периода")
    private Integer warmup;
}
