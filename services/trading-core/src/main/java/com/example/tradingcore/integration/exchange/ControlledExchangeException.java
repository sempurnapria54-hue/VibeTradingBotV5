package com.example.tradingcore.integration.exchange;

/**
 * База контролируемых исключений интеграции: внешний факт получен (или
 * должен был быть найден), но продолжать нормальный проход небезопасно.
 *
 * <p>Реакция единообразна для всех категорий и квалификатора «по тяжести»
 * не имеет: сущность → {@code ERROR} с назначенной причиной закрытия,
 * сделка → {@code ERROR}, счёт → {@code TRADE_BLOCKED} с flatten
 * (docs/rules/controlled-exchange-exceptions.md §«Реакция — безусловная
 * биржевая ступень 2»). Площадке, повёдшей себя неожиданно, мы
 * сворачиваемся и замолкаем.
 *
 * <p><b>В сервисной конструкции исключение через границу не летит.</b>
 * То, что внутри монолита было брошенным исключением, приезжает к ядру
 * КЛАССОМ ОТКАЗА в ответе коннектора
 * ({@code com.example.tradingbot.domain.exchange.ExchangeFailureClass});
 * здесь оно снова становится исключением — на этой стороне границы, где
 * его и ловит проход.
 */
public class ControlledExchangeException extends RuntimeException {

    public ControlledExchangeException(String message) {
        super(message);
    }
}
