package com.example.tradingbot.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на заведение инструмента. Статус (CREATED) и биржевые поля
 * (externalStatus/externalLeverage — при SYNC) проставляются системой.
 */
@Getter
@Setter
public class CreateInstrumentApiRequest {

    @NotBlank
    private String internalId;

    @NotNull
    private Long exchangeId;

    @NotBlank
    private String externalId;

    @NotBlank
    private String externalType;

    /** Режим маржи: ISOLATED / CROSS. */
    @NotBlank
    private String marginMode;

    private String externalMarginMode;

    @NotNull
    private Integer leverage;

    /** Плановый горизонт истории свечей (UTC мс); пусто → из конфига. */
    private Long plannedCandleStartDate;
}
