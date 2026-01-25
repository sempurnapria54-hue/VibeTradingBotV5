package com.example.tradingbot.client.okx;

import com.example.tradingbot.client.okx.dto.OkxAlgoOrderResultDto;
import com.example.tradingbot.client.okx.dto.OkxAmendOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxBalanceAccountDto;
import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCandleDto;
import com.example.tradingbot.client.okx.dto.OkxClosePositionRequest;
import com.example.tradingbot.client.okx.dto.OkxClosePositionResultDto;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCreateOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsArchiveRequest;
import com.example.tradingbot.client.okx.dto.OkxInstrumentDto;
import com.example.tradingbot.client.okx.dto.OkxOrderActionResultDto;
import com.example.tradingbot.client.okx.dto.OkxOrderDto;
import com.example.tradingbot.client.okx.dto.OkxPositionDto;
import com.example.tradingbot.client.okx.dto.OkxPriceTickerDto;
import com.example.tradingbot.client.okx.dto.OkxTradeFillDto;
import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveLinkDto;
import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveResultDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OkxRestClient {
    private final RestTemplate restTemplate;
    private final OkxAuthSigner authSigner;
    private final OkxConfig okxConfig;
    private final ObjectMapper objectMapper;

    public OkxRestClient(RestTemplate okxRestTemplate, OkxAuthSigner authSigner, OkxConfig okxConfig, ObjectMapper objectMapper) {
        this.restTemplate = okxRestTemplate;
        this.authSigner = authSigner;
        this.okxConfig = okxConfig;
        this.objectMapper = objectMapper;
    }

    public OkxApiResponse<OkxBalanceAccountDto> getBalance(String ccy) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (ccy != null) {
            params.add("ccy", ccy);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/account/balance",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxPositionDto> getPositions(String instType, String instId, String posId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (instType != null) {
            params.add("instType", instType);
        }
        if (instId != null) {
            params.add("instId", instId);
        }
        if (posId != null) {
            params.add("posId", posId);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/account/positions",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxOrderDto> getOrdersPending(String instType, String instFamily, String instId, String ordType,
                                                       String state, String after, String before, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (instType != null) {
            params.add("instType", instType);
        }
        if (instFamily != null) {
            params.add("instFamily", instFamily);
        }
        if (instId != null) {
            params.add("instId", instId);
        }
        if (ordType != null) {
            params.add("ordType", ordType);
        }
        if (state != null) {
            params.add("state", state);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/orders-pending",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxOrderDto> getOrderDetails(String instId, String ordId, String clOrdId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("instId", instId);
        if (ordId != null) {
            params.add("ordId", ordId);
        }
        if (clOrdId != null) {
            params.add("clOrdId", clOrdId);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/order",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxOrderDto> getOrdersHistory(String instType, String instId, String state, String after,
                                                       String before, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (instType != null) {
            params.add("instType", instType);
        }
        if (instId != null) {
            params.add("instId", instId);
        }
        if (state != null) {
            params.add("state", state);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/orders-history",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxOrderDto> getOrdersHistoryArchive(String instType, String instId, String state, String after,
                                                              String before, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (instType != null) {
            params.add("instType", instType);
        }
        if (instId != null) {
            params.add("instId", instId);
        }
        if (state != null) {
            params.add("state", state);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/orders-history-archive",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxTradeFillDto> getFills(String instType, String instId, String ordId, String after, String before,
                                                   String begin, String end, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (instType != null) {
            params.add("instType", instType);
        }
        if (instId != null) {
            params.add("instId", instId);
        }
        if (ordId != null) {
            params.add("ordId", ordId);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (begin != null) {
            params.add("begin", begin);
        }
        if (end != null) {
            params.add("end", end);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/fills",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxTradeFillDto> getFillsHistory(String instType, String instId, String ordId, String after,
                                                         String before, String begin, String end, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (instType != null) {
            params.add("instType", instType);
        }
        if (instId != null) {
            params.add("instId", instId);
        }
        if (ordId != null) {
            params.add("ordId", ordId);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (begin != null) {
            params.add("begin", begin);
        }
        if (end != null) {
            params.add("end", end);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/fills-history",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxTradeFillsArchiveResultDto> requestFillsArchive(OkxFillsArchiveRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/fills-archive",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxTradeFillsArchiveLinkDto> getFillsArchiveLink(String year, String quarter) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("year", year);
        params.add("quarter", quarter);
        return exchange(
                HttpMethod.GET,
                "/api/v5/trade/fills-archive",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxCandleDto> getCandles(String instId, String bar, String after, String before, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("instId", instId);
        if (bar != null) {
            params.add("bar", bar);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/market/candles",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                false
        );
    }

    public OkxApiResponse<OkxCandleDto> getHistoryCandles(String instId, String bar, String after, String before, String limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("instId", instId);
        if (bar != null) {
            params.add("bar", bar);
        }
        if (after != null) {
            params.add("after", after);
        }
        if (before != null) {
            params.add("before", before);
        }
        if (limit != null) {
            params.add("limit", limit);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/market/history-candles",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                false
        );
    }

    public OkxApiResponse<OkxOrderActionResultDto> createOrder(OkxCreateOrderRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/order",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxOrderActionResultDto> amendOrder(OkxAmendOrderRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/amend-order",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxOrderActionResultDto> cancelOrder(OkxCancelOrderRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/cancel-order",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxAlgoOrderResultDto> createAlgoOrder(OkxCreateAlgoOrderRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/order-algo",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxAlgoOrderResultDto> cancelAlgoOrder(OkxCancelAlgoOrderRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/cancel-algos",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxClosePositionResultDto> closePosition(OkxClosePositionRequest request) {
        return exchange(
                HttpMethod.POST,
                "/api/v5/trade/close-position",
                request,
                new LinkedMultiValueMap<>(),
                new ParameterizedTypeReference<>() {
                },
                true
        );
    }

    public OkxApiResponse<OkxInstrumentDto> getInstruments(String instType, String instId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("instType", instType);
        if (instId != null) {
            params.add("instId", instId);
        }
        return exchange(
                HttpMethod.GET,
                "/api/v5/public/instruments",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                false
        );
    }

    public OkxApiResponse<OkxPriceTickerDto> getTicker(String instId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("instId", instId);
        return exchange(
                HttpMethod.GET,
                "/api/v5/market/ticker",
                null,
                params,
                new ParameterizedTypeReference<>() {
                },
                false
        );
    }

    private <T> OkxApiResponse<T> exchange(HttpMethod method,
                                          String path,
                                          Object body,
                                          MultiValueMap<String, String> params,
                                          ParameterizedTypeReference<OkxApiResponse<T>> responseType,
                                          boolean privateRequest) {
        String requestPath = buildRequestPath(path, params);
        String url = buildUrl(path, params);
        String timestamp = Instant.now().toString();
        String bodyString = serializeBody(body);
        HttpHeaders headers = buildHeaders(privateRequest, timestamp, method, requestPath, bodyString);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<OkxApiResponse<T>> response = restTemplate.exchange(url, method, entity, responseType);
            return response.getBody();
        } catch (RestClientException ex) {
            throw ex;
        }
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize OKX request body", ex);
        }
    }

    private HttpHeaders buildHeaders(boolean privateRequest, String timestamp, HttpMethod method, String requestPath, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (privateRequest) {
            headers.add("OK-ACCESS-KEY", okxConfig.getApiKey());
            String signature = authSigner.sign(timestamp, method.name(), requestPath, body);
            headers.add("OK-ACCESS-SIGN", signature);
            headers.add("OK-ACCESS-TIMESTAMP", timestamp);
            headers.add("OK-ACCESS-PASSPHRASE", okxConfig.getPassphrase());
            if (okxConfig.isSimulatedTrading()) {
                headers.add("x-simulated-trading", "1");
            }
        }
        return headers;
    }

    private String buildRequestPath(String path, MultiValueMap<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path).queryParams(params);
        return builder.build(true).toUriString();
    }

    private String buildUrl(String path, MultiValueMap<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(okxConfig.getBaseUrl()).path(path).queryParams(params);
        return builder.build(true).toUriString();
    }
}
