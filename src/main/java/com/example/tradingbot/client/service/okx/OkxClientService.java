package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.client.model.okx.response.InstrumentResponse;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.client.service.ExchangeClientException;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.mapping.InstrumentMapper;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * OKX-реализация {@link ClientService}: ходит в публичные REST-endpoint'ы
 * через {@link OkxRestClient}, валидирует структуру/код ответа и отдаёт
 * нормализованные снапшоты (docs/components/ClientService.md,
 * docs/rules/raw-exchange-dto-boundary.md). Сырой OKX DTO наружу не
 * выходит.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OkxClientService implements ClientService {

    private static final String OKX_SUCCESS_CODE = "0";

    private final OkxRestClient okxRestClient;
    private final InstrumentMapper instrumentMapper;
    private final CandleMapper candleMapper;

    @Override
    public InstrumentExternalSnapshot getInstrument(String externalInstrumentId, String externalInstrumentType) {
        OkxApiResponse<InstrumentResponse> response = execute(
                () -> okxRestClient.getInstruments(externalInstrumentType, externalInstrumentId),
                "instruments", "instId=" + externalInstrumentId + " instType=" + externalInstrumentType);
        verifyCode(response, "instruments", "instId=" + externalInstrumentId);
        if (CollectionUtils.isEmpty(response.getData())) {
            return null;
        }
        InstrumentResponse first = response.getData().getFirst();
        return instrumentMapper.clientToSnapshot(first);
    }

    @Override
    public List<CandleExternalSnapshot> getHistoryCandles(String externalInstrumentId, String externalBar,
                                                          Long afterMillis, Integer limit) {
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getHistoryCandles(externalInstrumentId, externalBar, afterMillis, limit),
                "history-candles", "instId=" + externalInstrumentId + " bar=" + externalBar);
        verifyCode(response, "history-candles", "instId=" + externalInstrumentId);
        return toCandleSnapshots(response.getData());
    }

    @Override
    public List<CandleExternalSnapshot> getLatestCandles(String externalInstrumentId, String externalBar,
                                                         Integer limit) {
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getLatestCandles(externalInstrumentId, externalBar, limit),
                "candles", "instId=" + externalInstrumentId + " bar=" + externalBar);
        verifyCode(response, "candles", "instId=" + externalInstrumentId);
        return toCandleSnapshots(response.getData());
    }

    private List<CandleExternalSnapshot> toCandleSnapshots(List<List<String>> data) {
        if (CollectionUtils.isEmpty(data)) {
            return List.of();
        }
        return data.stream()
                .map(CandleResponse::of)
                .map(candleMapper::clientToSnapshot)
                .toList();
    }

    private <T> T execute(Supplier<T> call, String endpoint, String context) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.error("OKX transport error [{}] {}", endpoint, context, e);
            throw new ExchangeClientException("OKX transport error [" + endpoint + "] " + context, e);
        }
    }

    private void verifyCode(OkxApiResponse<?> response, String endpoint, String context) {
        if (Objects.isNull(response)) {
            log.error("OKX null response [{}] {}", endpoint, context);
            throw new ExchangeClientException("OKX null response [" + endpoint + "] " + context);
        }
        boolean success = Objects.equals(OKX_SUCCESS_CODE, response.getCode());
        if (BooleanUtils.isFalse(success)) {
            log.error("OKX error [{}] {} code={} msg={}", endpoint, context, response.getCode(), response.getMsg());
            throw new ExchangeClientException("OKX error [" + endpoint + "] code=" + response.getCode()
                    + " msg=" + response.getMsg());
        }
    }
}
