package com.example.tradingbot.exception;

public class TradeRuleViolationException extends RuntimeException {

    public TradeRuleViolationException(String message) {
        super(message);
    }
}
