package com.example.tradingbot.domain.service.candles.okx;

import com.example.tradingbot.client.service.okx.OkxRestClient;
import com.example.tradingbot.client.model.okx.request.CandlesRequest;
import com.example.tradingbot.util.OkxTimeframes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OkxCandleFetcher {

    private final OkxRestClient okxRestClient;
    private final OkxCandleDataParser okxCandleDataParser;

    public List<ClientCandle> fetchTail(String instId, String timeframe, int bars) {
        return fetchHistoryBackward(instId, timeframe, bars, null);
    }

    public List<ClientCandle> fetchHistoryBackward(String instId,
                                                   String timeframe,
                                                   int limit,
                                                   Long afterTsExclusive) {
        validateInput(instId, timeframe, limit);

        CandlesRequest request = new CandlesRequest();
        request.setInstrumentId(instId);
        request.setBar(timeframe);
        request.setLimit(String.valueOf(limit));
        if (afterTsExclusive != null) {
            request.setAfter(String.valueOf(afterTsExclusive));
        }

        return okxCandleDataParser.parse(okxRestClient.getHistoryCandles(request).getData());
    }

    private void validateInput(String instId, String timeframe, int limit) {
        if (instId == null || instId.isBlank()) {
            throw new IllegalArgumentException("instId must not be blank");
        }
        if (!OkxTimeframes.isSupported(timeframe)) {
            throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("bars/limit must be greater than zero");
        }
    }
}
