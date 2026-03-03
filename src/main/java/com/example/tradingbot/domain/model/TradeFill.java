package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFill extends Auditable {

    /** Идентификатор записи в биллинге биржи. */
    private String externalBillId;
    /** Идентификатор сделки. */
    private String externalTradeId;
    /** Идентификатор ордера, породившего исполнение. */
    private String externalOrderId;
    /** Идентификатор инструмента (instId). */
    private String externalInstrumentId;
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
