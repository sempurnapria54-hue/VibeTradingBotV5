package com.example.tradingbot.client.okx;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OkxApiException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public OkxApiException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
