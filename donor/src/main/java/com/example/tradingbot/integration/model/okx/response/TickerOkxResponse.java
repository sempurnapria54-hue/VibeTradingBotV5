package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Нативный DTO тикера OKX (GET /api/v5/market/ticker). Несёт только
 * snapshot-релевантное подмножество полей рыночной цены (last / ask /
 * bid + время); все приходят строками. Не выходит за
 * IntegrationService/adapter
 * (docs/models/integrations/okx/TickerOkxResponse.md).
 */
@Getter
@Setter
public class TickerOkxResponse {

    /** Тип инструмента (instType): SPOT/MARGIN/SWAP/FUTURES/OPTION. */
    private String instType;

    /** Имя инструмента на бирже (instId). */
    private String instId;

    /** Последняя цена сделки (last, decimal-строка). */
    private String last;

    /** Лучшая цена продажи (askPx, decimal-строка). */
    private String askPx;

    /** Лучшая цена покупки (bidPx, decimal-строка). */
    private String bidPx;

    /** Время тикера, epoch millis-строка (ts). */
    private String ts;
}
