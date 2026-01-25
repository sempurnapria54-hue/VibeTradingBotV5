package com.example.tradingbot.rest.model;

import java.util.List;

public class PositionRest {
    private String instType;
    private String instId;
    private String mgnMode;
    private String posId;
    private String posSide;
    private String pos;
    private String availPos;
    private String hedgedPos;
    private String avgPx;
    private String nonSettleAvgPx;
    private String markPx;
    private String last;
    private String idxPx;
    private String usdPx;
    private String bePx;
    private String upl;
    private String uplRatio;
    private String uplLastPx;
    private String uplRatioLastPx;
    private String lever;
    private String liqPx;
    private String imr;
    private String mmr;
    private String mgnRatio;
    private String margin;
    private String notionalUsd;
    private String realizedPnl;
    private String settledPnl;
    private String pnl;
    private String fee;
    private String fundingFee;
    private String liqPenalty;
    private String ccy;
    private String interest;
    private String liab;
    private String liabCcy;
    private String pendingCloseOrdLiabVal;
    private String adl;
    private String tradeId;
    private String cTime;
    private String uTime;
    private List<PositionCloseOrderAlgoRest> closeOrderAlgo;
    private String spotInUseAmt;
    private String spotInUseCcy;
    private String clSpotInUseAmt;
    private String maxSpotInUseAmt;
    private String optVal;
    private String deltaBS;
    private String deltaPA;
    private String gammaBS;
    private String gammaPA;
    private String thetaBS;
    private String thetaPA;
    private String vegaBS;
    private String vegaPA;
    private String baseBal;
    private String quoteBal;
    private String baseBorrowed;
    private String quoteBorrowed;
    private String baseInterest;
    private String quoteInterest;
    private String posCcy;
    private String bizRefId;
    private String bizRefType;

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

    public String getMgnMode() {
        return mgnMode;
    }

    public void setMgnMode(String mgnMode) {
        this.mgnMode = mgnMode;
    }

    public String getPosId() {
        return posId;
    }

    public void setPosId(String posId) {
        this.posId = posId;
    }

    public String getPosSide() {
        return posSide;
    }

    public void setPosSide(String posSide) {
        this.posSide = posSide;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public String getAvailPos() {
        return availPos;
    }

    public void setAvailPos(String availPos) {
        this.availPos = availPos;
    }

    public String getHedgedPos() {
        return hedgedPos;
    }

    public void setHedgedPos(String hedgedPos) {
        this.hedgedPos = hedgedPos;
    }

    public String getAvgPx() {
        return avgPx;
    }

    public void setAvgPx(String avgPx) {
        this.avgPx = avgPx;
    }

    public String getNonSettleAvgPx() {
        return nonSettleAvgPx;
    }

    public void setNonSettleAvgPx(String nonSettleAvgPx) {
        this.nonSettleAvgPx = nonSettleAvgPx;
    }

    public String getMarkPx() {
        return markPx;
    }

    public void setMarkPx(String markPx) {
        this.markPx = markPx;
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getIdxPx() {
        return idxPx;
    }

    public void setIdxPx(String idxPx) {
        this.idxPx = idxPx;
    }

    public String getUsdPx() {
        return usdPx;
    }

    public void setUsdPx(String usdPx) {
        this.usdPx = usdPx;
    }

    public String getBePx() {
        return bePx;
    }

    public void setBePx(String bePx) {
        this.bePx = bePx;
    }

    public String getUpl() {
        return upl;
    }

    public void setUpl(String upl) {
        this.upl = upl;
    }

    public String getUplRatio() {
        return uplRatio;
    }

    public void setUplRatio(String uplRatio) {
        this.uplRatio = uplRatio;
    }

    public String getUplLastPx() {
        return uplLastPx;
    }

    public void setUplLastPx(String uplLastPx) {
        this.uplLastPx = uplLastPx;
    }

    public String getUplRatioLastPx() {
        return uplRatioLastPx;
    }

    public void setUplRatioLastPx(String uplRatioLastPx) {
        this.uplRatioLastPx = uplRatioLastPx;
    }

    public String getLever() {
        return lever;
    }

    public void setLever(String lever) {
        this.lever = lever;
    }

    public String getLiqPx() {
        return liqPx;
    }

    public void setLiqPx(String liqPx) {
        this.liqPx = liqPx;
    }

    public String getImr() {
        return imr;
    }

    public void setImr(String imr) {
        this.imr = imr;
    }

    public String getMmr() {
        return mmr;
    }

    public void setMmr(String mmr) {
        this.mmr = mmr;
    }

    public String getMgnRatio() {
        return mgnRatio;
    }

    public void setMgnRatio(String mgnRatio) {
        this.mgnRatio = mgnRatio;
    }

    public String getMargin() {
        return margin;
    }

    public void setMargin(String margin) {
        this.margin = margin;
    }

    public String getNotionalUsd() {
        return notionalUsd;
    }

    public void setNotionalUsd(String notionalUsd) {
        this.notionalUsd = notionalUsd;
    }

    public String getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(String realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public String getSettledPnl() {
        return settledPnl;
    }

    public void setSettledPnl(String settledPnl) {
        this.settledPnl = settledPnl;
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

    public String getFundingFee() {
        return fundingFee;
    }

    public void setFundingFee(String fundingFee) {
        this.fundingFee = fundingFee;
    }

    public String getLiqPenalty() {
        return liqPenalty;
    }

    public void setLiqPenalty(String liqPenalty) {
        this.liqPenalty = liqPenalty;
    }

    public String getCcy() {
        return ccy;
    }

    public void setCcy(String ccy) {
        this.ccy = ccy;
    }

    public String getInterest() {
        return interest;
    }

    public void setInterest(String interest) {
        this.interest = interest;
    }

    public String getLiab() {
        return liab;
    }

    public void setLiab(String liab) {
        this.liab = liab;
    }

    public String getLiabCcy() {
        return liabCcy;
    }

    public void setLiabCcy(String liabCcy) {
        this.liabCcy = liabCcy;
    }

    public String getPendingCloseOrdLiabVal() {
        return pendingCloseOrdLiabVal;
    }

    public void setPendingCloseOrdLiabVal(String pendingCloseOrdLiabVal) {
        this.pendingCloseOrdLiabVal = pendingCloseOrdLiabVal;
    }

    public String getAdl() {
        return adl;
    }

    public void setAdl(String adl) {
        this.adl = adl;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getCTime() {
        return cTime;
    }

    public void setCTime(String cTime) {
        this.cTime = cTime;
    }

    public String getUTime() {
        return uTime;
    }

    public void setUTime(String uTime) {
        this.uTime = uTime;
    }

    public List<PositionCloseOrderAlgoRest> getCloseOrderAlgo() {
        return closeOrderAlgo;
    }

    public void setCloseOrderAlgo(List<PositionCloseOrderAlgoRest> closeOrderAlgo) {
        this.closeOrderAlgo = closeOrderAlgo;
    }

    public String getSpotInUseAmt() {
        return spotInUseAmt;
    }

    public void setSpotInUseAmt(String spotInUseAmt) {
        this.spotInUseAmt = spotInUseAmt;
    }

    public String getSpotInUseCcy() {
        return spotInUseCcy;
    }

    public void setSpotInUseCcy(String spotInUseCcy) {
        this.spotInUseCcy = spotInUseCcy;
    }

    public String getClSpotInUseAmt() {
        return clSpotInUseAmt;
    }

    public void setClSpotInUseAmt(String clSpotInUseAmt) {
        this.clSpotInUseAmt = clSpotInUseAmt;
    }

    public String getMaxSpotInUseAmt() {
        return maxSpotInUseAmt;
    }

    public void setMaxSpotInUseAmt(String maxSpotInUseAmt) {
        this.maxSpotInUseAmt = maxSpotInUseAmt;
    }

    public String getOptVal() {
        return optVal;
    }

    public void setOptVal(String optVal) {
        this.optVal = optVal;
    }

    public String getDeltaBS() {
        return deltaBS;
    }

    public void setDeltaBS(String deltaBS) {
        this.deltaBS = deltaBS;
    }

    public String getDeltaPA() {
        return deltaPA;
    }

    public void setDeltaPA(String deltaPA) {
        this.deltaPA = deltaPA;
    }

    public String getGammaBS() {
        return gammaBS;
    }

    public void setGammaBS(String gammaBS) {
        this.gammaBS = gammaBS;
    }

    public String getGammaPA() {
        return gammaPA;
    }

    public void setGammaPA(String gammaPA) {
        this.gammaPA = gammaPA;
    }

    public String getThetaBS() {
        return thetaBS;
    }

    public void setThetaBS(String thetaBS) {
        this.thetaBS = thetaBS;
    }

    public String getThetaPA() {
        return thetaPA;
    }

    public void setThetaPA(String thetaPA) {
        this.thetaPA = thetaPA;
    }

    public String getVegaBS() {
        return vegaBS;
    }

    public void setVegaBS(String vegaBS) {
        this.vegaBS = vegaBS;
    }

    public String getVegaPA() {
        return vegaPA;
    }

    public void setVegaPA(String vegaPA) {
        this.vegaPA = vegaPA;
    }

    public String getBaseBal() {
        return baseBal;
    }

    public void setBaseBal(String baseBal) {
        this.baseBal = baseBal;
    }

    public String getQuoteBal() {
        return quoteBal;
    }

    public void setQuoteBal(String quoteBal) {
        this.quoteBal = quoteBal;
    }

    public String getBaseBorrowed() {
        return baseBorrowed;
    }

    public void setBaseBorrowed(String baseBorrowed) {
        this.baseBorrowed = baseBorrowed;
    }

    public String getQuoteBorrowed() {
        return quoteBorrowed;
    }

    public void setQuoteBorrowed(String quoteBorrowed) {
        this.quoteBorrowed = quoteBorrowed;
    }

    public String getBaseInterest() {
        return baseInterest;
    }

    public void setBaseInterest(String baseInterest) {
        this.baseInterest = baseInterest;
    }

    public String getQuoteInterest() {
        return quoteInterest;
    }

    public void setQuoteInterest(String quoteInterest) {
        this.quoteInterest = quoteInterest;
    }

    public String getPosCcy() {
        return posCcy;
    }

    public void setPosCcy(String posCcy) {
        this.posCcy = posCcy;
    }

    public String getBizRefId() {
        return bizRefId;
    }

    public void setBizRefId(String bizRefId) {
        this.bizRefId = bizRefId;
    }

    public String getBizRefType() {
        return bizRefType;
    }

    public void setBizRefType(String bizRefType) {
        this.bizRefType = bizRefType;
    }
}
