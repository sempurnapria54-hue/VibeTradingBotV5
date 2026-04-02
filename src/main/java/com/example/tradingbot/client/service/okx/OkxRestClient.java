package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.exception.ExternalApiException;
import com.example.tradingbot.client.exception.ExternalTransportException;
import com.example.tradingbot.client.model.okx.request.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.CandlesRequest;
import com.example.tradingbot.client.model.okx.request.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.request.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveLinkRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveRequest;
import com.example.tradingbot.client.model.okx.request.FillsRequest;
import com.example.tradingbot.client.model.okx.request.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.request.get.GetAlgoOrdersHistorySearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrderDetailsSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersAlgoPendingSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersHistoryArchiveSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersHistorySearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersPendingSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetPositionsSearchParams;
import com.example.tradingbot.client.model.okx.response.AlgoOrderResponse;
import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.client.model.okx.response.InstrumentResponse;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.model.okx.response.PriceTickerResponse;
import com.example.tradingbot.client.model.okx.response.TickerRequest;
import com.example.tradingbot.client.model.okx.response.TradeFillResponse;
import com.example.tradingbot.client.model.okx.response.TradeFillsArchiveResponse;
import com.example.tradingbot.client.model.okx.response.balance.BalanceResponse;
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

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@RequiredArgsConstructor
public class OkxRestClient {

    private final RestTemplate restTemplate;
    private final OkxConfig okxConfig;
    private final OkxAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final OkxRequestValidator requestValidator;

    /**
     * GET.
     */
    public OkxApiResponse<BalanceResponse> getBalances() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        return getPrivate("/api/v5/account/balance", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<PositionResponse> getPositions(GetPositionsSearchParams request) {
        return getPrivate("/api/v5/account/positions", positionsParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<PositionResponse> getAllPositions() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        return getPrivate("/api/v5/account/positions", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> getOrdersPending(GetOrdersPendingSearchParams request) {
        return getPrivate("/api/v5/trade/orders-pending", ordersPendingParams(request),
                          new ParameterizedTypeReference<>() {
                          });
    }

    public OkxApiResponse<OrderResponse> getOrderDetails(GetOrderDetailsSearchParams request) {
        return getPrivate("/api/v5/trade/order", orderDetailsParams(request), new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<OrderResponse> getOrdersHistory(GetOrdersHistorySearchParams request) {
        return getPrivate("/api/v5/trade/orders-history", ordersHistoryParams(request),
                          new ParameterizedTypeReference<>() {
                          });
    }

    public OkxApiResponse<OrderResponse> getOrdersHistoryArchive(GetOrdersHistoryArchiveSearchParams request) {
        return getPrivate("/api/v5/trade/orders-history-archive", ordersHistoryArchiveParams(request),
                          new ParameterizedTypeReference<>() {
                          });
    }


    public OkxApiResponse<AlgoOrderResponse> getOrdersAlgoPending(GetOrdersAlgoPendingSearchParams request) {
        return getPrivate("/api/v5/trade/orders-algo-pending", ordersAlgoPendingParams(request),
                          new ParameterizedTypeReference<>() {
                          });
    }

    public OkxApiResponse<AlgoOrderResponse> getOrderAlgoDetails(String algoOrderInternalId,
                                                                 String algoOrderExternalId) {
        if (isBlank(algoOrderInternalId) && isBlank(algoOrderExternalId)) {
            throw new IllegalArgumentException(
                    "Either algoOrderExternalId (algoId) or algoOrderInternalId (algoClOrdId) must be provided");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "algoId", algoOrderExternalId);
        addIfPresent(params, "algoClOrdId", algoOrderInternalId);

        return getPrivate("/api/v5/trade/order-algo", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<AlgoOrderResponse> getOrdersAlgoHistory(GetAlgoOrdersHistorySearchParams searchParams) {
        requestValidator.validate(searchParams);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "ordType", searchParams.getExternalAlgoOrderType());
        addIfPresent(params, "instId", searchParams.getInstrumentExternalId());
        addIfPresent(params, "instType", searchParams.getInstrumentExternalType());
        addIfPresent(params, "state", searchParams.getExternalStatus());
        addIfPresent(params, "after", searchParams.getAfterAlgoOrderExternalId());
        addIfPresent(params, "before", searchParams.getBeforeAlgoOrderExternalId());
        addIfPresent(params, "limit", searchParams.getLimit());
        addIfPresent(params, "algoId", searchParams.getAlgoOrderExternalId());

        return getPrivate("/api/v5/trade/orders-algo-history", params, new ParameterizedTypeReference<>() {
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
        Map<String, String> body = Collections.singletonMap("instType", request.getExternalInstrumentType());
        return postPrivate("/api/v5/trade/fills-archive", body, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<TradeFillsArchiveResponse> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getExternalInstrumentType());
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
        return postPrivate("/api/v5/trade/order-algo", createAlgoOrderBody(request),
                           new ParameterizedTypeReference<>() {
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
        return postPrivate("/api/v5/trade/close-position", closePositionBody(request),
                           new ParameterizedTypeReference<>() {
                           });
    }

    public OkxApiResponse<InstrumentResponse> getInstruments(InstrumentsRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getExternalType());
        addIfPresent(params, "instId", request.getExternalId());
        return getPublic("/api/v5/public/instruments", params, new ParameterizedTypeReference<>() {
        });
    }

    public OkxApiResponse<PriceTickerResponse> getTicker(TickerRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getExternalInstrumentId());
        return getPublic("/api/v5/market/ticker", params, new ParameterizedTypeReference<>() {
        });
    }

    private MultiValueMap<String, String> positionsParams(GetPositionsSearchParams request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentExternalId());
        addIfPresent(params, "instType", request.getInstrumentExternalType());
        return params;
    }

    private MultiValueMap<String, String> ordersPendingParams(GetOrdersPendingSearchParams request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentExternalId());
        addIfPresent(params, "instType", request.getInstrumentExternalType());
        addIfPresent(params, "state", request.getExternalStatus());
        addIfPresent(params, "ordType", request.getExternalType());
        addIfPresent(params, "after", request.getAfterOrderExternalId());
        addIfPresent(params, "before", request.getBeforeOrderExternalId());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> orderDetailsParams(GetOrderDetailsSearchParams request) {
        if (isNull(request)) {
            throw new IllegalArgumentException("GetOrderDetailsSearchParams is null");
        }

        if (isBlank(request.getInstrumentExternalId())) {
            throw new IllegalArgumentException("instId (instrumentExternalId) is required");
        }

        if (isBlank(request.getExternalId()) && isBlank(request.getInternalId())) {
            throw new IllegalArgumentException("ordId (externalId) or clOrdId (internalId) is required");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getInstrumentExternalId());
        addIfPresent(params, "ordId", request.getExternalId());
        addIfPresent(params, "clOrdId", request.getInternalId());
        return params;
    }

    private MultiValueMap<String, String> ordersAlgoPendingParams(GetOrdersAlgoPendingSearchParams request) {
        if (isNull(request)) {
            throw new IllegalArgumentException("GetOrdersAlgoPendingSearchParams is null");
        }
        if (isBlank(request.getAlgoOrderExternalType())) {
            throw new IllegalArgumentException("ordType (algoOrderExternalType) is required");
        }

        boolean hasAfter = isNotBlank(request.getAfterAlgoOrderExternalId());
        boolean hasBefore = isNotBlank(request.getBeforeAlgoOrderExternalId());

        if (hasAfter && hasBefore) {
            throw new IllegalArgumentException(
                    "Only one of afterAlgoOrderExternalId / beforeAlgoOrderExternalId must be set");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "ordType", request.getAlgoOrderExternalType());
        addIfPresent(params, "algoId", request.getAlgoOrderExternalId());
        addIfPresent(params, "instType", request.getInstrumentExternalType());
        addIfPresent(params, "instId", request.getInstrumentExternalId());
        addIfPresent(params, "after", request.getAfterAlgoOrderExternalId());
        addIfPresent(params, "before", request.getBeforeAlgoOrderExternalId());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> ordersHistoryParams(GetOrdersHistorySearchParams request) {
        if (isNull(request)) {
            throw new IllegalArgumentException("OrdersHistoryRequest is null");
        }
        if (isBlank(request.getInstrumentExternalType())) {
            throw new IllegalArgumentException("instType (instrumentExternalType) is required");
        }

        boolean hasBegin = isNotBlank(request.getBegin());
        boolean hasEnd = isNotBlank(request.getEnd());

        if (hasBegin && isFalse(hasEnd)) {
            throw new IllegalArgumentException("end is required when begin is set");
        }
        if (isFalse(hasBegin) && hasEnd) {
            throw new IllegalArgumentException("begin is required when end is set");
        }

        boolean hasAfter = isNotBlank(request.getAfterOrderExternalId());
        boolean hasBefore = isNotBlank(request.getBeforeOrderExternalId());

        if (hasAfter && hasBefore) {
            throw new IllegalArgumentException("Only one of afterExternalOrderId / beforeExternalOrderId must be set");
        }
        if ((hasAfter || hasBefore) && (hasBegin || hasEnd)) {
            throw new IllegalArgumentException("Use either after/before pagination OR begin/end time filter, not both");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getInstrumentExternalType());
        addIfPresent(params, "instFamily", request.getInstrumentExternalFamily());
        addIfPresent(params, "instId", request.getInstrumentExternalId());
        addIfPresent(params, "ordType", request.getExternalType());
        addIfPresent(params, "state", request.getExternalStatus());
        addIfPresent(params, "category", request.getExternalCategory());
        addIfPresent(params, "after", request.getAfterOrderExternalId());
        addIfPresent(params, "before", request.getBeforeOrderExternalId());
        addIfPresent(params, "begin", request.getBegin());
        addIfPresent(params, "end", request.getEnd());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> ordersHistoryArchiveParams(GetOrdersHistoryArchiveSearchParams request) {
        if (isNull(request)) {
            throw new IllegalArgumentException("OrdersHistoryRequest is null");
        }
        if (isBlank(request.getInstrumentExternalType())) {
            throw new IllegalArgumentException("instType (instrumentExternalType) is required");
        }

        boolean hasAfter = isNotBlank(request.getAfterOrderExternalId());
        boolean hasBefore = isNotBlank(request.getBeforeOrderExternalId());

        if (hasAfter && hasBefore) {
            throw new IllegalArgumentException("Only one of afterOrderExternalId / beforeOrderExternalId must be set");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getInstrumentExternalType());
        addIfPresent(params, "instId", request.getInstrumentExternalId());
        addIfPresent(params, "ordType", request.getExternalType());
        addIfPresent(params, "state", request.getExternalStatus());
        addIfPresent(params, "after", request.getAfterOrderExternalId());
        addIfPresent(params, "before", request.getBeforeOrderExternalId());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> fillsParams(FillsRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instType", request.getExternalInstrumentType());
        addIfPresent(params, "instId", request.getExternalInstrumentId());
        addIfPresent(params, "ordId", request.getExternalOrderId());
        addIfPresent(params, "after", request.getAfter());
        addIfPresent(params, "before", request.getBefore());
        addIfPresent(params, "limit", request.getLimit());
        return params;
    }

    private MultiValueMap<String, String> candlesParams(CandlesRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        addIfPresent(params, "instId", request.getExternalInstrumentId());
        addIfPresent(params, "bar", request.getExternalTimeframe());
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
        if (Objects.nonNull(value) && isFalse(value.isBlank())) {
            params.add(key, value);
        }
    }

    private void putIfPresent(Map<String, String> body, String key, String value) {
        if (Objects.nonNull(value) && isFalse(value.isBlank())) {
            body.put(key, value);
        }
    }


    private void putIfPresentObject(Map<String, Object> body, String key, String value) {
        if (Objects.nonNull(value) && isFalse(value.isBlank())) {
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
            throw new ExternalTransportException("Timeout or connection error for endpoint " + path,
                                                 HttpStatus.GATEWAY_TIMEOUT);
        } catch (HttpStatusCodeException exception) {
            throw new ExternalTransportException("HTTP error from OKX for endpoint " + path, HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateEnvelope(OkxApiResponse<?> envelope, String path) {
        if (isNull(envelope)) {
            throw new ExternalTransportException("Empty response from OKX for endpoint " + path,
                                                 HttpStatus.BAD_GATEWAY);
        }
        boolean success = BooleanUtils.isTrue(Objects.equals(envelope.getCode(), "0"));
        if (isFalse(success)) {
            throw new ExternalApiException(envelope.getCode(), envelope.getMsg(), HttpStatus.BAD_REQUEST);
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
        return UriComponentsBuilder.fromPath(path)
                                   .queryParams(params)
                                   .build()
                                   .toUriString();
    }
}
