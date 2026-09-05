package com.example.connector.okx.integration.client;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.ClosePositionOkxRequest;
import com.example.connector.okx.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.SetLeverageOkxRequest;
import com.example.connector.okx.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.connector.okx.integration.model.okx.response.IndexTickerOkxResponse;
import com.example.connector.okx.integration.model.okx.response.InstrumentOkxResponse;
import com.example.connector.okx.integration.model.okx.response.MarkPriceOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderBookOkxResponse;
import com.example.connector.okx.integration.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OkxApiResponse;
import com.example.connector.okx.integration.model.okx.response.BalanceOkxResponse;
import com.example.connector.okx.integration.model.okx.response.AccountBillOkxResponse;
import com.example.connector.okx.integration.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.integration.model.okx.response.PositionsHistoryOkxResponse;
import com.example.connector.okx.integration.model.okx.response.ServerTimeOkxResponse;
import com.example.connector.okx.integration.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.integration.model.okx.response.TradeFeeOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderAckOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderOkxResponse;
import com.example.connector.okx.integration.model.okx.response.SetLeverageOkxResponse;
import com.example.connector.okx.util.OkxConstants;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Низкоуровневый HTTP-клиент OKX. Единая точка отправки — универсальный
 * {@link #dispatch}: маршрутизирует public ({@code okxRestClientHttp}) /
 * signed ({@code okxAuthRestClientHttp}, подпись —
 * {@link OkxSigningInterceptor}) по флагу, строит URI из path + query и
 * биндит ответ в переданный токен. Типизированные методы (instruments /
 * candles / ticker / trade / account) — тонкие обёртки над {@code dispatch}
 * для продукта; контур тестов ходит в {@code dispatch} напрямую через
 * generic-эндпоинт {@code /raw}. Возвращает сырые DTO источника; доменных
 * моделей не видит (codestyle: слои).
 */
@Component
@RequiredArgsConstructor
public class OkxRestClient {

    private static final ParameterizedTypeReference<OkxApiResponse<TradeFeeOkxResponse>> TRADE_FEE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<OkxApiResponse<InstrumentOkxResponse>> INSTRUMENT_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<List<String>>> CANDLE_ARRAY_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OrderBookOkxResponse>> ORDER_BOOK_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<MarkPriceOkxResponse>> MARK_PRICE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<IndexTickerOkxResponse>> INDEX_TICKER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<TickerOkxResponse>> TICKER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OrderOkxResponse>> ORDER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<PositionOkxResponse>> POSITION_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<PositionsHistoryOkxResponse>>
            POSITIONS_HISTORY_TYPE = new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<ServerTimeOkxResponse>> SERVER_TIME_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OrderAckOkxResponse>> ORDER_ACK_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<BalanceOkxResponse>> BALANCE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<AccountBillOkxResponse>> ACCOUNT_BILL_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<AlgoOrderOkxResponse>> ALGO_ORDER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<AlgoOrderAckOkxResponse>> ALGO_ACK_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<SetLeverageOkxResponse>> SET_LEVERAGE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient okxRestClientHttp;
    private final SignedRestClientFactory signedClientFactory;

    /**
     * Универсальная точка отправки запроса в OKX и общий core-dispatch
     * трёх потребителей: продукт (типизированные методы ниже делегируют
     * сюда), код-тесты и Postman/коллекция (через generic-эндпоинт
     * {@code /raw}). Выбирает public ({@code okxRestClientHttp}) или signed
     * ({@code okxAuthRestClientHttp}) RestClient по {@code signed} (подпись
     * и креды — {@link OkxSigningInterceptor}), строит URI из {@code path}
     * и {@code query} (null/blank-значения опускаются), для write-запросов
     * сериализует {@code body}, биндит ответ в {@code responseType}.
     * Токен ответа выбирает потребитель: {@code OkxApiResponse<*OkxResponse>}
     * (продукт), {@code OkxApiResponse<JsonNode>} (контур) или сырой
     * {@code String}.
     */
    public <R> R dispatch(HttpMethod method, String path, Map<String, ?> query, Object body,
                          ExchangeCredentials credentials, ParameterizedTypeReference<R> responseType) {
        RestClient restClient = isNull(credentials)
                ? okxRestClientHttp
                : signedClientFactory.forCredentials(credentials);
        RestClient.RequestBodySpec request = restClient.method(method)
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (nonNull(query)) {
                        query.forEach((name, value) -> {
                            if (isQueryable(value)) {
                                uriBuilder.queryParam(name, value);
                            }
                        });
                    }
                    return uriBuilder.build();
                });
        if (nonNull(body)) {
            request = request.body(body);
        }
        return request.retrieve().body(responseType);
    }

    /** Спецификация инструментов: {@code instType} обязателен, {@code instId} опционален. */
    /** Тикеры всего листинга одного типа инструмента: один запрос на срез. */
    public OkxApiResponse<TickerOkxResponse> getTickers(String instType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, instType);
        return dispatch(HttpMethod.GET, OkxConstants.MARKET_TICKERS_PATH, query, null, null, TICKER_TYPE);
    }

    /** Книга заявок инструмента на заданную глубину каждой стороны. */
    public OkxApiResponse<OrderBookOkxResponse> getOrderBook(String instId, Integer depth) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_SIZE, depth);
        return dispatch(HttpMethod.GET, OkxConstants.MARKET_BOOKS_PATH, query, null, null, ORDER_BOOK_TYPE);
    }

    /** Марк-цены всего листинга одного типа инструмента. */
    public OkxApiResponse<MarkPriceOkxResponse> getMarkPrices(String instType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, instType);
        return dispatch(HttpMethod.GET, OkxConstants.MARK_PRICE_PATH, query, null, null, MARK_PRICE_TYPE);
    }

    /** Цены индексов одной расчётной валюты. */
    public OkxApiResponse<IndexTickerOkxResponse> getIndexTickers(String quoteCurrency) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_QUOTE_CURRENCY, quoteCurrency);
        return dispatch(HttpMethod.GET, OkxConstants.INDEX_TICKERS_PATH, query, null, null, INDEX_TICKER_TYPE);
    }

    public OkxApiResponse<InstrumentOkxResponse> getInstruments(String instType, String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, instType);
        query.put(OkxConstants.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, OkxConstants.INSTRUMENTS_PATH, query, null, null, INSTRUMENT_TYPE);
    }

    /** История свечей (пагинация назад): {@code after} — свечи строго старше ts (ms). */
    public OkxApiResponse<List<String>> getHistoryCandles(String instId, String bar, Long after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_BAR, bar);
        query.put(OkxConstants.PARAM_AFTER, after);
        query.put(OkxConstants.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, OkxConstants.HISTORY_CANDLES_PATH, query, null, null, CANDLE_ARRAY_TYPE);
    }

    /**
     * История свечей индекса пары котировки: {@code after} — свечи строго
     * старше ts (ms). Носитель курса cross-ccy: свежий index-candles окно
     * в прошлом не обслуживает вовсе (наблюдение MG7.5). Строка —
     * [ts,o,h,l,c,confirm]. Публичный endpoint.
     */
    public OkxApiResponse<List<String>> getHistoryIndexCandles(String instId, String bar, Long after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_BAR, bar);
        query.put(OkxConstants.PARAM_AFTER, after);
        query.put(OkxConstants.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, OkxConstants.HISTORY_INDEX_CANDLES_PATH, query, null, null,
                CANDLE_ARRAY_TYPE);
    }

    /** Последние свечи (докачка хвоста). */
    public OkxApiResponse<List<String>> getLatestCandles(String instId, String bar, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_BAR, bar);
        query.put(OkxConstants.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, OkxConstants.CANDLES_PATH, query, null, null, CANDLE_ARRAY_TYPE);
    }

    /** Тикер (рыночная цена: last/ask/bid + ts) инструмента: {@code instId} обязателен. Публичный endpoint. */
    public OkxApiResponse<TickerOkxResponse> getTicker(String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, OkxConstants.MARKET_TICKER_PATH, query, null, null, TICKER_TYPE);
    }

    /** Live/pending ordinary orders по инструменту (звено evidence-cycle). */
    public OkxApiResponse<OrderOkxResponse> getPendingOrders(ExchangeCredentials credentials, String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDERS_PENDING_PATH, query, null, credentials, ORDER_TYPE);
    }

    /**
     * Счёт-широкий срез live/pending ordinary orders: лимит частоты у
     * эндпоинта по User ID, не по инструменту, поэтому один запрос
     * дешевле поштучного обхода (docs/components/AnomalyJob.md
     * §«Состояние носителя»). {@code limit} задан явно потолком страницы:
     * усечение обязано быть наблюдаемым, а не выглядеть полным срезом.
     */
    public OkxApiResponse<OrderOkxResponse> getAllPendingOrders(ExchangeCredentials credentials) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, OkxConstants.INST_TYPE_SWAP);
        query.put(OkxConstants.PARAM_LIMIT, OkxConstants.PENDING_PAGE_LIMIT);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDERS_PENDING_PATH, query, null, credentials, ORDER_TYPE);
    }

    /**
     * Счёт-широкий срез live/pending algo одной семьи. {@code ordType} у
     * этого эндпоинта обязателен, поэтому счёт-широкий срез algo
     * складывается из вызова на семью — в отличие от позиций и ordinary
     * orders, где хватает одного.
     */
    public OkxApiResponse<AlgoOrderOkxResponse> getAllPendingAlgoOrders(ExchangeCredentials credentials, String ordType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, OkxConstants.INST_TYPE_SWAP);
        query.put(OkxConstants.PARAM_ORD_TYPE, ordType);
        query.put(OkxConstants.PARAM_LIMIT, OkxConstants.PENDING_PAGE_LIMIT);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH, query, null, credentials,
                ALGO_ORDER_TYPE);
    }

    /** История ordinary orders по инструменту (7 дней; звено evidence-cycle). */
    public OkxApiResponse<OrderOkxResponse> getOrderHistory(ExchangeCredentials credentials, String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDERS_HISTORY_PATH, query, null, credentials, ORDER_TYPE);
    }

    /** Live/pending algo orders по инструменту и ordType (звено evidence-cycle). */
    public OkxApiResponse<AlgoOrderOkxResponse> getPendingAlgoOrders(ExchangeCredentials credentials, String instId,
                                                                     String ordType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_ORD_TYPE, ordType);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH, query, null, credentials, ALGO_ORDER_TYPE);
    }

    /**
     * История algo orders по инструменту и ordType (звено evidence-cycle).
     *
     * <p><b>Сверх ordType эндпоинт требует ОДИН из двух операндов</b> —
     * {@code algoId} либо {@code state}; без обоих отвечает
     * {@code code=50015} «Either parameter state or algoId is required»
     * (`docs/integrations/okx/contracts/algo-order.md`). Пустой из пары в
     * query не попадает.
     */
    public OkxApiResponse<AlgoOrderOkxResponse> getAlgoOrderHistory(ExchangeCredentials credentials, String instId, String ordType,
                                                                    String algoId, String state) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_ORD_TYPE, ordType);
        query.put(OkxConstants.PARAM_ALGO_ID, algoId);
        query.put(OkxConstants.PARAM_STATE, state);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, query, null, credentials, ALGO_ORDER_TYPE);
    }

    /**
     * Ordinary order по {@code ordId} (предпочтительно) или
     * {@code clOrdId}. Приватный endpoint (подпись).
     */
    public OkxApiResponse<OrderOkxResponse> getOrder(ExchangeCredentials credentials, String instId, String ordId,
                                                     String clOrdId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_ORD_ID, ordId);
        query.put(OkxConstants.PARAM_CL_ORD_ID, clOrdId);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDER_PATH, query, null, credentials, ORDER_TYPE);
    }

    /** Позиции аккаунта по инструменту. Приватный endpoint (подпись). */
    public OkxApiResponse<PositionOkxResponse> getPositions(ExchangeCredentials credentials, String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_POSITIONS_PATH, query, null, credentials, POSITION_TYPE);
    }

    /**
     * Все позиции аккаунта контура одним запросом — сужение только по
     * типу инструмента. Приватный endpoint (подпись).
     */
    public OkxApiResponse<PositionOkxResponse> getAllPositions(ExchangeCredentials credentials) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, OkxConstants.INST_TYPE_SWAP);
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_POSITIONS_PATH, query, null, credentials, POSITION_TYPE);
    }

    /**
     * История закрытых позиций инструмента. Окно снизу — по времени
     * обновления записи ({@code before}); сверху не задаётся. Фильтр по
     * идентификатору позиции не ставится: источник его переиспользует и
     * выборку им не сужает. Приватный endpoint (подпись).
     */
    public OkxApiResponse<PositionsHistoryOkxResponse> getPositionsHistory(ExchangeCredentials credentials,
                                                                           String instId, String beforeMillis) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, OkxConstants.INST_TYPE_SWAP);
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_BEFORE, beforeMillis);
        query.put(OkxConstants.PARAM_LIMIT, OkxConstants.POSITIONS_HISTORY_PAGE_LIMIT);
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH, query, null, credentials,
                POSITIONS_HISTORY_TYPE);
    }

    /** Серверное время источника. Публичный endpoint (без подписи). */
    public OkxApiResponse<ServerTimeOkxResponse> getServerTime() {
        return dispatch(HttpMethod.GET, OkxConstants.PUBLIC_TIME_PATH, null, null, null, SERVER_TIME_TYPE);
    }

    /** Постановка ordinary order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> placeOrder(ExchangeCredentials credentials, PlaceOrderOkxRequest request) {
        return dispatch(HttpMethod.POST, OkxConstants.TRADE_ORDER_PATH, null, request, credentials, ORDER_ACK_TYPE);
    }

    /** Отмена ordinary order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> cancelOrder(ExchangeCredentials credentials,
                                                           CancelOrderOkxRequest request) {
        return dispatch(HttpMethod.POST, OkxConstants.TRADE_CANCEL_ORDER_PATH, null, request, credentials, ORDER_ACK_TYPE);
    }

    /** Закрытие позиции (market). Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> closePosition(ExchangeCredentials credentials,
                                                             ClosePositionOkxRequest request) {
        return dispatch(HttpMethod.POST, OkxConstants.TRADE_CLOSE_POSITION_PATH, null, request, credentials, ORDER_ACK_TYPE);
    }

    /** Standalone algo-order по algoId (предпочтительно) или algoClOrdId. Приватный endpoint (подпись). */
    public OkxApiResponse<AlgoOrderOkxResponse> getAlgoOrder(ExchangeCredentials credentials, String instId,
                                                             String algoId, String algoClOrdId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_ID, instId);
        query.put(OkxConstants.PARAM_ALGO_ID, algoId);
        query.put(OkxConstants.PARAM_ALGO_CL_ORD_ID, algoClOrdId);
        return dispatch(HttpMethod.GET, OkxConstants.TRADE_ORDER_ALGO_PATH, query, null, credentials, ALGO_ORDER_TYPE);
    }

    /** Постановка standalone algo-order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<AlgoOrderAckOkxResponse> placeAlgoOrder(ExchangeCredentials credentials,
                                                                  PlaceAlgoOrderOkxRequest request) {
        return dispatch(HttpMethod.POST, OkxConstants.TRADE_ORDER_ALGO_PATH, null, request, credentials, ALGO_ACK_TYPE);
    }

    /** Отмена ordinary algo-семьи (conditional/oco/trigger). Тело — массив. Приватный endpoint. */
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAlgos(ExchangeCredentials credentials,
                                                               List<CancelAlgoOrderOkxRequest> requests) {
        return dispatch(HttpMethod.POST, OkxConstants.TRADE_CANCEL_ALGOS_PATH, null, requests, credentials, ALGO_ACK_TYPE);
    }

    /** Отмена advance algo-семьи (trailing/move_order_stop). Тело — массив. Приватный endpoint. */
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAdvanceAlgos(ExchangeCredentials credentials,
                                                                      List<CancelAlgoOrderOkxRequest> requests) {
        return dispatch(HttpMethod.POST, OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH, null, requests, credentials, ALGO_ACK_TYPE);
    }

    /**
     * Bill-записи движений счёта (7 дней): фильтр — тип инструментов и
     * окно времени; {@code after} — якорь пагинации по billId. Валюта в
     * запрос не идёт — фильтр по ней убил бы контроль чужой валюты
     * (docs/integrations/okx/contracts/account-bills.md). Приватный
     * endpoint.
     */
    public OkxApiResponse<AccountBillOkxResponse> getBills(ExchangeCredentials credentials, String begin, String end,
                                                           String after, Integer limit) {
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_BILLS_PATH,
                billsQuery(begin, end, after, limit), null, credentials, ACCOUNT_BILL_TYPE);
    }

    /** Архив bill-записей (3 месяца): те же оси запроса. Приватный endpoint. */
    public OkxApiResponse<AccountBillOkxResponse> getBillsArchive(ExchangeCredentials credentials, String begin, String end, String after,
                                                                  Integer limit) {
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_BILLS_ARCHIVE_PATH,
                billsQuery(begin, end, after, limit), null, credentials, ACCOUNT_BILL_TYPE);
    }

    private Map<String, Object> billsQuery(String begin, String end, String after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, OkxConstants.INST_TYPE_SWAP);
        query.put(OkxConstants.PARAM_BEGIN, begin);
        query.put(OkxConstants.PARAM_END, end);
        query.put(OkxConstants.PARAM_AFTER, after);
        query.put(OkxConstants.PARAM_LIMIT, limit);
        return query;
    }

    /** Выставление плеча инструмента (POST). Приватный endpoint (подпись). */
    public OkxApiResponse<SetLeverageOkxResponse> setLeverage(ExchangeCredentials credentials,
                                                              SetLeverageOkxRequest request) {
        return dispatch(HttpMethod.POST, OkxConstants.ACCOUNT_SET_LEVERAGE_PATH, null, request, credentials,
                SET_LEVERAGE_TYPE);
    }

    /**
     * Ставки комиссий счёта по типу инструмента. Приватный endpoint (подпись).
     *
     * <p>Ось запроса — <b>тип</b>, не инструмент: один вызов на тик отдаёт
     * группы всего типа, а {@code instId}/{@code instFamily} в запросе
     * вернули бы ставки с учётом market-maker incentive вместо organic base
     * rates (docs/integrations/okx/contracts/trade-fee.md).
     */
    public OkxApiResponse<TradeFeeOkxResponse> getTradeFee(ExchangeCredentials credentials, String instType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_INST_TYPE, instType);
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_TRADE_FEE_PATH, query, null, credentials, TRADE_FEE_TYPE);
    }

    /** Баланс аккаунта (опционально по валюте). Приватный endpoint (подпись). */
    public OkxApiResponse<BalanceOkxResponse> getBalance(ExchangeCredentials credentials, String ccy) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(OkxConstants.PARAM_CCY, ccy);
        return dispatch(HttpMethod.GET, OkxConstants.ACCOUNT_BALANCE_PATH, query, null, credentials, BALANCE_TYPE);
    }

    /** Значение пригодно для query-параметра: не null и (для строк) не blank. */
    private boolean isQueryable(Object value) {
        if (isNull(value)) {
            return false;
        }
        if (value instanceof String text) {
            return isNotBlank(text);
        }
        return true;
    }
}
