package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.model.okx.response.InstrumentResponse;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Низкоуровневый HTTP-клиент OKX: собирает запросы к публичным
 * REST-endpoint'ам шага 1 (instruments / candles / history-candles) и
 * возвращает сырые DTO источника. Подпись здесь не нужна — все
 * endpoint'ы публичные (docs/integrations/okx/contracts/*). Доменных
 * моделей не видит (codestyle: слои).
 */
@Component
@RequiredArgsConstructor
public class OkxRestClient {

    private static final String INSTRUMENTS_PATH = "/api/v5/public/instruments";
    private static final String CANDLES_PATH = "/api/v5/market/candles";
    private static final String HISTORY_CANDLES_PATH = "/api/v5/market/history-candles";

    private static final String PARAM_INST_TYPE = "instType";
    private static final String PARAM_INST_ID = "instId";
    private static final String PARAM_BAR = "bar";
    private static final String PARAM_AFTER = "after";
    private static final String PARAM_LIMIT = "limit";

    private static final ParameterizedTypeReference<OkxApiResponse<InstrumentResponse>> INSTRUMENT_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<OkxApiResponse<List<String>>> CANDLE_ARRAY_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient okxRestClientHttp;

    /** Спецификация инструментов: {@code instType} обязателен, {@code instId} опционален. */
    public OkxApiResponse<InstrumentResponse> getInstruments(String instType, String instId) {
        return okxRestClientHttp.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(INSTRUMENTS_PATH).queryParam(PARAM_INST_TYPE, instType);
                    if (StringUtils.isNotBlank(instId)) {
                        uriBuilder.queryParam(PARAM_INST_ID, instId);
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
                    uriBuilder.path(HISTORY_CANDLES_PATH)
                            .queryParam(PARAM_INST_ID, instId)
                            .queryParam(PARAM_BAR, bar);
                    if (Objects.nonNull(after)) {
                        uriBuilder.queryParam(PARAM_AFTER, after);
                    }
                    if (Objects.nonNull(limit)) {
                        uriBuilder.queryParam(PARAM_LIMIT, limit);
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
                    uriBuilder.path(CANDLES_PATH)
                            .queryParam(PARAM_INST_ID, instId)
                            .queryParam(PARAM_BAR, bar);
                    if (Objects.nonNull(limit)) {
                        uriBuilder.queryParam(PARAM_LIMIT, limit);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(CANDLE_ARRAY_TYPE);
    }
}
