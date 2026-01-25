package com.example.tradingbot.client.okx.dto;

import java.util.List;

public class OkxCreateOrderRequest {
    private String instId;
    private String tdMode;
    private String side;
    private String ordType;
    private String sz;
    private String px;
    private String posSide;
    private Boolean reduceOnly;
    private String clOrdId;
    private String tag;
    private String ccy;
    private String stpMode;
    private List<OkxCreateOrderRequestAttachAlgo> attachAlgoOrds;

    public String getInstId() {
        return instId;
    }

    public void setInstId(String instId) {
        this.instId = instId;
    }

    public String getTdMode() {
        return tdMode;
    }

    public void setTdMode(String tdMode) {
        this.tdMode = tdMode;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getOrdType() {
        return ordType;
    }

    public void setOrdType(String ordType) {
        this.ordType = ordType;
    }

    public String getSz() {
        return sz;
    }

    public void setSz(String sz) {
        this.sz = sz;
    }

    public String getPx() {
        return px;
    }

    public void setPx(String px) {
        this.px = px;
    }

    public String getPosSide() {
        return posSide;
    }

    public void setPosSide(String posSide) {
        this.posSide = posSide;
    }

    public Boolean getReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(Boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public void setClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getCcy() {
        return ccy;
    }

    public void setCcy(String ccy) {
        this.ccy = ccy;
    }

    public String getStpMode() {
        return stpMode;
    }

    public void setStpMode(String stpMode) {
        this.stpMode = stpMode;
    }

    public List<OkxCreateOrderRequestAttachAlgo> getAttachAlgoOrds() {
        return attachAlgoOrds;
    }

    public void setAttachAlgoOrds(List<OkxCreateOrderRequestAttachAlgo> attachAlgoOrds) {
        this.attachAlgoOrds = attachAlgoOrds;
    }
}
