package com.example.tradingbot.domain.service.deal;

public class TradeRuleViolationException extends RuntimeException {

    public TradeRuleViolationException(String message) {
        super(message);
    }
}
