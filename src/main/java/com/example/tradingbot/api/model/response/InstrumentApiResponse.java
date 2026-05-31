package com.example.tradingbot.api.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Представление инструмента в API нашего сервиса. Наружу — internalId
 * инструмента и exchangeInternalId биржи, не id/exchange_id из БД.
 */
@Getter
@Setter
public class InstrumentApiResponse extends AuditableApiResponse {

    @Schema(description = "Межсервисный идентификатор инструмента")
    private String internalId;

    @Schema(description = "Межсервисный идентификатор биржи инструмента")
    private String exchangeInternalId;

    @Schema(description = "Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP")
    private String externalId;

    @Schema(description = "Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION")
    private String externalType;

    @Schema(description = "Нормализованный онбординг-статус инструмента в системе")
    private String status;

    @Schema(description = "Биржевой статус инструмента (сырой, OKX state)")
    private String externalStatus;

    @Schema(description = "Нормализованный режим маржи: ISOLATED / CROSS")
    private String marginMode;

    @Schema(description = "Сырой режим маржи биржи (cross/isolated)")
    private String externalMarginMode;

    @Schema(description = "Рабочее плечо инструмента")
    private Integer leverage;

    @Schema(description = "Биржевое значение плеча (сырое, OKX lever)")
    private String externalLeverage;

    @Schema(description = "Плановая нижняя граница истории свечей (UTC мс)")
    private Long plannedCandleStartDate;
}
