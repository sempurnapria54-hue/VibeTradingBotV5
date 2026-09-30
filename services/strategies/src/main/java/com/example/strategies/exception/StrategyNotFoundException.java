package com.example.strategies.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Определение, названное путём, в контексте тенанта не найдено.
 *
 * <p><b>Чужое и несуществующее здесь одно и то же исключение</b>: точка,
 * адресующая одну сущность, отвечает на чужую тем же исходом, что на
 * несуществующую, и тело отказа двух причин не различает — ни классом, ни
 * пояснением (docs/architecture/contracts.md §«Контекст тенанта в
 * вызове»). Исход опознаётся классом поверхности плюс реджект-кодом
 * {@code STRATEGY_NOT_FOUND} в пояснении.
 *
 * <p><b>Один класс на две точки — чтение и переход</b>: текст отказа у них
 * обязан совпадать, и второй его носитель разошёлся бы с первым первой же
 * правкой.
 */
public class StrategyNotFoundException extends ResponseStatusException {

    public StrategyNotFoundException(String internalId) {
        super(HttpStatus.NOT_FOUND, "STRATEGY_NOT_FOUND: определения " + internalId + " у тенанта нет");
    }
}
