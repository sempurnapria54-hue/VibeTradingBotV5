package com.example.tradingbot.client.okx;

import com.example.tradingbot.client.model.okx.AlgoOrderResponse;
import com.example.tradingbot.client.model.okx.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.BalanceResponse;
import com.example.tradingbot.client.model.okx.BalanceRequest;
import com.example.tradingbot.client.model.okx.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.CandleResponse;
import com.example.tradingbot.client.model.okx.CandlesRequest;
import com.example.tradingbot.client.model.okx.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.FillsArchiveLinkRequest;
import com.example.tradingbot.client.model.okx.FillsArchiveRequest;
import com.example.tradingbot.client.model.okx.FillsRequest;
import com.example.tradingbot.client.model.okx.InstrumentResponse;
import com.example.tradingbot.client.model.okx.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.OkxApiResponse;
import com.example.tradingbot.client.model.okx.OrderDetailsRequest;
import com.example.tradingbot.client.model.okx.OrderResponse;
import com.example.tradingbot.client.model.okx.OrdersAlgoPendingRequest;
import com.example.tradingbot.client.model.okx.OrdersHistoryRequest;
import com.example.tradingbot.client.model.okx.OrdersPendingRequest;
import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.client.model.okx.PositionsRequest;
import com.example.tradingbot.client.model.okx.PriceTickerResponse;
import com.example.tradingbot.client.model.okx.TickerRequest;
import com.example.tradingbot.client.model.okx.TradeFillResponse;
import com.example.tradingbot.client.model.okx.TradeFillsArchiveResponse;
import com.example.tradingbot.config.OkxConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OkxRestClient {

    private final RestTemplate restTemplate;
    private final OkxConfig okxConfig;
    private final OkxAuthSigner signer;
    private final ObjectMapper objectMapper;

    public OkxApiResponse<BalanceResponse> getBalance(BalanceRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "ccy", request.getCurrency());
        return getPrivate("/api/v5/account/balance", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<PositionResponse> getPositions(PositionsRequest request) {
        return getPrivate("/api/v5/account/positions", positionsParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> getOrdersPending(OrdersPendingRequest request) {
        return getPrivate("/api/v5/trade/orders-pending", ordersPendingParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> getOrderDetails(OrderDetailsRequest request) {
        return getPrivate("/api/v5/trade/order", orderDetailsParams(request), new ParameterizedTypeReference<>() {
        });
    }


    public OkxApiResponse<AlgoOrderResponse> getOrdersAlgoPending(OrdersAlgoPendingRequest request) {
        return getPrivate("/api/v5/trade/orders-algo-pending", ordersAlgoPendingParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> getOrdersHistory(OrdersHistoryRequest request) {
        return getPrivate("/api/v5/trade/orders-history", ordersHistoryParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> getOrdersHistoryArchive(OrdersHistoryRequest request) {
        return getPrivate("/api/v5/trade/orders-history-archive", ordersHistoryParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<TradeFillResponse> getFills(FillsRequest request) {
        return getPrivate("/api/v5/trade/fills", fillsParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<TradeFillResponse> getFillsHistory(FillsRequest request) {
        return getPrivate("/api/v5/trade/fills-history", fillsParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<TradeFillsArchiveResponse> requestFillsArchive(FillsArchiveRequest request) {
        Map<String, String> body = Collections.singletonMap("instType", request.getInstrumentType());
        return postPrivate("/api/v5/trade/fills-archive", body, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<TradeFillsArchiveResponse> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getInstrumentType());
        return getPrivate("/api/v5/trade/fills-archive", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<CandleResponse> getCandles(CandlesRequest request) {
        return getPublic("/api/v5/market/candles", candlesParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<CandleResponse> getHistoryCandles(CandlesRequest request) {
        return getPublic("/api/v5/market/history-candles", candlesParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> createOrder(CreateOrderRequest request) {
        return postPrivate("/api/v5/trade/order", createOrderBody(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> amendOrder(AmendOrderRequest request) {
        return postPrivate("/api/v5/trade/amend-order", amendOrderBody(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> cancelOrder(CancelOrderRequest request) {
        return postPrivate("/api/v5/trade/cancel-order", cancelOrderBody(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<AlgoOrderResponse> createAlgoOrder(CreateAlgoOrderRequest request) {
        return postPrivate("/api/v5/trade/order-algo", createAlgoOrderBody(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<AlgoOrderResponse> cancelAlgoOrder(CancelAlgoOrderRequest request) {
        Map<String, Object> algoOrder = new java.util.LinkedHashMap<>();
        putIfPresentObject(algoOrder, "instId", request.getInstrumentId());
        putIfPresentObject(algoOrder, "algoId", request.getAlgoOrderId());
        putIfPresentObject(algoOrder, "algoClOrdId", request.getClientOrderId());

        Map<String, Object> body = Collections.singletonMap("algoOrders", List.of(algoOrder));
        return postPrivate("/api/v5/trade/cancel-algos", body, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<PositionResponse> closePosition(ClosePositionRequest request) {
        return postPrivate("/api/v5/trade/close-position", closePositionBody(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<InstrumentResponse> getInstruments(InstrumentsRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getInstrumentType());
        addIfPresent(params, "instId", request.getInstrumentId());
        return getPublic("/api/v5/public/instruments", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<PriceTickerResponse> getTicker(TickerRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentId());
        return getPublic("/api/v5/market/ticker", params, new ParameterizedTypeReference<>() {
        });
    }

    private MultiValueMap<String, String> positionsParams(PositionsRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "instType", request.getInstrumentType());
        return params;
    }

    private MultiValueMap<String, String> ordersPendingParams(OrdersPendingRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "instType", request.getInstrumentType());
        return params;
    }

    private MultiValueMap<String, String> orderDetailsParams(OrderDetailsRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "ordId", request.getOrderId());
        addIfPresent(params, "clOrdId", request.getClientOrderId());
        return params;
    }


    private MultiValueMap<String, String> ordersAlgoPendingParams(OrdersAlgoPendingRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "ordType", request.getOrderType());
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "instType", request.getInstrumentType());
        return params;
    }

    private MultiValueMap<String, String> ordersHistoryParams(OrdersHistoryRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getInstrumentType());
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "state", request.getState());
        addIfPresent(params, "after", request.getAfter());
        addIfPresent(params, "before", request.getBefore());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> fillsParams(FillsRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getInstrumentType());
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "ordId", request.getOrderId());
        addIfPresent(params, "after", request.getAfter());
        addIfPresent(params, "before", request.getBefore());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> candlesParams(CandlesRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentId());
        addIfPresent(params, "bar", request.getBar());
        addIfPresent(params, "after", request.getAfter());
        addIfPresent(params, "before", request.getBefore());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private Map<String, String> createOrderBody(CreateOrderRequest request) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        putIfPresent(body, "instId", request.getInstrumentId());
        putIfPresent(body, "tdMode", request.getTradeMode());
        putIfPresent(body, "side", request.getSide());
        putIfPresent(body, "posSide", request.getPositionSide());
        putIfPresent(body, "ordType", request.getOrderType());
        putIfPresent(body, "sz", request.getSize());
        putIfPresent(body, "px", request.getPrice());
        putIfPresent(body, "clOrdId", request.getClientOrderId());
        return body;
    }

    private Map<String, String> amendOrderBody(AmendOrderRequest request) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        putIfPresent(body, "instId", request.getInstrumentId());
        putIfPresent(body, "ordId", request.getOrderId());
        putIfPresent(body, "clOrdId", request.getClientOrderId());
        putIfPresent(body, "newSz", request.getNewSize());
        putIfPresent(body, "newPx", request.getNewPrice());
        return body;
    }

    private Map<String, String> cancelOrderBody(CancelOrderRequest request) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        putIfPresent(body, "instId", request.getInstrumentId());
        putIfPresent(body, "ordId", request.getOrderId());
        putIfPresent(body, "clOrdId", request.getClientOrderId());
        return body;
    }

    private Map<String, String> createAlgoOrderBody(CreateAlgoOrderRequest request) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        putIfPresent(body, "instId", request.getInstrumentId());
        putIfPresent(body, "tdMode", request.getTradeMode());
        putIfPresent(body, "side", request.getSide());
        putIfPresent(body, "posSide", request.getPositionSide());
        putIfPresent(body, "ordType", request.getOrderType());
        putIfPresent(body, "sz", request.getSize());
        putIfPresent(body, "triggerPx", request.getTriggerPrice());
        putIfPresent(body, "orderPx", request.getOrderPrice());
        putIfPresent(body, "algoClOrdId", request.getClientOrderId());
        return body;
    }

    private Map<String, String> closePositionBody(ClosePositionRequest request) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        putIfPresent(body, "instId", request.getInstrumentId());
        putIfPresent(body, "mgnMode", request.getMarginMode());
        putIfPresent(body, "posSide", request.getPositionSide());
        putIfPresent(body, "ccy", request.getCurrency());
        putIfPresent(body, "autoCxl", request.getAutoCancel());
        return body;
    }

    private void addIfPresent(MultiValueMap<String, String> params, String key, String value) {
        if (Objects.nonNull(value) && BooleanUtils.isFalse(value.isBlank())) {
            params.add(key, value);
        }
    }

    private void putIfPresent(Map<String, String> body, String key, String value) {
        if (Objects.nonNull(value) && BooleanUtils.isFalse(value.isBlank())) {
            body.put(key, value);
        }
    }


    private void putIfPresentObject(Map<String, Object> body, String key, String value) {
        if (Objects.nonNull(value) && BooleanUtils.isFalse(value.isBlank())) {
            body.put(key, value);
        }
    }

    private <T> OkxApiResponse<T> getPrivate(String path,
                                          MultiValueMap<String, String> params,
                                          ParameterizedTypeReference<OkxApiResponse<T>> typeReference) {
        return exchange(path, HttpMethod.GET, params, null, true, typeReference);
    }

    private <T> OkxApiResponse<T> getPublic(String path,
                                         MultiValueMap<String, String> params,
                                         ParameterizedTypeReference<OkxApiResponse<T>> typeReference) {
        return exchange(path, HttpMethod.GET, params, null, false, typeReference);
    }

    private <T> OkxApiResponse<T> postPrivate(String path,
                                           Object bodyObject,
                                           ParameterizedTypeReference<OkxApiResponse<T>> typeReference) {
        return exchange(path, HttpMethod.POST, new LinkedMultiValueMap<>(), bodyObject, true, typeReference);
    }

    private <T> OkxApiResponse<T> exchange(String path,
                                        HttpMethod method,
                                        MultiValueMap<String, String> params,
                                        Object bodyObject,
                                        boolean signed,
                                        ParameterizedTypeReference<OkxApiResponse<T>> typeReference) {
        String body = "";
        if (Objects.nonNull(bodyObject)) {
            body = toJson(bodyObject);
        }
        String uriPathWithQuery = buildPathWithQuery(path, params);
        String fullUri = okxConfig.getBaseUrl() + uriPathWithQuery;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signed) {
            headers.addAll(signer.buildHeaders(method, uriPathWithQuery, body));
        }
        String requestBody = null;
        if (Objects.nonNull(bodyObject)) {
            requestBody = body;
        }
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<OkxApiResponse<T>> response = restTemplate.exchange(fullUri, method, entity, typeReference);
            OkxApiResponse<T> envelope = response.getBody();
            validateEnvelope(envelope, path);
            return envelope;
        } catch (ResourceAccessException exception) {
            throw new OkxTransportException("Timeout or connection error for endpoint " + path, HttpStatus.GATEWAY_TIMEOUT);
        } catch (HttpStatusCodeException exception) {
            throw new OkxTransportException("HTTP error from OKX for endpoint " + path, HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateEnvelope(OkxApiResponse<?> envelope, String path) {
        if (Objects.isNull(envelope)) {
            throw new OkxTransportException("Empty response from OKX for endpoint " + path, HttpStatus.BAD_GATEWAY);
        }
        boolean success = BooleanUtils.isTrue(Objects.equals(envelope.getCode(), "0"));
        if (BooleanUtils.isFalse(success)) {
            throw new OkxApiException(envelope.getCode(), envelope.getMsg(), HttpStatus.BAD_REQUEST);
        }
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize request", exception);
        }
    }

    private String buildPathWithQuery(String path, MultiValueMap<String, String> params) {
        return UriComponentsBuilder.fromPath(path).queryParams(params).build().toUriString();
    }
}
