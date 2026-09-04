package com.example.tradingbot.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Элемент тела OKX cancel-algos / cancel-advance-algos (POST, массив):
 * instId + algoId (предпочтительно) / algoClOrdId. null-поля не
 * сериализуются. См. docs/models/mapping/AlgoOrder.md.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CancelAlgoOrderOkxRequest {

    /** Инструмент. */
    private String instId;

    /** Биржевой algo id. */
    private String algoId;

    /** stable client algo id. */
    private String algoClOrdId;
}
