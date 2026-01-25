package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxLinkedAlgoOrder {

    @JsonProperty("algoId")
    private String algoId;
}
