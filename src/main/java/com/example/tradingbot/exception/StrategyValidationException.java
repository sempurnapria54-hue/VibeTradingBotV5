package com.example.tradingbot.exception;

import org.springframework.http.HttpStatus;

public class StrategyValidationException extends TradingCommandException {

    public StrategyValidationException(HttpStatus httpStatus, String code, String message) {
        super(httpStatus, code, message);
    }
}
