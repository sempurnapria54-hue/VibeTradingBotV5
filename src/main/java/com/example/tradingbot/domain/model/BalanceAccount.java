package com.example.tradingbot.domain.model;

import java.util.List;

public class BalanceAccount {
    private String uTime;
    private String totalEq;
    private String isoEq;
    private String adjEq;
    private String availEq;
    private String ordFroz;
    private String imr;
    private String mmr;
    private String borrowFroz;
    private String mgnRatio;
    private String notionalUsd;
    private String notionalUsdForBorrow;
    private String notionalUsdForSwap;
    private String notionalUsdForFutures;
    private String notionalUsdForOption;
    private String upl;
    private String delta;
    private String deltaLever;
    private String deltaNeutralStatus;
    private List<BalanceDetail> details;

    public String getUTime() {
        return uTime;
    }

    public void setUTime(String uTime) {
        this.uTime = uTime;
    }

    public String getTotalEq() {
        return totalEq;
    }

    public void setTotalEq(String totalEq) {
        this.totalEq = totalEq;
    }

    public String getIsoEq() {
        return isoEq;
    }

    public void setIsoEq(String isoEq) {
        this.isoEq = isoEq;
    }

    public String getAdjEq() {
        return adjEq;
    }

    public void setAdjEq(String adjEq) {
        this.adjEq = adjEq;
    }

    public String getAvailEq() {
        return availEq;
    }

    public void setAvailEq(String availEq) {
        this.availEq = availEq;
    }

    public String getOrdFroz() {
        return ordFroz;
    }

    public void setOrdFroz(String ordFroz) {
        this.ordFroz = ordFroz;
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

    public String getBorrowFroz() {
        return borrowFroz;
    }

    public void setBorrowFroz(String borrowFroz) {
        this.borrowFroz = borrowFroz;
    }

    public String getMgnRatio() {
        return mgnRatio;
    }

    public void setMgnRatio(String mgnRatio) {
        this.mgnRatio = mgnRatio;
    }

    public String getNotionalUsd() {
        return notionalUsd;
    }

    public void setNotionalUsd(String notionalUsd) {
        this.notionalUsd = notionalUsd;
    }

    public String getNotionalUsdForBorrow() {
        return notionalUsdForBorrow;
    }

    public void setNotionalUsdForBorrow(String notionalUsdForBorrow) {
        this.notionalUsdForBorrow = notionalUsdForBorrow;
    }

    public String getNotionalUsdForSwap() {
        return notionalUsdForSwap;
    }

    public void setNotionalUsdForSwap(String notionalUsdForSwap) {
        this.notionalUsdForSwap = notionalUsdForSwap;
    }

    public String getNotionalUsdForFutures() {
        return notionalUsdForFutures;
    }

    public void setNotionalUsdForFutures(String notionalUsdForFutures) {
        this.notionalUsdForFutures = notionalUsdForFutures;
    }

    public String getNotionalUsdForOption() {
        return notionalUsdForOption;
    }

    public void setNotionalUsdForOption(String notionalUsdForOption) {
        this.notionalUsdForOption = notionalUsdForOption;
    }

    public String getUpl() {
        return upl;
    }

    public void setUpl(String upl) {
        this.upl = upl;
    }

    public String getDelta() {
        return delta;
    }

    public void setDelta(String delta) {
        this.delta = delta;
    }

    public String getDeltaLever() {
        return deltaLever;
    }

    public void setDeltaLever(String deltaLever) {
        this.deltaLever = deltaLever;
    }

    public String getDeltaNeutralStatus() {
        return deltaNeutralStatus;
    }

    public void setDeltaNeutralStatus(String deltaNeutralStatus) {
        this.deltaNeutralStatus = deltaNeutralStatus;
    }

    public List<BalanceDetail> getDetails() {
        return details;
    }

    public void setDetails(List<BalanceDetail> details) {
        this.details = details;
    }
}
