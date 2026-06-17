package com.example.tradingbot.integration.service.okx;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.ClosePositionOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
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
import com.example.tradingbot.util.Constants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Низкоуровневый HTTP-клиент OKX: публичные endpoint'ы (instruments /
 * candles / ticker) через {@code okxRestClientHttp}; приватные (trade / account)
 * через подписанный {@code okxAuthRestClientHttp}
 * ({@link OkxSigningInterceptor}). Возвращает сырые DTO источника;
 * доменных моделей не видит (codestyle: слои).
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

    private final RestClient okxRestClientHttp;
    private final RestClient okxAuthRestClientHttp;

    /** Спецификация инструментов: {@code instType} обязателен, {@code instId} опционален. */
    public OkxApiResponse<InstrumentOkxResponse> getInstruments(String instType, String instId) {
        return okxRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.INSTRUMENTS_PATH).queryParam(Constants.Okx.PARAM_INST_TYPE, instType);
                    if (isNotBlank(instId)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_INST_ID, instId);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(INSTRUMENT_TYPE);
    }

    /** История свечей (пагинация назад): {@code after} — свечи строго старше ts (ms). */
    public OkxApiResponse<List<String>> getHistoryCandles(String instId, String bar, Long after, Integer limit) {
        return okxRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.HISTORY_CANDLES_PATH)
                            .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                            .queryParam(Constants.Okx.PARAM_BAR, bar);
                    if (nonNull(after)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_AFTER, after);
                    }
                    if (nonNull(limit)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_LIMIT, limit);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(CANDLE_ARRAY_TYPE);
    }

    /** Последние свечи (докачка хвоста). */
    public OkxApiResponse<List<String>> getLatestCandles(String instId, String bar, Integer limit) {
        return okxRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.CANDLES_PATH)
                            .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                            .queryParam(Constants.Okx.PARAM_BAR, bar);
                    if (nonNull(limit)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_LIMIT, limit);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(CANDLE_ARRAY_TYPE);
    }

    /** Тикер (рыночная цена: last/ask/bid + ts) инструмента: {@code instId} обязателен. Публичный endpoint. */
    public OkxApiResponse<OkxTickerResponse> getTicker(String instId) {
        return okxRestClientHttp.get()
                .uri(uriBuilder -> uriBuilder.path(Constants.Okx.MARKET_TICKER_PATH)
                        .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                        .build())
                .retrieve()
                .body(TICKER_TYPE);
    }

    /** Live/pending ordinary orders по инструменту (звено evidence-cycle). */
    public OkxApiResponse<OrderOkxResponse> getPendingOrders(String instId) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> uriBuilder.path(Constants.Okx.TRADE_ORDERS_PENDING_PATH)
                        .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                        .build())
                .retrieve()
                .body(ORDER_TYPE);
    }

    /** История ordinary orders по инструменту (7 дней; звено evidence-cycle). */
    public OkxApiResponse<OrderOkxResponse> getOrderHistory(String instId) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> uriBuilder.path(Constants.Okx.TRADE_ORDERS_HISTORY_PATH)
                        .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                        .build())
                .retrieve()
                .body(ORDER_TYPE);
    }

    /** Live/pending algo orders по инструменту и ordType (звено evidence-cycle). */
    public OkxApiResponse<OkxAlgoOrderResponse> getPendingAlgoOrders(String instId, String ordType) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> uriBuilder.path(Constants.Okx.TRADE_ORDERS_ALGO_PENDING_PATH)
                        .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                        .queryParam(Constants.Okx.PARAM_ORD_TYPE, ordType)
                        .build())
                .retrieve()
                .body(ALGO_ORDER_TYPE);
    }

    /** История algo orders по инструменту и ordType (звено evidence-cycle). */
    public OkxApiResponse<OkxAlgoOrderResponse> getAlgoOrderHistory(String instId, String ordType) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> uriBuilder.path(Constants.Okx.TRADE_ORDERS_ALGO_HISTORY_PATH)
                        .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                        .queryParam(Constants.Okx.PARAM_ORD_TYPE, ordType)
                        .build())
                .retrieve()
                .body(ALGO_ORDER_TYPE);
    }

    /** История исполнений (fills, 3 месяца): {@code after} — якорь по billId. Приватный endpoint. */
    public OkxApiResponse<OkxFillResponse> getFillsHistory(String instId, String after, Integer limit) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.TRADE_FILLS_HISTORY_PATH)
                            .queryParam(Constants.Okx.PARAM_INST_ID, instId);
                    if (isNotBlank(after)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_AFTER, after);
                    }
                    if (nonNull(limit)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_LIMIT, limit);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(FILL_TYPE);
    }

    /**
     * Ordinary order по {@code ordId} (предпочтительно) или
     * {@code clOrdId}. Приватный endpoint (подпись).
     */
    public OkxApiResponse<OrderOkxResponse> getOrder(String instId, String ordId, String clOrdId) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.TRADE_ORDER_PATH).queryParam(Constants.Okx.PARAM_INST_ID, instId);
                    if (isNotBlank(ordId)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_ORD_ID, ordId);
                    }
                    if (isNotBlank(clOrdId)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_CL_ORD_ID, clOrdId);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(ORDER_TYPE);
    }

    /** Позиции аккаунта по инструменту. Приватный endpoint (подпись). */
    public OkxApiResponse<OkxPositionResponse> getPositions(String instId) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> uriBuilder.path(Constants.Okx.ACCOUNT_POSITIONS_PATH)
                        .queryParam(Constants.Okx.PARAM_INST_ID, instId)
                        .build())
                .retrieve()
                .body(POSITION_TYPE);
    }

    /** Постановка ordinary order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> placeOrder(PlaceOrderOkxRequest request) {
        return okxAuthRestClientHttp.post()
                .uri(Constants.Okx.TRADE_ORDER_PATH)
                .body(request)
                .retrieve()
                .body(ORDER_ACK_TYPE);
    }

    /** Отмена ordinary order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> cancelOrder(CancelOrderOkxRequest request) {
        return okxAuthRestClientHttp.post()
                .uri(Constants.Okx.TRADE_CANCEL_ORDER_PATH)
                .body(request)
                .retrieve()
                .body(ORDER_ACK_TYPE);
    }

    /** Закрытие позиции (market). Приватный endpoint (подпись POST). */
    public OkxApiResponse<OrderAckOkxResponse> closePosition(ClosePositionOkxRequest request) {
        return okxAuthRestClientHttp.post()
                .uri(Constants.Okx.TRADE_CLOSE_POSITION_PATH)
                .body(request)
                .retrieve()
                .body(ORDER_ACK_TYPE);
    }

    /** Standalone algo-order по algoId (предпочтительно) или algoClOrdId. Приватный endpoint (подпись). */
    public OkxApiResponse<OkxAlgoOrderResponse> getAlgoOrder(String instId, String algoId, String algoClOrdId) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.TRADE_ORDER_ALGO_PATH)
                            .queryParam(Constants.Okx.PARAM_INST_ID, instId);
                    if (isNotBlank(algoId)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_ALGO_ID, algoId);
                    }
                    if (isNotBlank(algoClOrdId)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_ALGO_CL_ORD_ID, algoClOrdId);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(ALGO_ORDER_TYPE);
    }

    /** Постановка standalone algo-order. Приватный endpoint (подпись POST). */
    public OkxApiResponse<AlgoOrderAckOkxResponse> placeAlgoOrder(PlaceAlgoOrderOkxRequest request) {
        return okxAuthRestClientHttp.post()
                .uri(Constants.Okx.TRADE_ORDER_ALGO_PATH)
                .body(request)
                .retrieve()
                .body(ALGO_ACK_TYPE);
    }

    /** Отмена ordinary algo-семьи (conditional/oco/trigger). Тело — массив. Приватный endpoint. */
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAlgos(List<CancelAlgoOrderOkxRequest> requests) {
        return okxAuthRestClientHttp.post()
                .uri(Constants.Okx.TRADE_CANCEL_ALGOS_PATH)
                .body(requests)
                .retrieve()
                .body(ALGO_ACK_TYPE);
    }

    /** Отмена advance algo-семьи (trailing/move_order_stop). Тело — массив. Приватный endpoint. */
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAdvanceAlgos(List<CancelAlgoOrderOkxRequest> requests) {
        return okxAuthRestClientHttp.post()
                .uri(Constants.Okx.TRADE_CANCEL_ADVANCE_ALGOS_PATH)
                .body(requests)
                .retrieve()
                .body(ALGO_ACK_TYPE);
    }

    /** Исполнения (fills, последние 3 дня): {@code after} — якорь по billId. Приватный endpoint. */
    public OkxApiResponse<OkxFillResponse> getFills(String instId, String after, Integer limit) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.TRADE_FILLS_PATH)
                            .queryParam(Constants.Okx.PARAM_INST_ID, instId);
                    if (isNotBlank(after)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_AFTER, after);
                    }
                    if (nonNull(limit)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_LIMIT, limit);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(FILL_TYPE);
    }

    /**
     * Диагностическое чтение конфигурации аккаунта (acctLv/posMode) —
     * сырое тело. Под разовую диагностику F3b (метода/DTO под этот
     * endpoint в клиентском слое нет — surface-gap). Read-only,
     * приватный endpoint (подпись).
     */
    public String getAccountConfig() {
        return okxAuthRestClientHttp.get()
                .uri(Constants.Okx.ACCOUNT_CONFIG_PATH)
                .retrieve()
                .body(String.class);
    }

    /** Баланс аккаунта (опционально по валюте). Приватный endpoint (подпись). */
    public OkxApiResponse<OkxBalanceResponse> getBalance(String ccy) {
        return okxAuthRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(Constants.Okx.ACCOUNT_BALANCE_PATH);
                    if (isNotBlank(ccy)) {
                        uriBuilder.queryParam(Constants.Okx.PARAM_CCY, ccy);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(BALANCE_TYPE);
    }
}
