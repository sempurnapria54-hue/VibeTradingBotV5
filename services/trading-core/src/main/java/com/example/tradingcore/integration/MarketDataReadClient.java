package com.example.tradingcore.integration;

import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.model.InstrumentMarketDataResponse;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Чтение каталога инструментов у {@code market-data}
 * (docs/architecture/contracts.md §«Синхронные вызовы»).
 *
 * <p><b>Листинг читается целиком, и это названное ограничение.</b>
 * Поверхность владельца пагинации не имеет, а строить окно без курсора на
 * стороне читателя нельзя; объём ограничен предметом — действующий
 * листинг одной площадки. Условие пересмотра — вторая площадка (фаза 3)
 * (docs/models/domain/core/Instrument.md §«Проекция у торгового ядра»).
 *
 * <p><b>Правила отдаются ПОИНСТРУМЕНТНО, и второго способа у владельца
 * нет.</b> Навес может быть ещё не материализован, и владелец отвечает
 * тогда пустым телом: {@code null} здесь означает «правил ещё нет», а не
 * «правила пусты» — разница видна писателю проекции.
 */
@Component
public class MarketDataReadClient {

    private static final String PEER = "market-data";

    private static final ParameterizedTypeReference<List<InstrumentMarketDataResponse>> INSTRUMENT_LIST =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;
    private final String clientRegistrationId;

    public MarketDataReadClient(RestClient.Builder restClientBuilder,
                                ServiceTokenProvider tokenProvider,
                                NeighbourProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getMarketData().getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.clientRegistrationId = properties.getMarketData().getClientRegistrationId();
    }

    /** Действующий листинг каталога. */
    public List<InstrumentMarketDataResponse> getInstruments() {
        return PeerCall.execute(PEER, "instruments", () -> restClient.get()
                .uri("/api/v1/market-data/instruments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(INSTRUMENT_LIST));
    }

    /** Справочные правила инструмента; пусто — навес ещё не собран. */
    public InstrumentExternalRules getInstrumentRules(String internalId) {
        return PeerCall.execute(PEER, "instrument-rules", () -> restClient.get()
                .uri("/api/v1/market-data/instruments/{internalId}/rules", internalId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(InstrumentExternalRules.class));
    }

    private String bearer() {
        return "Bearer " + tokenProvider.getTokenValue(clientRegistrationId);
    }
}
