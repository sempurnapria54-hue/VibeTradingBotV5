package com.example.tradingbot.domain.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TradingCommandException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;

    public TradingCommandException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
