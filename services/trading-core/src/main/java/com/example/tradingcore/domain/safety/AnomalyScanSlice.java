package com.example.tradingcore.domain.safety;

import java.util.List;
import lombok.Value;

/**
 * Одна выборка прохода детекции: строки плюс признак того, что она добыта
 * целиком.
 *
 * <p>Отдельным типом, а не вложенным в сервис
 * (.claude/rules/codestyle.md §«Строгие правила»). Живёт только в памяти
 * прохода — читателя за сериализацией нет.
 */
@Value
public class AnomalyScanSlice<T> {

    /** Строки выборки; при неполученной выборке — пустой список, не пусто. */
    List<T> rows;

    /** Выборка добыта целиком: вызов прошёл. */
    Boolean complete;
}
