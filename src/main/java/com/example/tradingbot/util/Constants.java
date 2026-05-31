package com.example.tradingbot.util;

import lombok.experimental.UtilityClass;

/**
 * Единый дом констант проекта: один класс с вложенными классами по
 * темам. Константа кладётся во вложенный класс по своей теме
 * (точность цены, интеграция с биржей, аудит и т. п.). Локальные
 * технические константы, осмысленные только внутри одного класса
 * (индексы позиционного разбора, коэффициенты алгоритма), остаются
 * рядом с кодом.
 */
public final class Constants {

    private Constants() {
    }

    /**
     * Точность/масштаб денежных и ценовых величин — едины для
     * domain-расчётов и precision/scale persistence-колонок.
     */
    @UtilityClass
    public class Price {

        /** Точность (всего значащих цифр) для денежных/ценовых величин. */
        public static final int PRECISION = 36;

        /** Масштаб (знаков после запятой) для денежных/ценовых величин. */
        public static final int SCALE = 18;
    }

    /** Константы интеграции с OKX: коды, флаги, заголовки, пути и имена полей запроса, таймфреймы. */
    @UtilityClass
    public class Okx {

        /** Код успешного ответа OKX (иначе — ошибка). */
        public static final String SUCCESS_CODE = "0";

        /** Значение confirm OKX для закрытой свечи. */
        public static final String CONFIRM_CLOSED = "1";

        /** Заголовок demo-окружения OKX (header x-simulated-trading). */
        public static final String SIMULATED_HEADER = "x-simulated-trading";

        /** Путь спецификации инструментов. */
        public static final String INSTRUMENTS_PATH = "/api/v5/public/instruments";

        /** Путь последних свечей. */
        public static final String CANDLES_PATH = "/api/v5/market/candles";

        /** Путь истории свечей. */
        public static final String HISTORY_CANDLES_PATH = "/api/v5/market/history-candles";

        /** Имя query-параметра instType. */
        public static final String PARAM_INST_TYPE = "instType";

        /** Имя query-параметра instId. */
        public static final String PARAM_INST_ID = "instId";

        /** Имя query-параметра bar. */
        public static final String PARAM_BAR = "bar";

        /** Имя query-параметра after. */
        public static final String PARAM_AFTER = "after";

        /** Имя query-параметра limit. */
        public static final String PARAM_LIMIT = "limit";

        /** Таймфрейм OKX: 1 минута. */
        public static final String BAR_ONE_MINUTE = "1m";

        /** Таймфрейм OKX: 3 минуты. */
        public static final String BAR_THREE_MINUTES = "3m";

        /** Таймфрейм OKX: 5 минут. */
        public static final String BAR_FIVE_MINUTES = "5m";

        /** Таймфрейм OKX: 15 минут. */
        public static final String BAR_FIFTEEN_MINUTES = "15m";

        /** Таймфрейм OKX: 1 час. */
        public static final String BAR_ONE_HOUR = "1H";

        /** Таймфрейм OKX: 2 часа. */
        public static final String BAR_TWO_HOURS = "2H";

        /** Таймфрейм OKX: 4 часа. */
        public static final String BAR_FOUR_HOURS = "4H";

        /** Таймфрейм OKX: 1 день (UTC-выровненный, 1Dutc). */
        public static final String BAR_ONE_DAY = "1Dutc";
    }

    /** Константы аудита. */
    @UtilityClass
    public class Audit {

        /** Системный принципал createdBy/modifiedBy до ввода аутентификации (шаг 9). */
        public static final String SYSTEM_PRINCIPAL = "system";
    }
}
