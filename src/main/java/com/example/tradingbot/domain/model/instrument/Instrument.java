package com.example.tradingbot.domain.model.instrument;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.candle.CandleGroup;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    private String externalType;

    /**
     * Статус: CREATED/HOLD/SYNC/CANDLES_LOADING/ACTIVE.
     */
    private Status status;

    /**
     * Режим маржи (cross/isolated).
     */
    private MarginMode marginMode;

    /**
     * Режим маржи на бирже (cross/isolated).
     */
    private String externalMarginMode;

    /**
     * Плечо.
     */
    private Integer leverage;

    /**
     * Набор групп свечей для разных таймфреймов инструмента.
     */
    private List<CandleGroup> candleGroups;

    public enum MarginMode {
        ISOLATED,
        CROSS
    }

    public enum Status {
        CREATED,
        HOLD,
        SYNC,
        CANDLES_LOADING,
        ACTIVE,
        CLOSED,
        ERROR
    }
}
