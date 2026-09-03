package com.example.tradingbot.domain.safety;

import java.util.List;
import lombok.Value;

/**
 * Одна выборка прохода детекции: строки плюс признак того, что она
 * добыта целиком. Отдельным типом, а не вложенным в сервис
 * (.claude/rules/codestyle.md §«Строгие правила»).
 */
@Value
public class AnomalyScanSlice<T> {

    List<T> rows;

    /** Выборка добыта целиком: вызов прошёл и страница не упёрлась в потолок. */
    Boolean complete;
}
