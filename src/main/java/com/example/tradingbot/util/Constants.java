package com.example.tradingbot.util;

import java.math.BigDecimal;
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

    /** Машинные коды причин реактивного safety-холда (попадают в AnomalyReport.code). */
    @UtilityClass
    public class Hold {

        /** L4: контролируемое нарушение биржевого факта/инварианта (ControlledExchangeException → VALIDATION_ERROR). */
        public static final String EXCHANGE_CONTROLLED_VIOLATION = "EXCHANGE_CONTROLLED_VIOLATION";

        /** L4-эскалация: kill-switch не подтвердил flat в пределах лимита попыток (остаточный риск, HOLD-Q1). */
        public static final String EXCHANGE_KILL_SWITCH_RESIDUAL = "EXCHANGE_KILL_SWITCH_RESIDUAL";

        /**
         * Ступень 2: живой риск без покрытия и без действующего
         * обязательства — сломался наш собственный учёт покрытия, радиус
         * доверия к нему неизвестен
         * (docs/rules/live-risk-protection.md §«Реакция на непокрытый
         * риск», docs/rules/exchange-hold.md §«Ступень 2 — сворачивание»).
         */
        public static final String EXCHANGE_LIVE_RISK_UNCOVERED = "EXCHANGE_LIVE_RISK_UNCOVERED";

        /**
         * Ступень 1: слепота safety-сети — серия подряд идущих неполных
         * проходов проактивной детекции. Ступень мягкая: слепота не
         * нарушает посылку «защита стоит на бирже и исполняется ею
         * независимо от нашей интеграции», значит принятый риск покрыт и
         * снимать его нечем; под сомнением право набирать новый вслепую
         * (docs/components/AnomalyJob.md §«Гейт полноты среза»,
         * docs/rules/exchange-hold.md §«Ступень 1 — мягкий холд»).
         */
        public static final String ANOMALY_PASS_INCOMPLETE = "ANOMALY_PASS_INCOMPLETE";

        /**
         * Ступень 2: живая сущность по инструменту ВНЕ контура. Счёт
         * принадлежит системе единолично, восстановление здесь
         * недостижимо (строки инструмента нет), и риск не может быть
         * приписан ничему (docs/rules/exchange-hold.md §«Ступень 2 —
         * сворачивание» п.3).
         */
        public static final String EXCHANGE_FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

        /**
         * Ступень 2: живая заявка либо algo без маркера контура —
         * сущность, которую мы не создавали (там же, п.3).
         */
        public static final String EXCHANGE_FOREIGN_ORDER = "EXCHANGE_FOREIGN_ORDER";

        /**
         * Ступень 2: позиций по одному инструменту больше одной — режим
         * позиций счёта не тот, который объявлен adapter-константой
         * (docs/rules/exchange-hold.md §«Ступень 2 — сворачивание» п.1).
         */
        public static final String EXCHANGE_POSITION_MODE_VIOLATION = "EXCHANGE_POSITION_MODE_VIOLATION";

        /**
         * Журнальный отчёт: стоящая жёсткая ступень радиуса не
         * проэнфорсена — на бирже живут сущности этого радиуса. Свой
         * kill-switch эта реакция не гоняет, поглощение держит анкер
         * (docs/components/SafetyHoldCoordinator.md §«Поглощённый сигнал
         * наблюдаем»).
         */
        public static final String SAFETY_RUNG_NOT_ENFORCED = "SAFETY_RUNG_NOT_ENFORCED";

        /**
         * Журнальный отчёт: локально ТЕРМИНАЛЬНАЯ сущность жива на бирже.
         * Блокировки в составе реакции нет; ключ дедупа несёт предмет —
         * саму сущность (docs/models/domain/other/AnomalyReport.md).
         */
        public static final String LOCAL_TERMINAL_ALIVE_ON_EXCHANGE = "LOCAL_TERMINAL_ALIVE_ON_EXCHANGE";

        /**
         * Мягкая ступень инструмента: хвосты заявок либо algo, не
         * объяснимые живой сделкой (docs/rules/instrument-hold.md
         * §Триггеры).
         */
        public static final String INSTRUMENT_ORPHAN_ORDERS = "INSTRUMENT_ORPHAN_ORDERS";

        /**
         * Мягкая ступень инструмента: живая сделка перестала укладываться
         * в потолки при стоящей защите — те же неравенства при нулевом
         * акте (docs/rules/instrument-hold.md §«Форма реакции на нарушение
         * риск-политики при живой защите»).
         */
        public static final String RISK_POLICY_BREACH_UNDER_PROTECTION = "RISK_POLICY_BREACH_UNDER_PROTECTION";

        /**
         * Ступень 2: сумма gross-экспозиций траншей разошлась с
         * нетто-размером живого эпизода. Код СВОЙ, а не общий с потерей
         * покрытия: коды — единственное, чем в данных различаются
         * основания одной ступени, и схлопывание двух разных расхождений
         * в одну строку отняло бы у разбора именно то, ради чего коды
         * заведены (docs/models/domain/aggregate/Deal.md).
         */
        public static final String EXCHANGE_EXPOSURE_MISMATCH = "EXCHANGE_EXPOSURE_MISMATCH";

        /** L3: шаг стратегии велел сворачиваться аварийно из-за устаревших рыночных данных. */
        public static final String INSTRUMENT_MARKET_DATA_EXPIRED = "INSTRUMENT_MARKET_DATA_EXPIRED";

        /** L3, мягкая: разбор истории не предъявил записи защиты — её судьба неизвестна. */
        public static final String INSTRUMENT_PROTECTION_FATE_UNKNOWN = "INSTRUMENT_PROTECTION_FATE_UNKNOWN";

        /**
         * Журнальный STATE-отчёт биржи: движение счёта не покрыто
         * отображением категорий — принимающая корзина непуста
         * (docs/models/mapping/DealCashFlow.md §«Резолв категории»).
         * Блокировки в составе реакции нет.
         */
        public static final String UNCLASSIFIED_CASH_FLOW = "UNCLASSIFIED_CASH_FLOW";

        /**
         * Расхождение сверки P&L сверх допуска. В боевом режиме допуска
         * стои́т и на отчёте, и на сигнале ступени 1; в разведочном —
         * только на отчёте, ступень не запрашивается
         * (docs/rules/pnl-reconciliation.md §«Реакция на расхождение»).
         */
        public static final String PNL_RECONCILIATION_MISMATCH = "PNL_RECONCILIATION_MISMATCH";

        /**
         * Журнальный отчёт-СОБЫТИЕ: вход состоялся, а знаменателя R нет.
         * Без него популяция сделок с потерянным знаменателем не всплывает
         * ни в одном сигнале (docs/models/domain/aggregate/Deal.md
         * §Енумы).
         */
        public static final String RISK_BENCHMARK_MISSING = "RISK_BENCHMARK_MISSING";

        /**
         * Журнальный отчёт-СОБЫТИЕ: запись закрытия эпизода добыта, а
         * торговый исход из её типа не выводится — поле пусто либо
         * значение перечню не принадлежит
         * (docs/models/mapping/PositionCloseResult.md).
         */
        public static final String UNRECOGNIZED_CLOSE_TYPE = "UNRECOGNIZED_CLOSE_TYPE";
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

        /**
         * Путь истории свечей индекса пары котировки — носитель курса
         * cross-ccy: свежий index-candles окно в прошлом не обслуживает
         * (docs/integrations/okx/contracts/index-data.md). Публичный
         * endpoint.
         */
        public static final String HISTORY_INDEX_CANDLES_PATH = "/api/v5/market/history-index-candles";

        /** Путь тикера (рыночная цена: last/ask/bid). Публичный endpoint. */
        public static final String MARKET_TICKER_PATH = "/api/v5/market/ticker";

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

    /** Константы расчёта производных рыночных данных (индикаторы/структура/фаза). */
    @UtilityClass
    public class Calc {

        /** Контекст округления промежуточных делений BigDecimal в расчётах (precision/HALF_UP). */
        public static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
    }

    /** Константы риск-контроля, не назначаемые конфигурацией. */
    @UtilityClass
    public class Risk {

        /**
         * Доля катастрофического потолка, которая обязана остаться
         * свободной после объявленного нотинала. КОНСТАНТА ПРАВИЛА, а не
         * число риск-аппетита: выведена из наблюдаемой величины проскока,
         * поля конфигурации не имеет и отказа при незаданности не даёт
         * (docs/rules/risk-policy.md §«Нотинал укладывается в потолок с
         * запасом, а не в границу», docs/spec/strategy-reference.json,
         * операнд {@code notionalHeadroomShare}).
         */
        public static final BigDecimal NOTIONAL_HEADROOM_SHARE = new BigDecimal("0.01");

        /** Полное покрытие защитой в процентах: сумма долей защитного набора шага. */
        public static final BigDecimal FULL_COVERAGE_PERCENTS = new BigDecimal("100");
    }

    /** Константы аудита. */
    @UtilityClass
    public class Audit {

        /** Системный принципал createdBy/modifiedBy до ввода аутентификации (шаг 9). */
        public static final String SYSTEM_PRINCIPAL = "system";
    }
}
