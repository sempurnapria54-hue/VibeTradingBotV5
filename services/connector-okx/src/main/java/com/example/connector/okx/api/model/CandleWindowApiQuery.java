package com.example.connector.okx.api.model;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Окно последних свечей инструмента. */
@Getter
@Setter
public class CandleWindowApiQuery {

    @Schema(description = "Идентификатор инструмента на площадке")
    private String externalInstrumentId;

    @Schema(description = "Доменный таймфрейм серии; в словарь площадки его переводит коннектор")
    private TimeFrame timeframe;

    @Schema(description = "Сколько последних свечей вернуть; окно ограничено — безлимитного чтения нет")
    private Integer limit;
}
