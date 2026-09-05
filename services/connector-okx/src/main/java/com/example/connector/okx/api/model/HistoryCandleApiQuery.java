package com.example.connector.okx.api.model;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Окно исторических свечей инструмента. */
@Getter
@Setter
public class HistoryCandleApiQuery {

    @Schema(description = "Идентификатор инструмента на площадке")
    private String externalInstrumentId;

    @Schema(description = "Доменный таймфрейм серии; в словарь площадки его переводит коннектор")
    private TimeFrame timeframe;

    @Schema(description = "Верхняя граница окна, миллисекунды эпохи: свечи берутся назад от неё")
    private Long afterMillis;

    @Schema(description = "Предел числа свечей; безлимитного чтения истории нет")
    private Integer limit;
}
