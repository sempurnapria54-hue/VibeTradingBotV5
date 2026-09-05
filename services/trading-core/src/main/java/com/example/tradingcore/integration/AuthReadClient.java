package com.example.tradingcore.integration;

import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.model.ExchangeAccountAuthResponse;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Чтение реестра биржевых счетов у {@code auth}
 * (docs/architecture/contracts.md §«Синхронные вызовы»).
 *
 * <p><b>Ядро обязано знать, какие счета существуют,</b> иначе не начнёт ни
 * одного прохода; события {@code ExchangeAccountRegistered} производителя
 * пока не имеет, и синхронное чтение — единственная тропа
 * (docs/architecture/data-ownership.md §«Копии чужих данных»).
 *
 * <p><b>Ключей счёта здесь не бывает ни в каком виде.</b> Реестр отдаёт
 * идентичность и контур; подписывает запросы площадки коннектор, читая
 * ключи из Vault по пути, выводимому из идентичности счёта
 * (docs/architecture/tenant-and-exchange.md §Ключи).
 */
@Component
public class AuthReadClient {

    private static final String PEER = "auth";

    private static final ParameterizedTypeReference<List<ExchangeAccountAuthResponse>> ACCOUNT_LIST =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;
    private final String clientRegistrationId;

    public AuthReadClient(RestClient.Builder restClientBuilder,
                          ServiceTokenProvider tokenProvider,
                          NeighbourProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getAuth().getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.clientRegistrationId = properties.getAuth().getClientRegistrationId();
    }

    /** Реестр биржевых счетов целиком: тик синка сводит с ним проекцию. */
    public List<ExchangeAccountAuthResponse> getExchangeAccounts() {
        return PeerCall.execute(PEER, "exchange-accounts", () -> restClient.get()
                .uri("/api/v1/exchange-accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ACCOUNT_LIST));
    }

    private String bearer() {
        return "Bearer " + tokenProvider.getTokenValue(clientRegistrationId);
    }
}
