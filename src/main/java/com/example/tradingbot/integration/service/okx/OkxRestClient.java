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
import com.example.tradingbot.integration.model.okx.response.OkxAlgoOrderResponse;
import com.example.tradingbot.integration.model.okx.response.OkxApiResponse;
import com.example.tradingbot.integration.model.okx.response.OkxBalanceResponse;
import com.example.tradingbot.integration.model.okx.response.OkxFillResponse;
import com.example.tradingbot.integration.model.okx.response.OkxPositionResponse;
import com.example.tradingbot.integration.model.okx.response.OkxTickerResponse;
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

    private static final ParameterizedTypeReference<OkxApiResponse<InstrumentOkxResponse>> INSTRUMENT_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<List<String>>> CANDLE_ARRAY_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OkxTickerResponse>> TICKER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OrderOkxResponse>> ORDER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OkxPositionResponse>> POSITION_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OrderAckOkxResponse>> ORDER_ACK_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OkxBalanceResponse>> BALANCE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OkxFillResponse>> FILL_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<OkxAlgoOrderResponse>> ALGO_ORDER_TYPE =
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

    /** Последние свечи (докачка хвоста). */
    public OkxApiResponse<List<String>> getLatestCandles(String instId, String bar, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_BAR, bar);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, Constants.Okx.CANDLES_PATH, query, null, false, CANDLE_ARRAY_TYPE);
    }

    /** Тикер (рыночная цена: last/ask/bid + ts) инструмента: {@code instId} обязателен. Публичный endpoint. */
    public OkxApiResponse<OkxTickerResponse> getTicker(String instId) {
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
    public OkxApiResponse<OkxAlgoOrderResponse> getPendingAlgoOrders(String instId, String ordType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_ORD_TYPE, ordType);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDERS_ALGO_PENDING_PATH, query, null, true, ALGO_ORDER_TYPE);
    }

    /** История algo orders по инструменту и ordType (звено evidence-cycle). */
    public OkxApiResponse<OkxAlgoOrderResponse> getAlgoOrderHistory(String instId, String ordType) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_ORD_TYPE, ordType);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_ORDERS_ALGO_HISTORY_PATH, query, null, true, ALGO_ORDER_TYPE);
    }

    /** История исполнений (fills, 3 месяца): {@code after} — якорь по billId. Приватный endpoint. */
    public OkxApiResponse<OkxFillResponse> getFillsHistory(String instId, String after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_AFTER, after);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_FILLS_HISTORY_PATH, query, null, true, FILL_TYPE);
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
    public OkxApiResponse<OkxPositionResponse> getPositions(String instId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        return dispatch(HttpMethod.GET, Constants.Okx.ACCOUNT_POSITIONS_PATH, query, null, true, POSITION_TYPE);
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
    public OkxApiResponse<OkxAlgoOrderResponse> getAlgoOrder(String instId, String algoId, String algoClOrdId) {
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

    /** Исполнения (fills, последние 3 дня): {@code after} — якорь по billId. Приватный endpoint. */
    public OkxApiResponse<OkxFillResponse> getFills(String instId, String after, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(Constants.Okx.PARAM_INST_ID, instId);
        query.put(Constants.Okx.PARAM_AFTER, after);
        query.put(Constants.Okx.PARAM_LIMIT, limit);
        return dispatch(HttpMethod.GET, Constants.Okx.TRADE_FILLS_PATH, query, null, true, FILL_TYPE);
    }

    /** Выставление плеча инструмента (POST). Приватный endpoint (подпись). */
    public OkxApiResponse<SetLeverageOkxResponse> setLeverage(SetLeverageOkxRequest request) {
        return dispatch(HttpMethod.POST, Constants.Okx.ACCOUNT_SET_LEVERAGE_PATH, null, request, true,
                SET_LEVERAGE_TYPE);
    }

    /** Баланс аккаунта (опционально по валюте). Приватный endpoint (подпись). */
    public OkxApiResponse<OkxBalanceResponse> getBalance(String ccy) {
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
