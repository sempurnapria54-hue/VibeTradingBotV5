package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxClosePositionResult {

    @JsonProperty("instId")
    private String exchangeInstrumentName;

    @JsonProperty("posSide")
    private String positionSide;
}
