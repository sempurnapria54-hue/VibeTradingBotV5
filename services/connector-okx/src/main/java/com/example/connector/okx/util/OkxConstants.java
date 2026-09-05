package com.example.connector.okx.util;

import lombok.experimental.UtilityClass;

/**
 * Словарь площадки OKX: литералы полей, значений и заголовков.
 *
 * <p><b>Живёт у коннектора, и это граница, а не удобство.</b> Константы
 * источника границу не переходят: их читают только слои, которые с
 * источником и говорят (.claude/rules/codestyle.md §«Слои»). В доноре
 * класс был вложен в общий {@code util.Constants}, откуда его мог
 * прочитать кто угодно; здесь он лежит в том сервисе, который
 * единственный имеет на него право.
 */
@UtilityClass
public class OkxConstants {

    /** Код успешного ответа OKX (иначе — ошибка). */
    public static final String SUCCESS_CODE = "0";

    /** Значение confirm OKX для закрытой свечи. */
    public static final String CONFIRM_CLOSED = "1";

    /** Заголовок demo-окружения OKX (header x-simulated-trading). */
    public static final String SIMULATED_HEADER = "x-simulated-trading";

    /** Значение заголовка demo-окружения: контур площадки — демо. */
    public static final String SIMULATED_ON = "1";

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

    /**
     * Путь истории свечей индекса пары котировки — носитель курса
     * cross-ccy: свежий index-candles окно в прошлом не обслуживает
     * (docs/integrations/okx/contracts/index-data.md). Публичный
     * endpoint.
     */
    public static final String HISTORY_INDEX_CANDLES_PATH = "/api/v5/market/history-index-candles";

    /** Путь тикера (рыночная цена: last/ask/bid). Публичный endpoint. */
    public static final String MARKET_TICKER_PATH = "/api/v5/market/ticker";

    /**
     * Тикеры ВСЕГО листинга одним ответом. Отдельная константа от
     * поинструментного чтения: срез по листингу поинструментным обходом
     * стоил бы сотни запросов из общего бюджета лимитов.
     */
    public static final String MARKET_TICKERS_PATH = "/api/v5/market/tickers";

    /** Книга заявок инструмента. */
    public static final String MARKET_BOOKS_PATH = "/api/v5/market/books";

    /** Марк-цены: агрегатное чтение по типу инструмента. */
    public static final String MARK_PRICE_PATH = "/api/v5/public/mark-price";

    /** Цены индексов: агрегатное чтение по расчётной валюте. */
    public static final String INDEX_TICKERS_PATH = "/api/v5/market/index-tickers";

    /** Имя параметра запроса: глубина книги на сторону. */
    public static final String PARAM_SIZE = "sz";

    /** Имя параметра запроса: расчётная валюта индекса. */
    public static final String PARAM_QUOTE_CURRENCY = "quoteCcy";

    /** Путь ordinary order (place POST / get GET). Приватный endpoint. */
    public static final String TRADE_ORDER_PATH = "/api/v5/trade/order";

    /** Путь позиций аккаунта (GET). Приватный endpoint. */
    public static final String ACCOUNT_POSITIONS_PATH = "/api/v5/account/positions";

    /** Путь серверного времени источника. Публичный endpoint. */
    public static final String PUBLIC_TIME_PATH = "/api/v5/public/time";

    /** Путь истории закрытых позиций аккаунта. Приватный endpoint. */
    public static final String ACCOUNT_POSITIONS_HISTORY_PATH = "/api/v5/account/positions-history";

    /** Путь отмены ordinary order (POST). Приватный endpoint. */
    public static final String TRADE_CANCEL_ORDER_PATH = "/api/v5/trade/cancel-order";

    /** Путь закрытия позиции (POST). Приватный endpoint. */
    public static final String TRADE_CLOSE_POSITION_PATH = "/api/v5/trade/close-position";

    /** Путь баланса аккаунта (GET). Приватный endpoint. */
    public static final String ACCOUNT_BALANCE_PATH = "/api/v5/account/balance";

    /** Ставки комиссий счёта: ось запроса — тип инструмента, не инструмент. */
    public static final String ACCOUNT_TRADE_FEE_PATH = "/api/v5/account/trade-fee";

    /** Путь конфигурации аккаунта (acctLv/posMode, GET). Приватный endpoint. */
    public static final String ACCOUNT_CONFIG_PATH = "/api/v5/account/config";

    /** Путь выставления плеча инструмента (POST). Приватный endpoint. */
    public static final String ACCOUNT_SET_LEVERAGE_PATH = "/api/v5/account/set-leverage";


    /** Путь bill-записей движений счёта (7 дней, GET). Приватный endpoint. */
    public static final String ACCOUNT_BILLS_PATH = "/api/v5/account/bills";

    /** Путь архива bill-записей (3 месяца, GET). Приватный endpoint. */
    public static final String ACCOUNT_BILLS_ARCHIVE_PATH = "/api/v5/account/bills-archive";


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

    /**
     * Потолок страницы pending-срезов. Задаётся явно, чтобы усечение
     * было НАБЛЮДАЕМЫМ: полная страница означает «возможно, есть
     * ещё», и проход детекции объявляет себя неполным вместо того,
     * чтобы принять усечённый срез за полный
     * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
     * Меряется у КАЖДОГО вызова, а не у склейки семей.
     */
    public static final Integer PENDING_PAGE_LIMIT = 100;

    /**
     * Маркер контура в клиентском идентификаторе заявки. Единственный
     * дискриминатор «наше против чужого» на стороне БИРЖИ: по нему
     * проактивная детекция опознаёт заявку, которую заводили не мы
     * (docs/components/AnomalyJob.md §«Что ищет»). Короткий — потолок
     * clOrdId у источника, и маркер тратит энтропию случайной части.
     */
    public static final String CLIENT_ID_MARKER = "vtb";

    /**
     * Потолок длины клиентского идентификатора заявки у источника
     * (docs/integrations/okx/contracts/order.md). Ограничение биржи,
     * а не алгоритма генератора, поэтому дом у него здесь.
     */
    public static final int CLIENT_ID_MAX_LENGTH = 32;

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

    /**
     * Имя query-параметра state истории условных заявок. У эндпоинта
     * обязателен он либо algoId (иначе code=50015).
     */
    public static final String PARAM_STATE = "state";

    /** Терминальное состояние условной заявки: сработала. */
    public static final String ALGO_STATE_EFFECTIVE = "effective";

    /** Терминальное состояние условной заявки: снята. */
    public static final String ALGO_STATE_CANCELED = "canceled";

    /** Терминальное состояние условной заявки: сработала, заявка не исполнилась. */
    public static final String ALGO_STATE_ORDER_FAILED = "order_failed";

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

    /** Имя query-параметра before (нижняя граница окна по времени). */
    public static final String PARAM_BEFORE = "before";

    /** Имя query-параметра begin (нижняя граница фильтра по времени, Unix ms). */
    public static final String PARAM_BEGIN = "begin";

    /** Имя query-параметра end (верхняя граница фильтра по времени, Unix ms). */
    public static final String PARAM_END = "end";

    /** Класс инструментов контура: бессрочный своп. */
    public static final String INST_TYPE_SWAP = "SWAP";

    /** Размер страницы истории позиций (потолок источника — 100). */
    public static final String POSITIONS_HISTORY_PAGE_LIMIT = "100";

    /** Имя query-параметра limit. */
    public static final String PARAM_LIMIT = "limit";

    /** Имя query/body-параметра ordId (биржевой id ордера). */
    public static final String PARAM_ORD_ID = "ordId";

    /** Имя query/body-параметра clOrdId (stable client id). */
    public static final String PARAM_CL_ORD_ID = "clOrdId";

    /** Имя query-параметра ccy (валюта). */
    public static final String PARAM_CCY = "ccy";

    /** Таймфрейм OKX: 1 секунда (index-candles, координата курса). */
    public static final String BAR_ONE_SECOND = "1s";

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
