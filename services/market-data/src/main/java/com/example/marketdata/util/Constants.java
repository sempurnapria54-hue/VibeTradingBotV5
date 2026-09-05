package com.example.marketdata.util;

import lombok.experimental.UtilityClass;

/**
 * Константы сервиса рыночных данных: величины, осмысленные больше чем в
 * одном классе. Один класс-дом с вложенными по теме
 * (.claude/rules/codestyle.md §Константы).
 */
@UtilityClass
public class Constants {

    /** Точность денежных и ценовых величин в схеме. */
    @UtilityClass
    public class Price {

        /** Точность (всего значащих цифр) для денежных/ценовых величин. */
        public static final int PRECISION = 36;

        /** Масштаб (знаков после запятой) для денежных/ценовых величин. */
        public static final int SCALE = 18;
    }

    /** Разделители составных внутренних идентификаторов. */
    @UtilityClass
    public class InternalId {

        /** Разделитель сегментов составного internalId (группа свечей, идентичность вычисления). */
        public static final String SEPARATOR = ":";
    }
}
