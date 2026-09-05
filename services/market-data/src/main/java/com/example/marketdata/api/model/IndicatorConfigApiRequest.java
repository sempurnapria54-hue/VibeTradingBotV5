package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Требование индикатора: что считать.
 *
 * <p>Параметры приходят картой и разбираются в подтип по типу индикатора
 * — тег подтипа в теле не дублируется
 * (docs/rules/persistence-representation.md).
 */
@Getter
@Setter
public class IndicatorConfigApiRequest {

    @NotNull
    @Schema(description = "Тип индикатора")
    private IndicatorValue.Type indicatorType;

    @NotNull
    @Schema(description = "Таймфрейм серии, по которой считается индикатор")
    private TimeFrame timeframe;

    @NotNull
    @Schema(description = "Параметры расчёта по типу индикатора")
    private Map<String, Object> params;
}
