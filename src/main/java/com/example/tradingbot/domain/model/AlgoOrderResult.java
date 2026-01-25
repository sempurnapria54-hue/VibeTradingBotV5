package com.example.tradingbot.domain.model;

public class AlgoOrderResult {
    private String algoId;
    private String algoClOrdId;
    private String clOrdId;
    private String sCode;
    private String sMsg;
    private String tag;

    public String getAlgoId() {
        return algoId;
    }

    public void setAlgoId(String algoId) {
        this.algoId = algoId;
    }

    public String getAlgoClOrdId() {
        return algoClOrdId;
    }

    public void setAlgoClOrdId(String algoClOrdId) {
        this.algoClOrdId = algoClOrdId;
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public void setClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
    }

    public String getSCode() {
        return sCode;
    }

    public void setSCode(String sCode) {
        this.sCode = sCode;
    }

    public String getSMsg() {
        return sMsg;
    }

    public void setSMsg(String sMsg) {
        this.sMsg = sMsg;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
