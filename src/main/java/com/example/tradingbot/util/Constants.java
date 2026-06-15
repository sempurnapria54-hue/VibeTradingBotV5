package com.example.tradingbot.util;

import java.math.MathContext;
import java.math.RoundingMode;
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

        /** Заголовок OKX: API-ключ (приватные endpoint'ы). */
        public static final String ACCESS_KEY_HEADER = "OK-ACCESS-KEY";

        /** Заголовок OKX: подпись запроса (base64 HMAC-SHA256). */
        public static final String ACCESS_SIGN_HEADER = "OK-ACCESS-SIGN";

        /** Заголовок OKX: метка времени запроса (ISO-8601, мс UTC). */
        public static final String ACCESS_TIMESTAMP_HEADER = "OK-ACCESS-TIMESTAMP";

        /** Заголовок OKX: passphrase API-ключа. */
        public static final String ACCESS_PASSPHRASE_HEADER = "OK-ACCESS-PASSPHRASE";

        /** Путь спецификации инструментов. */
        public static final String INSTRUMENTS_PATH = "/api/v5/public/instruments";

        /** Путь последних свечей. */
        public static final String CANDLES_PATH = "/api/v5/market/candles";

        /** Путь истории свечей. */
        public static final String HISTORY_CANDLES_PATH = "/api/v5/market/history-candles";

        /** Путь ordinary order (place POST / get GET). Приватный endpoint. */
        public static final String TRADE_ORDER_PATH = "/api/v5/trade/order";

        /** Путь позиций аккаунта (GET). Приватный endpoint. */
        public static final String ACCOUNT_POSITIONS_PATH = "/api/v5/account/positions";

        /** Путь отмены ordinary order (POST). Приватный endpoint. */
        public static final String TRADE_CANCEL_ORDER_PATH = "/api/v5/trade/cancel-order";

        /** Путь закрытия позиции (POST). Приватный endpoint. */
        public static final String TRADE_CLOSE_POSITION_PATH = "/api/v5/trade/close-position";

        /** Путь баланса аккаунта (GET). Приватный endpoint. */
        public static final String ACCOUNT_BALANCE_PATH = "/api/v5/account/balance";

        /** Путь конфигурации аккаунта (acctLv/posMode, GET). Приватный endpoint. */
        public static final String ACCOUNT_CONFIG_PATH = "/api/v5/account/config";

        /** Путь исполнений (fills, последние 3 дня, GET). Приватный endpoint. */
        public static final String TRADE_FILLS_PATH = "/api/v5/trade/fills";

        /** Путь истории исполнений (fills, последние 3 месяца, GET). Приватный endpoint. */
        public static final String TRADE_FILLS_HISTORY_PATH = "/api/v5/trade/fills-history";

        /** Путь live/pending ordinary orders (GET). Звено order evidence-cycle. */
        public static final String TRADE_ORDERS_PENDING_PATH = "/api/v5/trade/orders-pending";

        /** Путь истории ordinary orders (GET, 7 дней). Звено order evidence-cycle. */
        public static final String TRADE_ORDERS_HISTORY_PATH = "/api/v5/trade/orders-history";

        /** Путь live/pending algo orders (GET). Звено algo evidence-cycle. */
        public static final String TRADE_ORDERS_ALGO_PENDING_PATH = "/api/v5/trade/orders-algo-pending";

        /** Путь истории algo orders (GET). Звено algo evidence-cycle. */
        public static final String TRADE_ORDERS_ALGO_HISTORY_PATH = "/api/v5/trade/orders-algo-history";

        /** Имя query-параметра ordType (тип algo-order для pending/history). */
        public static final String PARAM_ORD_TYPE = "ordType";

        /** Путь standalone algo-order (place POST / get GET). Приватный endpoint. */
        public static final String TRADE_ORDER_ALGO_PATH = "/api/v5/trade/order-algo";

        /** Путь отмены ordinary algo-семьи (conditional/oco/trigger). Приватный endpoint. */
        public static final String TRADE_CANCEL_ALGOS_PATH = "/api/v5/trade/cancel-algos";

        /** Путь отмены advance algo-семьи (trailing/move_order_stop). Приватный endpoint. */
        public static final String TRADE_CANCEL_ADVANCE_ALGOS_PATH = "/api/v5/trade/cancel-advance-algos";

        /** Тип algo-order OKX: conditional (SL/TP/partial). */
        public static final String ALGO_ORD_TYPE_CONDITIONAL = "conditional";

        /** Тип algo-order OKX: OCO. */
        public static final String ALGO_ORD_TYPE_OCO = "oco";

        /** Тип algo-order OKX: trailing (move_order_stop). */
        public static final String ALGO_ORD_TYPE_MOVE_STOP = "move_order_stop";

        /** Флаг исполнения ноги market после trigger (slOrdPx/tpOrdPx). */
        public static final String MARKET_PRICE_FLAG = "-1";

        /** Имя query/body-параметра algoId (биржевой algo id). */
        public static final String PARAM_ALGO_ID = "algoId";

        /** Имя query/body-параметра algoClOrdId (stable client algo id). */
        public static final String PARAM_ALGO_CL_ORD_ID = "algoClOrdId";

        /** Adapter-константа режима маржи. */
        public static final String TD_MODE_ISOLATED = "isolated";

        /** Adapter-константа стороны позиции (net-режим). */
        public static final String POS_SIDE_NET = "net";

        /** Сторона ордера OKX: покупка. */
        public static final String SIDE_BUY = "buy";

        /** Сторона ордера OKX: продажа. */
        public static final String SIDE_SELL = "sell";

        /** Тип ордера OKX: лимитный. */
        public static final String ORD_TYPE_LIMIT = "limit";

        /** Тип ордера OKX: рыночный. */
        public static final String ORD_TYPE_MARKET = "market";

        /** Метка запросов бота (tag). */
        public static final String ORDER_TAG = "tb";

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

        /** Имя query/body-параметра ordId (биржевой id ордера). */
        public static final String PARAM_ORD_ID = "ordId";

        /** Имя query/body-параметра clOrdId (stable client id). */
        public static final String PARAM_CL_ORD_ID = "clOrdId";

        /** Имя query-параметра ccy (валюта). */
        public static final String PARAM_CCY = "ccy";

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

    /** Константы расчёта производных рыночных данных (индикаторы/структура/фаза). */
    @UtilityClass
    public class Calc {

        /** Контекст округления промежуточных делений BigDecimal в расчётах (precision/HALF_UP). */
        public static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
    }

    /** Константы аудита. */
    @UtilityClass
    public class Audit {

        /** Системный принципал createdBy/modifiedBy до ввода аутентификации (шаг 9). */
        public static final String SYSTEM_PRINCIPAL = "system";
    }
}
