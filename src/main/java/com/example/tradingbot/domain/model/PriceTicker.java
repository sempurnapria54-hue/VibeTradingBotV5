package com.example.tradingbot.domain.model;

public class PriceTicker {
    private String instType;
    private String instId;
    private String last;
    private String lastSz;
    private String askPx;
    private String askSz;
    private String bidPx;
    private String bidSz;
    private String open24h;
    private String high24h;
    private String low24h;
    private String volCcy24h;
    private String vol24h;
    private String sodUtc0;
    private String sodUtc8;
    private String ts;

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

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getLastSz() {
        return lastSz;
    }

    public void setLastSz(String lastSz) {
        this.lastSz = lastSz;
    }

    public String getAskPx() {
        return askPx;
    }

    public void setAskPx(String askPx) {
        this.askPx = askPx;
    }

    public String getAskSz() {
        return askSz;
    }

    public void setAskSz(String askSz) {
        this.askSz = askSz;
    }

    public String getBidPx() {
        return bidPx;
    }

    public void setBidPx(String bidPx) {
        this.bidPx = bidPx;
    }

    public String getBidSz() {
        return bidSz;
    }

    public void setBidSz(String bidSz) {
        this.bidSz = bidSz;
    }

    public String getOpen24h() {
        return open24h;
    }

    public void setOpen24h(String open24h) {
        this.open24h = open24h;
    }

    public String getHigh24h() {
        return high24h;
    }

    public void setHigh24h(String high24h) {
        this.high24h = high24h;
    }

    public String getLow24h() {
        return low24h;
    }

    public void setLow24h(String low24h) {
        this.low24h = low24h;
    }

    public String getVolCcy24h() {
        return volCcy24h;
    }

    public void setVolCcy24h(String volCcy24h) {
        this.volCcy24h = volCcy24h;
    }

    public String getVol24h() {
        return vol24h;
    }

    public void setVol24h(String vol24h) {
        this.vol24h = vol24h;
    }

    public String getSodUtc0() {
        return sodUtc0;
    }

    public void setSodUtc0(String sodUtc0) {
        this.sodUtc0 = sodUtc0;
    }

    public String getSodUtc8() {
        return sodUtc8;
    }

    public void setSodUtc8(String sodUtc8) {
        this.sodUtc8 = sodUtc8;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }
}
