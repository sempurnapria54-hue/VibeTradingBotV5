package com.example.tradingbot.domain.model;

public class OrderAttachAlgo {
    private String attachAlgoId;
    private String attachAlgoClOrdId;
    private String tpOrdKind;
    private String tpTriggerPx;
    private String tpTriggerRatio;
    private String tpTriggerPxType;
    private String tpOrdPx;
    private String slTriggerPx;
    private String slTriggerRatio;
    private String slTriggerPxType;
    private String slOrdPx;
    private String sz;
    private String amendPxOnTriggerType;
    private String failCode;
    private String failReason;

    public String getAttachAlgoId() {
        return attachAlgoId;
    }

    public void setAttachAlgoId(String attachAlgoId) {
        this.attachAlgoId = attachAlgoId;
    }

    public String getAttachAlgoClOrdId() {
        return attachAlgoClOrdId;
    }

    public void setAttachAlgoClOrdId(String attachAlgoClOrdId) {
        this.attachAlgoClOrdId = attachAlgoClOrdId;
    }

    public String getTpOrdKind() {
        return tpOrdKind;
    }

    public void setTpOrdKind(String tpOrdKind) {
        this.tpOrdKind = tpOrdKind;
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

    public String getTpTriggerPxType() {
        return tpTriggerPxType;
    }

    public void setTpTriggerPxType(String tpTriggerPxType) {
        this.tpTriggerPxType = tpTriggerPxType;
    }

    public String getTpOrdPx() {
        return tpOrdPx;
    }

    public void setTpOrdPx(String tpOrdPx) {
        this.tpOrdPx = tpOrdPx;
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

    public String getSlTriggerPxType() {
        return slTriggerPxType;
    }

    public void setSlTriggerPxType(String slTriggerPxType) {
        this.slTriggerPxType = slTriggerPxType;
    }

    public String getSlOrdPx() {
        return slOrdPx;
    }

    public void setSlOrdPx(String slOrdPx) {
        this.slOrdPx = slOrdPx;
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

    public String getFailCode() {
        return failCode;
    }

    public void setFailCode(String failCode) {
        this.failCode = failCode;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
