package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxAmendOrderRequest {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("cxlOnFail")
    private Boolean cancelOnFail;

    @JsonProperty("ordId")
    private String ordId;

    @JsonProperty("clOrdId")
    private String clOrdId;

    @JsonProperty("reqId")
    private String reqId;

    @JsonProperty("newSz")
    private String newSz;

    @JsonProperty("newPx")
    private String newPx;

    @JsonProperty("newPxUsd")
    private String newPxUsd;

    @JsonProperty("newPxVol")
    private String newPxVol;

    @JsonProperty("pxAmendType")
    private String pxAmendType;

    @JsonProperty("attachAlgoOrds")
    private List<OkxAmendOrderRequestAttachedAlgo> attachAlgoOrds;
}
