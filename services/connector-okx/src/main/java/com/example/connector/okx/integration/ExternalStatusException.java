package com.example.connector.okx.integration;

import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import lombok.Getter;

/**
 * Бросает status resolver, если внешний статус получен, но неизвестен
 * или означает problem-state. Reaction: сущность → ERROR (closeReason =
 * reasonCode), Deal → ERROR, Exchange → HOLD. См.
 * docs/rules/controlled-exchange-exceptions.md.
 */
@Getter
public class ExternalStatusException extends ControlledExchangeException {

    private final ExternalStatusReason reasonCode;

    public ExternalStatusException(ExternalStatusReason reasonCode, String externalStatus) {
        super("Unexpected external status [" + externalStatus + "] reason=" + reasonCode);
        this.reasonCode = reasonCode;
    }
}
