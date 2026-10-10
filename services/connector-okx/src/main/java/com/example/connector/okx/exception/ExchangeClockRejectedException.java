package com.example.connector.okx.exception;

/**
 * Площадка отвергла метку подписи приватного запроса ({@code 50102}), и
 * перемер смещения часов отказа не снял: чтение, повторённое новым моментом,
 * отвергнуто снова либо смещения не перемерить; команда граница не
 * повторяет вовсе (docs/components/IntegrationService.md).
 *
 * <p><b>Это не отказ кредов и не отказ площадки по вызову.</b> Ключи в
 * порядке, а вызов к рассмотрению не принят: часы нашей стороны разошлись с
 * источником (docs/integrations/okx/rules/auth-rejection-codes.md). Наружу
 * поэтому уезжает не класс границы, а общий класс недоступности — коннектор
 * сообщает о себе, что исполнить приватный вызов сейчас не может; ядро
 * читает его пропуском прохода без расхода бюджета повторов
 * (docs/rules/runtime-error-classification.md).
 *
 * <p><b>Наследует {@link ExchangeIntegrationException} намеренно</b> — тем же
 * доводом, что и {@link CredentialsRejectedException}: незнающий обработчик
 * увидит обычный сбой интеграции, а не пропустит его молча.
 */
public class ExchangeClockRejectedException extends ExchangeIntegrationException {

    public ExchangeClockRejectedException(String message) {
        super(message);
    }
}
