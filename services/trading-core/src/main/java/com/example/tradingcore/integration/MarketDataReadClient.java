package com.example.tradingcore.integration;

import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.model.InstrumentMarketDataResponse;
import com.example.tradingcore.integration.model.MarketFeatureBundleResponse;
import com.example.tradingcore.integration.model.MarketFeatureReadRequest;
import com.example.tradingcore.util.Constants;
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
 *
 * <p><b>Фичи момента читаются ОДНИМ вызовом, а не по операнду.</b> Оценке
 * условий и расчёту параметров нужны сразу все входы; россыпь чтений
 * собрала бы контекст из значений РАЗНЫХ моментов — пока идёт обход имён,
 * тик расчёта у владельца успевает записать новое значение, и условие
 * сравнило бы величины, не существовавшие одновременно.
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

    /**
     * Фичи на момент решения: значения по привязкам, их предыдущие
     * значения, структуры, цены и — если переданы клаузы — фаза.
     *
     * <p><b>Предыдущее значение — операнд, а не удобство.</b> На нём стои́т
     * весь класс правил пересечения и объёмный фильтр; читатель, у
     * которого его нет, вычислил бы такое правило на пустом операнде и
     * получил бы ЛОЖЬ — то есть шаг стратегии не исполнился бы молча
     * (docs/components/StrategyConditionEvaluator.md).
     */
    public MarketFeatureBundleResponse readFeatures(String instrumentInternalId,
                                                    MarketFeatureReadRequest request) {
        return PeerCall.execute(PEER, "features", () -> restClient.post()
                .uri("/api/v1/market-data/instruments/{internalId}/features", instrumentInternalId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(request)
                .retrieve()
                .body(MarketFeatureBundleResponse.class));
    }

    private String bearer() {
        return Constants.Header.BEARER_PREFIX + tokenProvider.getTokenValue(clientRegistrationId);
    }
}
