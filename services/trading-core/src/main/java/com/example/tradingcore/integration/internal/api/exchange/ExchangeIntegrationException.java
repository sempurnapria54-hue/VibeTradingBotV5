package com.example.tradingcore.integration.internal.api.exchange;

/**
 * Отказ взаимодействия с площадкой, приехавший классом через границу
 * коннектора: площадка не ответила, ответила отказом либо не приняла наши
 * ключи.
 *
 * <p><b>По природе это сбой ВЗАИМОДЕЙСТВИЯ, а не баг приложения и не
 * нарушение инварианта</b> — отсюда отдельная иерархия от
 * {@link ControlledExchangeException}: у контролируемых категорий предмет
 * — сущность, и каждая несёт её {@code closeReason}
 * (docs/rules/controlled-exchange-exceptions.md).
 *
 * <p><b>Ретраибельность решает не этот класс.</b> Он говорит, ЧТО
 * случилось; повторять ли — политика границы исполнения прохода
 * (docs/rules/runtime-error-classification.md), и она читает конкретный
 * подкласс: отвергнутые ключи из ретраибельного изъяты, недоступность
 * хранилища — наоборот, ровно тот случай, ради которого повтор и заведён.
 */
public class ExchangeIntegrationException extends RuntimeException {

    public ExchangeIntegrationException(String message) {
        super(message);
    }

    public ExchangeIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
