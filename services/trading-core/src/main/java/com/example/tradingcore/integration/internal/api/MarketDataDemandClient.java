package com.example.tradingcore.integration.internal.api;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.internal.api.model.ComputationConfigResponse;
import com.example.tradingcore.util.Constants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Объявление потребности ядра владельцу рыночных данных
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»).
 *
 * <p><b>Требование — синхронная команда, а не событие и не конфигурация.</b>
 * Тропа единственная, что есть в контракте: потребителем определений
 * стратегий {@code market-data} не является и узнать о потребности иначе
 * ему неоткуда.
 *
 * <p><b>Все три команды идемпотентны по СОДЕРЖАНИЮ.</b> Повтор того же
 * требования возвращает ту же единицу сбора либо ту же идентичность, и
 * ровно поэтому тик вправе повторять их безнаказанно
 * (docs/rules/idempotency-via-unique.md).
 *
 * <p>Отзыва требования у владельца нет: снятая стратегия собранного не
 * удаляет.
 */
@Component
public class MarketDataDemandClient {

    private static final String PEER = "market-data";

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;
    private final String clientRegistrationId;

    public MarketDataDemandClient(RestClient.Builder restClientBuilder,
                                  ServiceTokenProvider tokenProvider,
                                  NeighbourProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getMarketData().getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.clientRegistrationId = properties.getMarketData().getClientRegistrationId();
    }

    /**
     * Требование свечей: инструмент, таймфрейм и глубина истории.
     *
     * @param depthBars сколько баров истории нужно; <b>пусто — вся
     *                  доступная история площадки</b>. Ядро подставляет
     *                  пустоту, когда стратегия глубины не объявила: вывод
     *                  прогрева из типа и периода принадлежит реализации
     *                  индикатора, то есть владельцу данных, и придумывать
     *                  число за него значило бы заказать МЕНЬШЕ нужного —
     *                  индикатор не посчитался бы вовсе.
     */
    public void requireCandles(String instrumentInternalId, TimeFrame timeframe, Long depthBars) {
        Map<String, Object> body = new HashMap<>();
        body.put("instrumentInternalId", instrumentInternalId);
        body.put("timeframe", timeframe);
        body.put("depthBars", depthBars);
        PeerCall.execute(PEER, "require-candles", () -> restClient.post()
                .uri("/api/v1/market-data/requirements/candles")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    /** Требование индикатора: возвращает идентичность вычисления. */
    public ComputationConfigResponse requireIndicator(String indicatorType, TimeFrame timeframe,
                                                      Map<String, Object> params) {
        Map<String, Object> body = new HashMap<>();
        body.put("indicatorType", indicatorType);
        body.put("timeframe", timeframe);
        body.put("params", params);
        return PeerCall.execute(PEER, "require-indicator", () -> restClient.post()
                .uri("/api/v1/market-data/requirements/indicators")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(body)
                .retrieve()
                .body(ComputationConfigResponse.class));
    }

    /**
     * Требование структуры рынка: возвращает идентичность вычисления.
     *
     * <p>Входы адресуются <b>идентичностями</b>, а не авторскими ключами:
     * ключ — номенклатура стратегии, и владелец данных о нём ничего не
     * знает. Пустой вход законен — резолвер работает и без него.
     */
    public ComputationConfigResponse requireMarketStructure(TimeFrame timeframe, Map<String, Object> params,
                                                            String efficiencyRatioConfigInternalId,
                                                            String atrConfigInternalId) {
        Map<String, Object> body = new HashMap<>();
        body.put("timeframe", timeframe);
        body.put("params", params);
        body.put("efficiencyRatioConfigInternalId", efficiencyRatioConfigInternalId);
        body.put("atrConfigInternalId", atrConfigInternalId);
        return PeerCall.execute(PEER, "require-market-structure", () -> restClient.post()
                .uri("/api/v1/market-data/requirements/market-structures")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(body)
                .retrieve()
                .body(ComputationConfigResponse.class));
    }

    private String bearer() {
        return Constants.Header.BEARER_PREFIX + tokenProvider.getTokenValue(clientRegistrationId);
    }
}
