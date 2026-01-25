package com.example.tradingbot.client.okx.dto;

public class OkxClosePositionRequest {
    private String instId;
    private String mgnMode;
    private String posSide;
    private String ccy;
    private Boolean autoCxl;

    public String getInstId() {
        return instId;
    }

    public void setInstId(String instId) {
        this.instId = instId;
    }

    public String getMgnMode() {
        return mgnMode;
    }

    public void setMgnMode(String mgnMode) {
        this.mgnMode = mgnMode;
    }

    public String getPosSide() {
        return posSide;
    }

    public void setPosSide(String posSide) {
        this.posSide = posSide;
    }

    public String getCcy() {
        return ccy;
    }

    public void setCcy(String ccy) {
        this.ccy = ccy;
    }

    public Boolean getAutoCxl() {
        return autoCxl;
    }

    public void setAutoCxl(Boolean autoCxl) {
        this.autoCxl = autoCxl;
    }
}
