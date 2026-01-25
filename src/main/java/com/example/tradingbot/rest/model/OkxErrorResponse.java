package com.example.tradingbot.rest.model;

public class OkxErrorResponse {
    private String code;
    private String msg;

    public OkxErrorResponse() {
    }

    public OkxErrorResponse(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
