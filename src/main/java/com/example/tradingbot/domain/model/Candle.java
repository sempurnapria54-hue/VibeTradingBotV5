package com.example.tradingbot.domain.model;

public class Candle {
    private String ts;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;
    private String volumeCcy;
    private String volumeCcyQuote;
    private String confirm;

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getOpen() {
        return open;
    }

    public void setOpen(String open) {
        this.open = open;
    }

    public String getHigh() {
        return high;
    }

    public void setHigh(String high) {
        this.high = high;
    }

    public String getLow() {
        return low;
    }

    public void setLow(String low) {
        this.low = low;
    }

    public String getClose() {
        return close;
    }

    public void setClose(String close) {
        this.close = close;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getVolumeCcy() {
        return volumeCcy;
    }

    public void setVolumeCcy(String volumeCcy) {
        this.volumeCcy = volumeCcy;
    }

    public String getVolumeCcyQuote() {
        return volumeCcyQuote;
    }

    public void setVolumeCcyQuote(String volumeCcyQuote) {
        this.volumeCcyQuote = volumeCcyQuote;
    }

    public String getConfirm() {
        return confirm;
    }

    public void setConfirm(String confirm) {
        this.confirm = confirm;
    }
}
