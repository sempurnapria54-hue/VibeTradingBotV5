package com.example.tradingbot.integration.service.okx;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.ClosePositionOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.SetLeverageOkxRequest;
import com.example.tradingbot.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.InstrumentOkxResponse;
import com.example.tradingbot.integration.model.okx.response.AlgoOrderOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OkxApiResponse;
import com.example.tradingbot.integration.model.okx.response.BalanceOkxResponse;
import com.example.tradingbot.integration.model.okx.response.AccountBillOkxResponse;
import com.example.tradingbot.integration.model.okx.response.PositionOkxResponse;
import com.example.tradingbot.integration.model.okx.response.PositionsHistoryOkxResponse;
import com.example.tradingbot.integration.model.okx.response.ServerTimeOkxResponse;
import com.example.tradingbot.integration.model.okx.response.TickerOkxResponse;
import com.example.tradingbot.integration.model.okx.response.TradeFeeOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OrderOkxResponse;
import com.example.tradingbot.integration.model.okx.response.SetLeverageOkxResponse;
import com.example.tradingbot.util.Constants;
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
    private final RestClient okxAuthRestClientHttp;

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
                          Boolean signed, ParameterizedTypeReference<R> responseType) {
        RestClient restClient = isTrue(signed) ? okxAuthRestClientHttp : okxRestClientHttp;
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
    public OkxApiResponse<InstrumentOkxResponse> getInstruments(String instType, String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_TYPE, instType);
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, Constants.Okx.INSTRUMENTS_PATH, query, null, false, INSTRUMENT_TYPE);
    }

    /** История свечей (пагинация назад): {@code after} — свечи строго старше ts (ms). */
    public OkxApiResponse<List<String>> getHistoryCandles(String instId, String bar, Long after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_BAR, bar);
        query.put(Constants.Okx.PARAM_AFTER, after);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, Constants.Okx.HISTORY_CANDLES_PATH, query, null, false, CANDLE_ARRAY_TYPE);
    }

    /**
     * История свечей индекса пары котировки: {@code after} — свечи строго
     * старше ts (ms). Носитель курса cross-ccy: свежий index-candles окно
     * в прошлом не обслуживает вовсе (наблюдение MG7.5). Строка —
     * [ts,o,h,l,c,confirm]. Публичный endpoint.
     */
    public OkxApiResponse<List<String>> getHistoryIndexCandles(String instId, String bar, Long after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_BAR, bar);
        query.put(Constants.Okx.PARAM_AFTER, after);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, Constants.Okx.HISTORY_INDEX_CANDLES_PATH, query, null, false,
                CANDLE_ARRAY_TYPE);
    }

    /** Последние свечи (докачка хвоста). */
    public OkxApiResponse<List<String>> getLatestCandles(String instId, String bar, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_BAR, bar);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, Constants.Okx.CANDLES_PATH, query, null, false, CANDLE_ARRAY_TYPE);
    }

    /** Тикер (рыночная цена: last/ask/bid + ts) инструмента: {@code instId} обязателен. Публичный endpoint. */
    public OkxApiResponse<TickerOkxResponse> getTicker(String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, Constants.Okx.MARKET_TICKER_PATH, query, null, false, TICKER_TYPE);
    }

    /** Live/pending ordinary orders по инструменту (звено evidence-cycle). */
    public OkxApiResponse<OrderOkxResponse> getPendingOrders(String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDERS_PENDING_PATH, query, null, true, ORDER_TYPE);
    }

    /** История ordinary orders по инструменту (7 дней; звено evidence-cycle). */
    public OkxApiResponse<OrderOkxResponse> getOrderHistory(String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDERS_HISTORY_PATH, query, null, true, ORDER_TYPE);
    }

    /** Live/pending algo orders по инструменту и ordType (звено evidence-cycle). */
    public OkxApiResponse<AlgoOrderOkxResponse> getPendingAlgoOrders(String instId, String ordType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_ORD_TYPE, ordType);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDERS_ALGO_PENDING_PATH, query, null, true, ALGO_ORDER_TYPE);
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
    public OkxApiResponse<AlgoOrderOkxResponse> getAlgoOrderHistory(String instId, String ordType,
                                                                    String algoId, String state) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_ORD_TYPE, ordType);
        query.put(Constants.Okx.PARAM_ALGO_ID, algoId);
        query.put(Constants.Okx.PARAM_STATE, state);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDERS_ALGO_HISTORY_PATH, query, null, true, ALGO_ORDER_TYPE);
    }

    /**
     * Ordinary order по {@code ordId} (предпочтительно) или
     * {@code clOrdId}. Приватный endpoint (подпись).
     */
    public OkxApiResponse<OrderOkxResponse> getOrder(String instId, String ordId, String clOrdId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_ORD_ID, ordId);
        query.put(Constants.Okx.PARAM_CL_ORD_ID, clOrdId);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDER_PATH, query, null, true, ORDER_TYPE);
    }

    /** Позиции аккаунта по инструменту. Приватный endpoint (подпись). */
    public OkxApiResponse<PositionOkxResponse> getPositions(String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_POSITIONS_PATH, query, null, true, POSITION_TYPE);
    }

    /**
     * Все позиции аккаунта контура одним запросом — сужение только по
     * типу инструмента. Приватный endpoint (подпись).
     */
    public OkxApiResponse<PositionOkxResponse> getAllPositions() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_TYPE, Constants.Okx.INST_TYPE_SWAP);
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_POSITIONS_PATH, query, null, true, POSITION_TYPE);
    }

    /**
     * История закрытых позиций инструмента. Окно снизу — по времени
     * обновления записи ({@code before}); сверху не задаётся. Фильтр по
     * идентификатору позиции не ставится: источник его переиспользует и
     * выборку им не сужает. Приватный endpoint (подпись).
     */
    public OkxApiResponse<PositionsHistoryOkxResponse> getPositionsHistory(String instId, String beforeMillis) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_TYPE, Constants.Okx.INST_TYPE_SWAP);
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_BEFORE, beforeMillis);
        query.put(Constants.Okx.PARAM_LIMIT, Constants.Okx.POSITIONS_HISTORY_PAGE_LIMIT);
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_POSITIONS_HISTORY_PATH, query, null, true,
                POSITIONS_HISTORY_TYPE);
    }

    /** Серверное время источника. Публичный endpoint (без подписи). */
    public OkxApiResponse<ServerTimeOkxResponse> getServerTime() {
        return dispatch(HttpMethod.GET, Constants.Okx.PUBLIC_TIME_PATH, null, null, false, SERVER_TIME_TYPE);
    }

    /** Постановка ordinary order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> placeOrder(PlaceOrderOkxRequest request) {
        return dispatch(HttpMethod.POST, Constants.Okx.TRADE_ORDER_PATH, null, request, true, ORDER_ACK_TYPE);
    }

    /** Отмена ordinary order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> cancelOrder(CancelOrderOkxRequest request) {
        return dispatch(HttpMethod.POST, Constants.Okx.TRADE_CANCEL_ORDER_PATH, null, request, true, ORDER_ACK_TYPE);
    }

    /** Закрытие позиции (market). Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> closePosition(ClosePositionOkxRequest request) {
        return dispatch(HttpMethod.POST, Constants.Okx.TRADE_CLOSE_POSITION_PATH, null, request, true, ORDER_ACK_TYPE);
    }

    /** Standalone algo-order по algoId (предпочтительно) или algoClOrdId. Приватный endpoint (подпись). */
    public OkxApiResponse<AlgoOrderOkxResponse> getAlgoOrder(String instId, String algoId, String algoClOrdId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_ALGO_ID, algoId);
        query.put(Constants.Okx.PARAM_ALGO_CL_ORD_ID, algoClOrdId);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDER_ALGO_PATH, query, null, true, ALGO_ORDER_TYPE);
    }

    /** Постановка standalone algo-order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<AlgoOrderAckOkxResponse> placeAlgoOrder(PlaceAlgoOrderOkxRequest request) {
        return dispatch(HttpMethod.POST, Constants.Okx.TRADE_ORDER_ALGO_PATH, null, request, true, ALGO_ACK_TYPE);
    }

    /** Отмена ordinary algo-семьи (conditional/oco/trigger). Тело — массив. Приватный endpoint. */
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAlgos(List<CancelAlgoOrderOkxRequest> requests) {
        return dispatch(HttpMethod.POST, Constants.Okx.TRADE_CANCEL_ALGOS_PATH, null, requests, true, ALGO_ACK_TYPE);
    }

    /** Отмена advance algo-семьи (trailing/move_order_stop). Тело — массив. Приватный endpoint. */
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAdvanceAlgos(List<CancelAlgoOrderOkxRequest> requests) {
        return dispatch(HttpMethod.POST, Constants.Okx.TRADE_CANCEL_ADVANCE_ALGOS_PATH, null, requests, true, ALGO_ACK_TYPE);
    }

    /**
     * Bill-записи движений счёта (7 дней): фильтр — тип инструментов и
     * окно времени; {@code after} — якорь пагинации по billId. Валюта в
     * запрос не идёт — фильтр по ней убил бы контроль чужой валюты
     * (docs/integrations/okx/contracts/account-bills.md). Приватный
     * endpoint.
     */
    public OkxApiResponse<AccountBillOkxResponse> getBills(String begin, String end, String after, Integer limit) {
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_BILLS_PATH,
                billsQuery(begin, end, after, limit), null, true, ACCOUNT_BILL_TYPE);
    }

    /** Архив bill-записей (3 месяца): те же оси запроса. Приватный endpoint. */
    public OkxApiResponse<AccountBillOkxResponse> getBillsArchive(String begin, String end, String after,
                                                                  Integer limit) {
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_BILLS_ARCHIVE_PATH,
                billsQuery(begin, end, after, limit), null, true, ACCOUNT_BILL_TYPE);
    }

    private Map<String, Object> billsQuery(String begin, String end, String after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_TYPE, Constants.Okx.INST_TYPE_SWAP);
        query.put(Constants.Okx.PARAM_BEGIN, begin);
        query.put(Constants.Okx.PARAM_END, end);
        query.put(Constants.Okx.PARAM_AFTER, after);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return query;
    }

    /** Выставление плеча инструмента (POST). Приватный endpoint (подпись). */
    public OkxApiResponse<SetLeverageOkxResponse> setLeverage(SetLeverageOkxRequest request) {
        return dispatch(HttpMethod.POST, Constants.Okx.ACCOUNT_SET_LEVERAGE_PATH, null, request, true,
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
    public OkxApiResponse<TradeFeeOkxResponse> getTradeFee(String instType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_TYPE, instType);
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_TRADE_FEE_PATH, query, null, true, TRADE_FEE_TYPE);
    }

    /** Баланс аккаунта (опционально по валюте). Приватный endpoint (подпись). */
    public OkxApiResponse<BalanceOkxResponse> getBalance(String ccy) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_CCY, ccy);
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_BALANCE_PATH, query, null, true, BALANCE_TYPE);
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
