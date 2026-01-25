package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCancelOrderResult {

    @JsonProperty("ordId")
    private String orderId;

    @JsonProperty("clOrdId")
    private String clientOrderId;

    @JsonProperty("sCode")
    private String resultCode;

    @JsonProperty("sMsg")
    private String resultMessage;
}
