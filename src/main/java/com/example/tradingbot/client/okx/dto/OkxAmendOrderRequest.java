package com.example.tradingbot.client.okx.dto;

import java.util.List;

public class OkxAmendOrderRequest {
    private String instId;
    private Boolean cxlOnFail;
    private String ordId;
    private String clOrdId;
    private String reqId;
    private String newSz;
    private String newPx;
    private String newPxUsd;
    private String newPxVol;
    private String pxAmendType;
    private List<OkxAmendOrderRequestAttachAlgo> attachAlgoOrds;

    public String getInstId() {
        return instId;
    }

    public void setInstId(String instId) {
        this.instId = instId;
    }

    public Boolean getCxlOnFail() {
        return cxlOnFail;
    }

    public void setCxlOnFail(Boolean cxlOnFail) {
        this.cxlOnFail = cxlOnFail;
    }

    public String getOrdId() {
        return ordId;
    }

    public void setOrdId(String ordId) {
        this.ordId = ordId;
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public void setClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
    }

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    public String getNewSz() {
        return newSz;
    }

    public void setNewSz(String newSz) {
        this.newSz = newSz;
    }

    public String getNewPx() {
        return newPx;
    }

    public void setNewPx(String newPx) {
        this.newPx = newPx;
    }

    public String getNewPxUsd() {
        return newPxUsd;
    }

    public void setNewPxUsd(String newPxUsd) {
        this.newPxUsd = newPxUsd;
    }

    public String getNewPxVol() {
        return newPxVol;
    }

    public void setNewPxVol(String newPxVol) {
        this.newPxVol = newPxVol;
    }

    public String getPxAmendType() {
        return pxAmendType;
    }

    public void setPxAmendType(String pxAmendType) {
        this.pxAmendType = pxAmendType;
    }

    public List<OkxAmendOrderRequestAttachAlgo> getAttachAlgoOrds() {
        return attachAlgoOrds;
    }

    public void setAttachAlgoOrds(List<OkxAmendOrderRequestAttachAlgo> attachAlgoOrds) {
        this.attachAlgoOrds = attachAlgoOrds;
    }
}
