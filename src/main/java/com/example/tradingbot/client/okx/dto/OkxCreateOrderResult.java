package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCreateOrderResult {

    @JsonProperty("ordId")
    private String orderId;

    @JsonProperty("clOrdId")
    private String clientOrderId;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("ts")
    private String exchangeProcessedAt;

    @JsonProperty("sCode")
    private String resultCode;

    @JsonProperty("sMsg")
    private String resultMessage;
}
