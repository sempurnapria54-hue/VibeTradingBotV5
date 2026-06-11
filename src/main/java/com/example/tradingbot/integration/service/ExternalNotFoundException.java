package com.example.tradingbot.integration.service;

/**
 * Бросает RefreshExecutor / recovery-search boundary, если после
 * полного evidence-cycle сущность не найдена и финал нельзя безопасно
 * объяснить. Нельзя бросать после одного пустого response. Reaction:
 * сущность → ERROR (closeReason = MISSING_AFTER_REFRESH), Deal → ERROR.
 * См. docs/rules/controlled-exchange-exceptions.md.
 */
public class ExternalNotFoundException extends ControlledExchangeException {

    public ExternalNotFoundException(String message) {
        super(message);
    }
}
