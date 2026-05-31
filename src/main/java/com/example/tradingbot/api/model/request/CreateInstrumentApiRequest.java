package com.example.tradingbot.api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на заведение инструмента. Статус (CREATED) и биржевые поля
 * (externalStatus/externalLeverage — при SYNC) проставляются системой.
 * Биржа задаётся межсервисным идентификатором (exchangeInternalId).
 */
@Getter
@Setter
public class CreateInstrumentApiRequest {

    @NotBlank
    @Schema(description = "Межсервисный идентификатор инструмента", requiredMode = Schema.RequiredMode.REQUIRED)
    private String internalId;

    @NotBlank
    @Schema(description = "Межсервисный идентификатор биржи инструмента", requiredMode = Schema.RequiredMode.REQUIRED)
    private String exchangeInternalId;

    @NotBlank
    @Schema(description = "Имя инструмента на бирже (OKX instId)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String externalId;

    @NotBlank
    @Schema(description = "Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String externalType;

    @NotBlank
    @Schema(description = "Режим маржи: ISOLATED / CROSS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String marginMode;

    @Schema(description = "Сырой режим маржи биржи (cross/isolated)")
    private String externalMarginMode;

    @NotNull
    @Schema(description = "Рабочее плечо инструмента", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer leverage;

    @Schema(description = "Плановый горизонт истории свечей (UTC мс); пусто → из конфига")
    private Long plannedCandleStartDate;
}
