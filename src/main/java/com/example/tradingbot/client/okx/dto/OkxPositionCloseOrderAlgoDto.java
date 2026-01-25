package com.example.tradingbot.client.okx.dto;

public class OkxPositionCloseOrderAlgoDto {
    private String algoId;
    private String slTriggerPx;
    private String slTriggerPxType;
    private String tpTriggerPx;
    private String tpTriggerPxType;
    private String closeFraction;

    public String getAlgoId() {
        return algoId;
    }

    public void setAlgoId(String algoId) {
        this.algoId = algoId;
    }

    public String getSlTriggerPx() {
        return slTriggerPx;
    }

    public void setSlTriggerPx(String slTriggerPx) {
        this.slTriggerPx = slTriggerPx;
    }

    public String getSlTriggerPxType() {
        return slTriggerPxType;
    }

    public void setSlTriggerPxType(String slTriggerPxType) {
        this.slTriggerPxType = slTriggerPxType;
    }

    public String getTpTriggerPx() {
        return tpTriggerPx;
    }

    public void setTpTriggerPx(String tpTriggerPx) {
        this.tpTriggerPx = tpTriggerPx;
    }

    public String getTpTriggerPxType() {
        return tpTriggerPxType;
    }

    public void setTpTriggerPxType(String tpTriggerPxType) {
        this.tpTriggerPxType = tpTriggerPxType;
    }

    public String getCloseFraction() {
        return closeFraction;
    }

    public void setCloseFraction(String closeFraction) {
        this.closeFraction = closeFraction;
    }
}
