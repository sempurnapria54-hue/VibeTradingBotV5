package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCancelOrderRequest {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("ordId")
    private String ordId;

    @JsonProperty("clOrdId")
    private String clOrdId;
}
