package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxTradeFillsArchiveResult {

    @JsonProperty("result")
    private String requestLinkAlreadyExists;

    @JsonProperty("ts")
    private String exchangeTimestamp;

    @JsonProperty("state")
    private String state;

    @JsonProperty("fileHref")
    private String fileHref;
}
