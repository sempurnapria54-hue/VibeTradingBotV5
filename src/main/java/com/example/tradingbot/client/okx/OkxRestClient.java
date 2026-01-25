package com.example.tradingbot.client.okx;

import com.example.tradingbot.client.okx.dto.OkxAmendOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxAmendOrderResult;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxBalance;
import com.example.tradingbot.client.okx.dto.OkxBalanceRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderResult;
import com.example.tradingbot.client.okx.dto.OkxCancelOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelOrderResult;
import com.example.tradingbot.client.okx.dto.OkxCandlesRequest;
import com.example.tradingbot.client.okx.dto.OkxClosePositionRequest;
import com.example.tradingbot.client.okx.dto.OkxClosePositionResult;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderResult;
import com.example.tradingbot.client.okx.dto.OkxCreateOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCreateOrderResult;
import com.example.tradingbot.client.okx.dto.OkxFillsArchiveQueryRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsArchiveRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsHistoryRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsRequest;
import com.example.tradingbot.client.okx.dto.OkxHistoryCandlesRequest;
import com.example.tradingbot.client.okx.dto.OkxInstrument;
import com.example.tradingbot.client.okx.dto.OkxInstrumentsRequest;
import com.example.tradingbot.client.okx.dto.OkxOrder;
import com.example.tradingbot.client.okx.dto.OkxOrderDetailsRequest;
import com.example.tradingbot.client.okx.dto.OkxOrdersHistoryArchiveRequest;
import com.example.tradingbot.client.okx.dto.OkxOrdersHistoryRequest;
import com.example.tradingbot.client.okx.dto.OkxOrdersPendingRequest;
import com.example.tradingbot.client.okx.dto.OkxPosition;
import com.example.tradingbot.client.okx.dto.OkxPositionsRequest;
import com.example.tradingbot.client.okx.dto.OkxPriceTicker;
import com.example.tradingbot.client.okx.dto.OkxTickerRequest;
import com.example.tradingbot.client.okx.dto.OkxTradeFill;
import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveResult;
import com.example.tradingbot.client.okx.dto.OkxCandle;
import com.example.tradingbot.config.OkxConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OkxRestClient {

    private final RestTemplate restTemplate;
    private final OkxAuthSigner authSigner;
    private final OkxConfig okxConfig;
    private final ObjectMapper objectMapper;

    public OkxApiResponse<OkxBalance> getBalance(OkxBalanceRequest request) {
        String path = buildPath("/api/v5/account/balance", mapOf("ccy", request.getCcy()));
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxPosition> getPositions(OkxPositionsRequest request) {
        String path = buildPath("/api/v5/account/positions", mapOf("instId", request.getInstId()));
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxOrder> getOrdersPending(OkxOrdersPendingRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instType", request.getInstType());
        params.put("instFamily", request.getInstFamily());
        params.put("instId", request.getInstId());
        params.put("ordType", request.getOrdType());
        params.put("state", request.getState());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("limit", request.getLimit());
        String path = buildPath("/api/v5/trade/orders-pending", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxOrder> getOrderDetails(OkxOrderDetailsRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instId", request.getInstId());
        params.put("ordId", request.getOrdId());
        params.put("clOrdId", request.getClOrdId());
        String path = buildPath("/api/v5/trade/order", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxOrder> getOrdersHistory(OkxOrdersHistoryRequest request) {
        String path = buildPath("/api/v5/trade/orders-history", orderHistoryParams(request));
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxOrder> getOrdersHistoryArchive(OkxOrdersHistoryArchiveRequest request) {
        String path = buildPath("/api/v5/trade/orders-history-archive", orderHistoryArchiveParams(request));
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxTradeFill> getFills(OkxFillsRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instType", request.getInstType());
        params.put("instId", request.getInstId());
        params.put("ordId", request.getOrdId());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("limit", request.getLimit());
        String path = buildPath("/api/v5/trade/fills", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxTradeFill> getFillsHistory(OkxFillsHistoryRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instType", request.getInstType());
        params.put("instId", request.getInstId());
        params.put("ordId", request.getOrdId());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("begin", request.getBegin());
        params.put("end", request.getEnd());
        params.put("limit", request.getLimit());
        String path = buildPath("/api/v5/trade/fills-history", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxTradeFillsArchiveResult> requestFillsArchive(OkxFillsArchiveRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/fills-archive", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxTradeFillsArchiveResult> getFillsArchive(OkxFillsArchiveQueryRequest request) {
        String path = buildPath("/api/v5/trade/fills-archive", mapOf("year", request.getYear(), "quarter", request.getQuarter()));
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxCandle> getCandles(OkxCandlesRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instId", request.getInstId());
        params.put("bar", request.getBar());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("limit", request.getLimit());
        String path = buildPath("/api/v5/market/candles", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, false);
    }

    public OkxApiResponse<OkxCandle> getHistoryCandles(OkxHistoryCandlesRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instId", request.getInstId());
        params.put("bar", request.getBar());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("limit", request.getLimit());
        String path = buildPath("/api/v5/market/history-candles", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, false);
    }

    public OkxApiResponse<OkxCreateOrderResult> createOrder(OkxCreateOrderRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/order", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxAmendOrderResult> amendOrder(OkxAmendOrderRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/amend-order", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxCancelOrderResult> cancelOrder(OkxCancelOrderRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/cancel-order", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxCreateAlgoOrderResult> createAlgoOrder(OkxCreateAlgoOrderRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/order-algo", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxCancelAlgoOrderResult> cancelAlgoOrder(OkxCancelAlgoOrderRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/cancel-algos", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxClosePositionResult> closePosition(OkxClosePositionRequest request) {
        return execute(HttpMethod.POST, "/api/v5/trade/close-position", request, new ParameterizedTypeReference<>() { }, true);
    }

    public OkxApiResponse<OkxInstrument> getInstruments(OkxInstrumentsRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instType", request.getInstType());
        params.put("instId", request.getInstId());
        params.put("instFamily", request.getInstFamily());
        String path = buildPath("/api/v5/public/instruments", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, false);
    }

    public OkxApiResponse<OkxPriceTicker> getTicker(OkxTickerRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instId", request.getInstId());
        params.put("instType", request.getInstType());
        String path = buildPath("/api/v5/market/ticker", params);
        return execute(HttpMethod.GET, path, null, new ParameterizedTypeReference<>() { }, false);
    }

    private Map<String, String> orderHistoryParams(OkxOrdersHistoryRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instType", request.getInstType());
        params.put("instFamily", request.getInstFamily());
        params.put("instId", request.getInstId());
        params.put("ordType", request.getOrdType());
        params.put("state", request.getState());
        params.put("category", request.getCategory());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("begin", request.getBegin());
        params.put("end", request.getEnd());
        params.put("limit", request.getLimit());
        return params;
    }

    private Map<String, String> orderHistoryArchiveParams(OkxOrdersHistoryArchiveRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("instType", request.getInstType());
        params.put("instFamily", request.getInstFamily());
        params.put("instId", request.getInstId());
        params.put("ordType", request.getOrdType());
        params.put("state", request.getState());
        params.put("category", request.getCategory());
        params.put("after", request.getAfter());
        params.put("before", request.getBefore());
        params.put("begin", request.getBegin());
        params.put("end", request.getEnd());
        params.put("limit", request.getLimit());
        return params;
    }

    private Map<String, String> mapOf(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private Map<String, String> mapOf(String key1, String value1, String key2, String value2) {
        Map<String, String> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private String buildPath(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.queryParam(key, value);
            }
        });
        return builder.build(false).toUriString();
    }

    private <T> OkxApiResponse<T> execute(
        HttpMethod method,
        String requestPath,
        Object body,
        ParameterizedTypeReference<OkxApiResponse<T>> responseType,
        boolean signed
    ) {
        String bodyString = bodyToString(body);
        HttpHeaders headers = buildHeaders(method, requestPath, bodyString, signed);
        HttpEntity<String> entity = new HttpEntity<>(bodyString.isBlank() ? null : bodyString, headers);
        String url = okxConfig.getBaseUrl() + requestPath;
        try {
            ResponseEntity<OkxApiResponse<T>> response = restTemplate.exchange(url, method, entity, responseType);
            OkxApiResponse<T> responseBody = response.getBody();
            if (responseBody == null) {
                throw new OkxTransportException("OKX response body is empty", response.getStatusCode(), null);
            }
            if (!"0".equals(responseBody.getCode())) {
                throw new OkxApiException(responseBody.getCode(), responseBody.getMsg());
            }
            return responseBody;
        } catch (HttpStatusCodeException exception) {
            throw new OkxTransportException("OKX HTTP error", exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new OkxTransportException("OKX request timeout", null, exception);
        }
    }

    private HttpHeaders buildHeaders(HttpMethod method, String requestPath, String body, boolean signed) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (okxConfig.isSimulatedTrading()) {
            headers.add("x-simulated-trading", "1");
        }
        if (signed) {
            String timestamp = Instant.now().toString();
            String signature = authSigner.sign(timestamp, method.name(), requestPath, body);
            headers.add("OK-ACCESS-KEY", okxConfig.getApiKey());
            headers.add("OK-ACCESS-SIGN", signature);
            headers.add("OK-ACCESS-TIMESTAMP", timestamp);
            headers.add("OK-ACCESS-PASSPHRASE", okxConfig.getPassphrase());
        }
        return headers;
    }

    private String bodyToString(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize OKX request body", exception);
        }
    }
}
