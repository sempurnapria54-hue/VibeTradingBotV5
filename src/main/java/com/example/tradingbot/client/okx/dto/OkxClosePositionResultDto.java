package com.example.tradingbot.client.okx.dto;

public class OkxClosePositionResultDto {
    private String instId;
    private String posSide;

    public String getInstId() {
        return instId;
    }

    public void setInstId(String instId) {
        this.instId = instId;
    }

    public String getPosSide() {
        return posSide;
    }

    public void setPosSide(String posSide) {
        this.posSide = posSide;
    }
}
