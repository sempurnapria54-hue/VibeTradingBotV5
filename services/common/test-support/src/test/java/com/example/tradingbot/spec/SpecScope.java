package com.example.tradingbot.spec;

/** Маркер «идентификатор в области видимости не найден». */
public final class SpecScope {

    /** Отличает «поля нет» от «поле есть и пусто»: пустота — значение, отсутствие — нет. */
    public static final Object ABSENT = new Object() {

        @Override
        public String toString() {
            return "<нет такого поля>";
        }
    };

    private SpecScope() {
    }
}
