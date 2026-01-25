package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxClosePositionRequest {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("mgnMode")
    private String marginMode;

    @JsonProperty("posSide")
    private String positionSide;

    @JsonProperty("ccy")
    private String marginCurrency;

    @JsonProperty("autoCxl")
    private Boolean autoCancel;
}
