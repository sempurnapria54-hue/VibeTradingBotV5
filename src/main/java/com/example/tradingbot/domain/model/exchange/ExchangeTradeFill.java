package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeTradeFill {

    /** Идентификатор записи в биллинге биржи. */
    private String billId;
    /** Идентификатор сделки. */
    private String tradeId;
    /** Идентификатор ордера, породившего исполнение. */
    private String orderId;
    /** Идентификатор инструмента (instId). */
    private String instrumentId;
    /** Сторона исполнения: buy/sell. */
    private String side;
    /** Размер исполненной части сделки. */
    private String fillSize;
    /** Цена исполнения сделки. */
    private String fillPrice;
    /** PnL, связанный с исполнением. */
    private String fillPnl;
    /** Время исполнения сделки в миллисекундах UTC. */
    private String timestamp;
}
