package com.example.connector.okx.exception;

/**
 * Бросает IntegrationService / adapter-layer, если response получен, но
 * нарушает ожидаемый exchange invariant (tdMode != isolated, posSide !=
 * net, side/ordType != expected, недостача обязательного поля) либо несёт
 * значение вне формы контракта (число, время, перечень, длина позиционной
 * строки — сеть разбора OkxExchangeGateway).
 *
 * <p><b>Это факт о ЧТЕНИИ, а не о сущности:</b> судьба сущности таким
 * ответом не наблюдена, и её статус остаётся последним применённым фактом —
 * причины закрытия у класса нет. Реакция — за классом отказа: Deal → ERROR,
 * биржевая ступень 2 ({@code ExchangeAccount.safetyRung = TRADE_BLOCKED},
 * код причины {@code EXCHANGE_CONTROLLED_FAILURE}). См.
 * docs/rules/controlled-exchange-exceptions.md.
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }

    public ExternalInvariantViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
