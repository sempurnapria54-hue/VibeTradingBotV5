package com.example.tradingbot.rest.model.request.candle_group;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateCandleGroupRequest {

    /**
     * Внутренний идентификатор биржи.
     */
    private String exchangeInternalId;
    /**
     * Внутренний идентификатор инструмента.
     */
    private String instrumentInternalId;
    /**
     * Таймфрейм.
     */
    private String timeframe;
}
