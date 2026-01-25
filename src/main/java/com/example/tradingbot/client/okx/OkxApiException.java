package com.example.tradingbot.client.okx;

import lombok.Getter;

@Getter
public class OkxApiException extends RuntimeException {

    private final String code;

    public OkxApiException(String code, String message) {
        super(message);
        this.code = code;
    }
}
