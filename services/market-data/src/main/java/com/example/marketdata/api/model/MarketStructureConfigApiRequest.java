package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Требование структуры рынка: что считать и на каких входах.
 *
 * <p>Идентичности входов входят в идентичность результата: два расчёта с
 * разными ER/ATR — разные строки
 * (docs/models/domain/other/MarketStructure.md).
 */
@Getter
@Setter
public class MarketStructureConfigApiRequest {

    @NotNull
    @Schema(description = "Таймфрейм серии, по которой считается структура")
    private TimeFrame timeframe;

    @NotNull
    @Schema(description = "Параметры расчёта окна и уровней")
    private Map<String, Object> params;

    @Schema(description = "Идентичность входного ER; пусто — вход не объявлен")
    private String efficiencyRatioConfigInternalId;

    @Schema(description = "Идентичность входного ATR; пусто — вход не объявлен")
    private String atrConfigInternalId;
}
