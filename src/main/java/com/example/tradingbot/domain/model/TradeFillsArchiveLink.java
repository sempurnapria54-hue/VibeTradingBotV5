package com.example.tradingbot.domain.model;

public class TradeFillsArchiveLink {
    private String year;
    private String quarter;
    private String state;
    private String ts;
    private String fileHref;

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getQuarter() {
        return quarter;
    }

    public void setQuarter(String quarter) {
        this.quarter = quarter;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getFileHref() {
        return fileHref;
    }

    public void setFileHref(String fileHref) {
        this.fileHref = fileHref;
    }
}
