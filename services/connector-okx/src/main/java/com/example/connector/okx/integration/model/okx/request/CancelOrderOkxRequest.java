package com.example.connector.okx.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело OKX cancel-order (POST /trade/cancel-order): instId + один из
 * ordId (предпочтительно) / clOrdId. null-поля не сериализуются. См.
 * docs/models/mapping/Order.md.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CancelOrderOkxRequest {

    /** Инструмент. */
    private String instId;

    /** Биржевой id ордера. */
    private String ordId;

    /** stable client id. */
    private String clOrdId;
}
