package com.example.tradingbot.client.okx.dto;

public class OkxCreateOrderRequestAttachAlgo {
    private String attachAlgoClOrdId;
    private String tpTriggerPx;
    private String tpTriggerRatio;
    private String tpOrdPx;
    private String tpOrdKind;
    private String slTriggerPx;
    private String slTriggerRatio;
    private String slOrdPx;
    private String tpTriggerPxType;
    private String slTriggerPxType;
    private String sz;
    private String amendPxOnTriggerType;

    public String getAttachAlgoClOrdId() {
        return attachAlgoClOrdId;
    }

    public void setAttachAlgoClOrdId(String attachAlgoClOrdId) {
        this.attachAlgoClOrdId = attachAlgoClOrdId;
    }

    public String getTpTriggerPx() {
        return tpTriggerPx;
    }

    public void setTpTriggerPx(String tpTriggerPx) {
        this.tpTriggerPx = tpTriggerPx;
    }

    public String getTpTriggerRatio() {
        return tpTriggerRatio;
    }

    public void setTpTriggerRatio(String tpTriggerRatio) {
        this.tpTriggerRatio = tpTriggerRatio;
    }

    public String getTpOrdPx() {
        return tpOrdPx;
    }

    public void setTpOrdPx(String tpOrdPx) {
        this.tpOrdPx = tpOrdPx;
    }

    public String getTpOrdKind() {
        return tpOrdKind;
    }

    public void setTpOrdKind(String tpOrdKind) {
        this.tpOrdKind = tpOrdKind;
    }

    public String getSlTriggerPx() {
        return slTriggerPx;
    }

    public void setSlTriggerPx(String slTriggerPx) {
        this.slTriggerPx = slTriggerPx;
    }

    public String getSlTriggerRatio() {
        return slTriggerRatio;
    }

    public void setSlTriggerRatio(String slTriggerRatio) {
        this.slTriggerRatio = slTriggerRatio;
    }

    public String getSlOrdPx() {
        return slOrdPx;
    }

    public void setSlOrdPx(String slOrdPx) {
        this.slOrdPx = slOrdPx;
    }

    public String getTpTriggerPxType() {
        return tpTriggerPxType;
    }

    public void setTpTriggerPxType(String tpTriggerPxType) {
        this.tpTriggerPxType = tpTriggerPxType;
    }

    public String getSlTriggerPxType() {
        return slTriggerPxType;
    }

    public void setSlTriggerPxType(String slTriggerPxType) {
        this.slTriggerPxType = slTriggerPxType;
    }

    public String getSz() {
        return sz;
    }

    public void setSz(String sz) {
        this.sz = sz;
    }

    public String getAmendPxOnTriggerType() {
        return amendPxOnTriggerType;
    }

    public void setAmendPxOnTriggerType(String amendPxOnTriggerType) {
        this.amendPxOnTriggerType = amendPxOnTriggerType;
    }
}
