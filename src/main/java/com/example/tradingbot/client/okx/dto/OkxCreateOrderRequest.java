package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCreateOrderRequest {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("tdMode")
    private String tdMode;

    @JsonProperty("ccy")
    private String ccy;

    @JsonProperty("clOrdId")
    private String clOrdId;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("side")
    private String side;

    @JsonProperty("posSide")
    private String posSide;

    @JsonProperty("ordType")
    private String ordType;

    @JsonProperty("sz")
    private String sz;

    @JsonProperty("px")
    private String px;

    @JsonProperty("reduceOnly")
    private Boolean reduceOnly;

    @JsonProperty("tgtCcy")
    private String tgtCcy;

    @JsonProperty("banAmend")
    private Boolean banAmend;

    @JsonProperty("pxAmendType")
    private String pxAmendType;

    @JsonProperty("tradeQuoteCcy")
    private String tradeQuoteCcy;

    @JsonProperty("stpMode")
    private String stpMode;

    @JsonProperty("attachAlgoOrds")
    private List<OkxCreateOrderRequestAttachedAlgo> attachAlgoOrds;
}
