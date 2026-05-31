package com.example.tradingbot.integration.service.okx;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.integration.model.okx.response.InstrumentResponse;
import com.example.tradingbot.integration.model.okx.response.OkxApiResponse;
import com.example.tradingbot.util.Constants;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
}
