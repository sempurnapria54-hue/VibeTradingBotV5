package com.example.tradingcore.exception;

/**
 * Ответ площадки получен, но нарушает инвариант контракта: недостача
 * обязательного поля, запись чужого инструмента, значение вне формы
 * контракта, расхождение эха заявки с нашей строкой.
 *
 * <p><b>Производителей два.</b> Форму ответа проверяет ГРАНИЦА — там, где
 * ответ впервые разбирается (docs/components/IntegrationService.md
 * §«Проверка инвариантов контракта»), и ядро получает уже класс. Эхо заявки
 * сверяет добыча в ЯДРЕ: ожидаемое — наша строка, которой коннектор при
 * чтении не видит (docs/models/mapping/AlgoOrder.md §«Сверка эха»,
 * docs/models/mapping/Order.md).
 *
 * <p><b>Статуса сущности класс не меняет и причины закрытия ей не
 * пишет.</b> Нарушение инварианта — факт о ЧТЕНИИ, а не о сущности: её
 * судьба им не наблюдена, и статус остаётся последним применённым фактом.
 * Реакция принадлежит классу отказа — сделка в {@code ERROR}, биржевая
 * ступень 2 (docs/rules/controlled-exchange-exceptions.md).
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }
}
