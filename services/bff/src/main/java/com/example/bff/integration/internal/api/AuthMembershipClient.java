package com.example.bff.integration.internal.api;

import com.example.bff.integration.internal.api.model.MembershipApiModel;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Чтение членств предъявителя у владельца «кто есть кто»
 * (docs/architecture/contracts.md §«Синхронные вызовы», строка
 * {@code bff → auth}).
 *
 * <p><b>Вызов идёт под токеном ПОЛЬЗОВАТЕЛЯ, а не под служебной
 * идентичностью.</b> Резолв обязан отвечать про предъявителя, и
 * подставить его может только его же токен; служебная идентичность
 * сказала бы владельцу про сервис (docs/architecture/contracts.md
 * §«Контекст тенанта в вызове»).
 *
 * <p><b>Точка отвечает {@code POST}, потому что при первом предъявлении
 * ЗАВОДИТ тенанта</b> — тропа заведения не должна зависеть от того,
 * первый это пользователь или нет.
 */
@Slf4j
@Component
public class AuthMembershipClient {

    /** Имя единицы владельца «кто есть кто» в инвентаре сервисов. */
    private static final String OWNER = "auth";

    /** Точка резолва членств предъявителя. */
    private static final String RESOLVE_PATH = "/api/v1/auth/memberships/self";

    private final RestClient restClient;

    public AuthMembershipClient(RestClient.Builder builder, OwnerAddressResolver addressResolver) {
        this.restClient = builder.baseUrl(addressResolver.baseUrlOf(OWNER)).build();
    }

    /**
     * Членства предъявителя; при их отсутствии владелец заводит тенанта
     * с членством {@code OWNER} и возвращает его.
     *
     * @param bearerToken предъявленный токен пользователя, как есть
     * @return членства предъявителя
     */
    public List<MembershipApiModel> resolveSelf(String bearerToken) {
        try {
            return restClient.post()
                    .uri(RESOLVE_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MembershipApiModel>>() {
                    });
        } catch (ResourceAccessException failure) {
            log.error("The identity owner did not answer the membership resolution path={}", RESOLVE_PATH, failure);
            throw new PeerServiceUnavailableException("Владелец членств недоступен", failure);
        }
    }
}
