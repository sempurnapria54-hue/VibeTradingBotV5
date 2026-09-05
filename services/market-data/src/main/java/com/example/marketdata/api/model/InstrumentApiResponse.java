package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Инструмент каталога наружу.
 *
 * <p>Числового идентификатора здесь нет: наружу сущность адресуется
 * {@code internalId} (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@Getter
@Setter
public class InstrumentApiResponse {

    @Schema(description = "Межсервисный идентификатор инструмента")
    private String internalId;

    @Schema(description = "Код площадки: OKX, BYBIT")
    private String exchangeCode;

    @Schema(description = "Имя инструмента на площадке")
    private String externalId;

    @Schema(description = "Тип инструмента на площадке (сырой)")
    private String externalType;

    @Schema(description = "Статус готовности данных инструмента в системе")
    private String status;

    @Schema(description = "Статус инструмента на площадке (сырой)")
    private String externalStatus;

    @Schema(description = "Расчётная валюта инструмента")
    private String externalSettlementCurrency;

    @Schema(description = "Базовая валюта инструмента")
    private String externalBaseCurrency;

    @Schema(description = "Котировочная валюта инструмента")
    private String externalQuoteCurrency;
}
