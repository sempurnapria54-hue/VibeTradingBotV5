package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCreateAlgoOrderResult {

    @JsonProperty("algoId")
    private String algoId;

    @JsonProperty("algoClOrdId")
    private String algoClOrdId;

    @JsonProperty("clOrdId")
    private String clOrdId;

    @JsonProperty("sCode")
    private String resultCode;

    @JsonProperty("sMsg")
    private String resultMessage;

    @JsonProperty("tag")
    private String tag;
}
