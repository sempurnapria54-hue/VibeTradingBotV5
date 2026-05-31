package com.example.tradingbot.api.model.response;

import lombok.Getter;
import lombok.Setter;

/** Представление группы свечей в API нашего сервиса. */
@Getter
@Setter
public class CandleGroupApiResponse {

    private Long id;

    private Long instrumentId;

    private String timeframe;

    private String externalTimeframe;

    private String status;

    private Long actualFirstUtcMillis;

    private Long actualLastUtcMillis;

    private Long count;
}
