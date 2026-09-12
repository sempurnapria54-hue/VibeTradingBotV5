package com.example.tradingbot.domain.util;

import static java.util.Objects.isNull;

import lombok.experimental.UtilityClass;

/**
 * Имя значения перечня для носителя, который перечня не знает.
 *
 * <p><b>Почему в общей библиотеке, а не рядом с каждым носителем.</b>
 * Перевод перечня в строку — один и тот же ответ у всех, кто кладёт
 * доменное значение в носитель без типа: содержимое события едет строкой
 * (docs/models/domain/other/AuditRecord.md), колонка персистентности
 * хранит {@code name()} доменного перечня
 * (.claude/rules/codestyle.md §«Слои моделей и enum'ы»). Копия хелпера в
 * каждом таком носителе — та самая форма, против которой заведён дом
 * статических хелперов (.claude/rules/codestyle.md §Lombok:
 * {@code @UtilityClass} в пакете {@code util}).
 *
 * <p><b>Пустое остаётся пустым, и это несущее свойство, а не удобство.</b>
 * Отсутствие значения перечня значаще
 * (docs/rules/absent-value-semantics.md): подстановка литерала на его
 * место выдала бы неизвестное за исход. Ровно поэтому у хелпера один
 * ответ на оба случая — и каждая переписанная от руки тернарная пара
 * была бы местом, где второй случай можно забыть.
 */
@UtilityClass
public class EnumNames {

    /**
     * Имя значения перечня; пустой перечень остаётся пустым.
     *
     * @param value значение перечня либо пусто
     * @return {@code name()} значения либо пусто
     */
    public static String name(Enum<?> value) {
        return isNull(value) ? null : value.name();
    }
}
