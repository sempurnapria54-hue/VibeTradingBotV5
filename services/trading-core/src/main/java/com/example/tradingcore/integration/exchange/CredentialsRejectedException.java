package com.example.tradingcore.integration.exchange;

/**
 * Площадка отвергла ключи счёта: подпись не принята, ключ отозван либо
 * неизвестен, passphrase не та, ключ предъявлен не тому контуру
 * (docs/integrations/okx/rules/auth-rejection-codes.md).
 *
 * <p><b>Контролируемым исключением интеграции это НЕ является</b>, и
 * различение несущее: у трёх контролируемых категорий предмет — сущность,
 * и каждая несёт её {@code closeReason}. Отказ ключей сущности не
 * касается — он о праве контура обращаться к источнику; назначить
 * сущности причину закрытия значило бы объяснить её исход отказом права
 * (docs/rules/controlled-exchange-exceptions.md).
 *
 * <p><b>Наследует {@link ExchangeIntegrationException} намеренно:</b>
 * незнающий обработчик увидит обычный сбой интеграции, а не пропустит
 * отказ молча. Изъятие из ретраибельности и биржевая ступень 2 —
 * явные ветви границы исполнения (docs/rules/exchange-hold.md).
 */
public class CredentialsRejectedException extends ExchangeIntegrationException {

    public CredentialsRejectedException(String message) {
        super(message);
    }
}
