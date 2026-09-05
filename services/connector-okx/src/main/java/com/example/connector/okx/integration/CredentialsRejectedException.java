package com.example.connector.okx.integration;

/**
 * Источник отверг наши креды: подпись не принята, ключ отозван либо неизвестен,
 * passphrase не та, ключ предъявлен не тому контуру
 * (docs/integrations/okx/rules/auth-rejection-codes.md).
 *
 * <p><b>Контролируемым исключением интеграции это НЕ является</b>, и различение
 * несущее: у всех трёх контролируемых категорий предмет — <b>сущность</b>, и
 * каждая несёт её {@code closeReason}. Отказ кредов сущности не касается — он
 * о праве контура обращаться к источнику; назначить сущности причину закрытия
 * значило бы объяснить её исход отказом права, то есть подставить факт
 * (docs/rules/controlled-exchange-exceptions.md).
 *
 * <p><b>Наследует {@link ExchangeIntegrationException} намеренно.</b> По природе
 * это ошибка взаимодействия с биржей, а не баг приложения и не нарушение
 * инварианта; наследование делает поведение <b>консервативным по умолчанию</b>
 * — незнающий обработчик увидит обычный сбой интеграции, а не пропустит его
 * молча. Изъятие из ретраибельности и подъём биржевой ступени 2 добавляются
 * явными ветвями на границе исполнения прохода
 * (docs/rules/runtime-error-classification.md, docs/rules/exchange-hold.md).
 */
public class CredentialsRejectedException extends ExchangeIntegrationException {

    public CredentialsRejectedException(String message) {
        super(message);
    }
}
