package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCancelAlgoOrderRequest {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("algoId")
    private String algoId;

    @JsonProperty("algoClOrdId")
    private String algoClOrdId;
}
