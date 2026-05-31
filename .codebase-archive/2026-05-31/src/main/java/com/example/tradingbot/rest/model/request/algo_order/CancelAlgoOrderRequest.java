package com.example.tradingbot.rest.model.request.algo_order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancelAlgoOrderRequest {

    /**
     * Внутренний идентификатор биржи.
     */
    private String exchangeInternalId;

    /**
     * Внутренний идентификатор инструмента.
     */
    private String instrumentInternalId;

    /**
     * * Внутренний идентификатор алго-ордера.
     */
    private String internalId;

}
