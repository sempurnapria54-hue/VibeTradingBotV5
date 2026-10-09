package com.example.strategies.integration.internal.api;

import com.example.strategies.config.NeighbourProperties;
import com.example.strategies.integration.internal.api.model.PairCheckCoreResponse;
import com.example.strategies.integration.internal.api.model.RiskAppetiteCoreResponse;
import com.example.strategies.util.Constants;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Чтение у торгового ядра: принятые им числа риск-аппетита и разрешимость
 * ссылок определения (docs/architecture/contracts.md §«Синхронные
 * вызовы»).
 *
 * <p><b>Проекции этих величин у владельца определений не заводится.</b>
 * Числа — конфигурация окружения, и принимает её одно ядро; копия
 * потолка, устарев, ошибалась бы <b>в разрешающую</b> сторону —
 * пропускала бы стратегию, которую живой потолок отвергнет
 * (docs/rules/strategy-validation.md).
 *
 * <p><b>Вызов идёт ДО открытия транзакции перехода.</b> Сеть внутри
 * транзакции удерживала бы соединение пула на время вызова, а на отказе
 * соседа транзакция висела бы до таймаута
 * (docs/rules/strategy-validation.md §«Что проверяется на активации»).
 */
@Component
public class TradingCoreReadClient {

    private static final String PEER = "trading-core";

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;
    private final String clientRegistrationId;

    public TradingCoreReadClient(RestClient.Builder restClientBuilder,
                                 ServiceTokenProvider tokenProvider,
                                 NeighbourProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getTradingCore().getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.clientRegistrationId = properties.getTradingCore().getClientRegistrationId();
    }

    /**
     * Принятые ядром числа риск-аппетита — операнд трёх из пяти неравенств
     * создания. Тенанта в пути нет: числа одинаковы для всего окружения.
     */
    public RiskAppetiteCoreResponse getRiskAppetite() {
        return PeerCall.execute(PEER, "risk-appetite", () -> restClient.get()
                .uri("/api/v1/trading-core/risk-appetite")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(RiskAppetiteCoreResponse.class));
    }

    /** Разрешаются ли счёт и инструмент определения в контексте тенанта. */
    public PairCheckCoreResponse checkPair(String tenantInternalId, String exchangeAccountInternalId,
                                           String instrumentInternalId) {
        return PeerCall.execute(PEER, "pair-checks", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/trading-core/pair-checks")
                        .queryParam("tenantInternalId", tenantInternalId)
                        .queryParam("exchangeAccountInternalId", exchangeAccountInternalId)
                        .queryParam("instrumentInternalId", instrumentInternalId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(PairCheckCoreResponse.class));
    }

    private String bearer() {
        return Constants.Header.BEARER_PREFIX + tokenProvider.getTokenValue(clientRegistrationId);
    }
}
