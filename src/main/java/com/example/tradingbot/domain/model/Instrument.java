package com.example.tradingbot.domain.model;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Instrument extends Auditable {

    /**
     * Внутренний идентификатор инструмента.
     */
    private Long id;

    /**
     * Межсервисный идентификатор инструмента.
     */
    private String internalId;

    /**
     * Внутренний идентификатор биржи.
     */
    private Long exchangeId;

    /**
     * Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP.
     */
    private String externalId;

    /**
     * Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION.
     */
    private String type;

    /**
     * Статус: CREATED/HOLD/SYNC/CANDLES_LOADING/ACTIVE.
     */
    private String status;

    /**
     * Режим маржи (cross/isolated).
     */
    private MarginMode marginMode;

    /**
     * Плечо.
     */
    private Integer leverage;

    public enum MarginMode {
        ISOLATED,
        CROSS
    }
}
