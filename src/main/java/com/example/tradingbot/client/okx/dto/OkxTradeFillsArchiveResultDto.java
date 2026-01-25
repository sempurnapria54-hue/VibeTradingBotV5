package com.example.tradingbot.client.okx.dto;

public class OkxTradeFillsArchiveResultDto {
    private String result;
    private String ts;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }
}
