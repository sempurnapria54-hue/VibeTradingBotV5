package com.example.tradingbot.client.okx.dto;

public class OkxFillsArchiveRequest {
    private String year;
    private String quarter;

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
}
