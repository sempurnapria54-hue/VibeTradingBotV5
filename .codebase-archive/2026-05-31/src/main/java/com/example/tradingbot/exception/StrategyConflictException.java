package com.example.tradingbot.exception;

import org.springframework.http.HttpStatus;

public class StrategyConflictException extends TradingCommandException {

    public StrategyConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
