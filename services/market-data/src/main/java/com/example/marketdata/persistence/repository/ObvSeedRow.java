package com.example.marketdata.persistence.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Проекция записанного значения OBV: момент бара и значение — всё, что
 * нужно затравке продолжения ряда (docs/spec/indicator-calculation.json,
 * `obvSeed`). Строка значения целиком ради двух полей не читается
 * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
 * одного поля»).
 */
public interface ObvSeedRow {

    OffsetDateTime getCandleTimestamp();

    BigDecimal getObv();
}
