package com.example.tradingbot.domain.model;

import java.util.List;

public class Order {
    private String instType;
    private String instId;
    private String tdMode;
    private String ccy;
    private String ordId;
    private String clOrdId;
    private String tag;
    private String side;
    private String posSide;
    private String ordType;
    private String px;
    private String sz;
    private String reduceOnly;
    private String state;
    private String accFillSz;
    private String avgPx;
    private String fillPx;
    private String fillSz;
    private String fillTime;
    private String tradeId;
    private String pnl;
    private String fee;
    private String feeCcy;
    private String rebate;
    private String rebateCcy;
    private String attachAlgoClOrdId;
    private String tpTriggerPx;
    private String tpTriggerPxType;
    private String tpOrdPx;
    private String slTriggerPx;
    private String slTriggerPxType;
    private String slOrdPx;
    private List<OrderAttachAlgo> attachAlgoOrds;
    private String algoClOrdId;
    private String algoId;
    private OrderLinkedAlgo linkedAlgoOrd;
    private String source;
    private String category;
    private String isTpLimit;
    private String cancelSource;
    private String cancelSourceReason;
    private String quickMgnType;
    private String lever;
    private String stpMode;
    private String uTime;
    private String cTime;
    private String tgtCcy;
    private String tradeQuoteCcy;
    private String pxUsd;
    private String pxVol;
    private String pxType;
    private String stpId;
    private String ordTypeName;
    private String fillTimeMs;
    private String fillPxAvg;
    private String fillSzLast;
    private String fillTimeLast;
    private String pxTypeCode;
    private String lastFillPx;
    private String lastFillSz;

    public String getInstType() {
        return instType;
    }

    public void setInstType(String instType) {
        this.instType = instType;
    }

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

    public String getCcy() {
        return ccy;
    }

    public void setCcy(String ccy) {
        this.ccy = ccy;
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

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getPosSide() {
        return posSide;
    }

    public void setPosSide(String posSide) {
        this.posSide = posSide;
    }

    public String getOrdType() {
        return ordType;
    }

    public void setOrdType(String ordType) {
        this.ordType = ordType;
    }

    public String getPx() {
        return px;
    }

    public void setPx(String px) {
        this.px = px;
    }

    public String getSz() {
        return sz;
    }

    public void setSz(String sz) {
        this.sz = sz;
    }

    public String getReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(String reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAccFillSz() {
        return accFillSz;
    }

    public void setAccFillSz(String accFillSz) {
        this.accFillSz = accFillSz;
    }

    public String getAvgPx() {
        return avgPx;
    }

    public void setAvgPx(String avgPx) {
        this.avgPx = avgPx;
    }

    public String getFillPx() {
        return fillPx;
    }

    public void setFillPx(String fillPx) {
        this.fillPx = fillPx;
    }

    public String getFillSz() {
        return fillSz;
    }

    public void setFillSz(String fillSz) {
        this.fillSz = fillSz;
    }

    public String getFillTime() {
        return fillTime;
    }

    public void setFillTime(String fillTime) {
        this.fillTime = fillTime;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getPnl() {
        return pnl;
    }

    public void setPnl(String pnl) {
        this.pnl = pnl;
    }

    public String getFee() {
        return fee;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public String getFeeCcy() {
        return feeCcy;
    }

    public void setFeeCcy(String feeCcy) {
        this.feeCcy = feeCcy;
    }

    public String getRebate() {
        return rebate;
    }

    public void setRebate(String rebate) {
        this.rebate = rebate;
    }

    public String getRebateCcy() {
        return rebateCcy;
    }

    public void setRebateCcy(String rebateCcy) {
        this.rebateCcy = rebateCcy;
    }

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

    public List<OrderAttachAlgo> getAttachAlgoOrds() {
        return attachAlgoOrds;
    }

    public void setAttachAlgoOrds(List<OrderAttachAlgo> attachAlgoOrds) {
        this.attachAlgoOrds = attachAlgoOrds;
    }

    public String getAlgoClOrdId() {
        return algoClOrdId;
    }

    public void setAlgoClOrdId(String algoClOrdId) {
        this.algoClOrdId = algoClOrdId;
    }

    public String getAlgoId() {
        return algoId;
    }

    public void setAlgoId(String algoId) {
        this.algoId = algoId;
    }

    public OrderLinkedAlgo getLinkedAlgoOrd() {
        return linkedAlgoOrd;
    }

    public void setLinkedAlgoOrd(OrderLinkedAlgo linkedAlgoOrd) {
        this.linkedAlgoOrd = linkedAlgoOrd;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIsTpLimit() {
        return isTpLimit;
    }

    public void setIsTpLimit(String isTpLimit) {
        this.isTpLimit = isTpLimit;
    }

    public String getCancelSource() {
        return cancelSource;
    }

    public void setCancelSource(String cancelSource) {
        this.cancelSource = cancelSource;
    }

    public String getCancelSourceReason() {
        return cancelSourceReason;
    }

    public void setCancelSourceReason(String cancelSourceReason) {
        this.cancelSourceReason = cancelSourceReason;
    }

    public String getQuickMgnType() {
        return quickMgnType;
    }

    public void setQuickMgnType(String quickMgnType) {
        this.quickMgnType = quickMgnType;
    }

    public String getLever() {
        return lever;
    }

    public void setLever(String lever) {
        this.lever = lever;
    }

    public String getStpMode() {
        return stpMode;
    }

    public void setStpMode(String stpMode) {
        this.stpMode = stpMode;
    }

    public String getUTime() {
        return uTime;
    }

    public void setUTime(String uTime) {
        this.uTime = uTime;
    }

    public String getCTime() {
        return cTime;
    }

    public void setCTime(String cTime) {
        this.cTime = cTime;
    }

    public String getTgtCcy() {
        return tgtCcy;
    }

    public void setTgtCcy(String tgtCcy) {
        this.tgtCcy = tgtCcy;
    }

    public String getTradeQuoteCcy() {
        return tradeQuoteCcy;
    }

    public void setTradeQuoteCcy(String tradeQuoteCcy) {
        this.tradeQuoteCcy = tradeQuoteCcy;
    }

    public String getPxUsd() {
        return pxUsd;
    }

    public void setPxUsd(String pxUsd) {
        this.pxUsd = pxUsd;
    }

    public String getPxVol() {
        return pxVol;
    }

    public void setPxVol(String pxVol) {
        this.pxVol = pxVol;
    }

    public String getPxType() {
        return pxType;
    }

    public void setPxType(String pxType) {
        this.pxType = pxType;
    }

    public String getStpId() {
        return stpId;
    }

    public void setStpId(String stpId) {
        this.stpId = stpId;
    }

    public String getOrdTypeName() {
        return ordTypeName;
    }

    public void setOrdTypeName(String ordTypeName) {
        this.ordTypeName = ordTypeName;
    }

    public String getFillTimeMs() {
        return fillTimeMs;
    }

    public void setFillTimeMs(String fillTimeMs) {
        this.fillTimeMs = fillTimeMs;
    }

    public String getFillPxAvg() {
        return fillPxAvg;
    }

    public void setFillPxAvg(String fillPxAvg) {
        this.fillPxAvg = fillPxAvg;
    }

    public String getFillSzLast() {
        return fillSzLast;
    }

    public void setFillSzLast(String fillSzLast) {
        this.fillSzLast = fillSzLast;
    }

    public String getFillTimeLast() {
        return fillTimeLast;
    }

    public void setFillTimeLast(String fillTimeLast) {
        this.fillTimeLast = fillTimeLast;
    }

    public String getPxTypeCode() {
        return pxTypeCode;
    }

    public void setPxTypeCode(String pxTypeCode) {
        this.pxTypeCode = pxTypeCode;
    }

    public String getLastFillPx() {
        return lastFillPx;
    }

    public void setLastFillPx(String lastFillPx) {
        this.lastFillPx = lastFillPx;
    }

    public String getLastFillSz() {
        return lastFillSz;
    }

    public void setLastFillSz(String lastFillSz) {
        this.lastFillSz = lastFillSz;
    }
}
