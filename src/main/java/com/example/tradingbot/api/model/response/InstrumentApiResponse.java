package com.example.tradingbot.api.model.response;

import lombok.Getter;
import lombok.Setter;

/** Представление инструмента в API нашего сервиса. */
@Getter
@Setter
public class InstrumentApiResponse {

    private Long id;

    private String internalId;

    private Long exchangeId;

    private String externalId;

    private String externalType;

    private String status;

    private String externalStatus;

    private String marginMode;

    private String externalMarginMode;

    private Integer leverage;

    private String externalLeverage;

    private Long plannedCandleStartDate;
}
