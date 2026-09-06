package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.api.controller.MembershipController;
import com.example.auth.api.model.MembershipApiResponse;
import com.example.auth.config.IdentityProperties;
import com.example.auth.domain.model.Membership;
import com.example.auth.domain.service.MembershipResolutionService;
import com.example.auth.domain.service.TenantProvisioningService;
import com.example.auth.persistence.model.MembershipEntity;
import com.example.auth.persistence.repository.MembershipRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * Резолв контекста тенанта — вход периметра
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p>Проверяются три несущих свойства тропы: существующее членство
 * возвращается без заведения; отсутствие членств есть ЗАВЕДЕНИЕ, а не
 * отказ; служебная идентичность кластера тропы не проходит — иначе
 * тенант завёлся бы на каждый сервис.
 */
class MembershipResolutionTest {

    private static final String BROWSER_CLIENT = "web";
    private static final String USER = "user-42";

    @Test
    @DisplayName("Существующее членство возвращается, и заведения не происходит")
    void anExistingMembershipIsReturnedWithoutProvisioning() {
        MembershipRepository repository = mock(MembershipRepository.class);
        TenantProvisioningService provisioning = mock(TenantProvisioningService.class);
        when(repository.findAllByUserId(USER)).thenReturn(List.of(membership("tenant-7")));

        List<MembershipEntity> resolved =
                new MembershipResolutionService(repository, provisioning).resolveOrProvision(USER, USER);

        assertThat(resolved).hasSize(1);
        verify(provisioning, never()).provision(anyString(), anyString());
    }

    /**
     * Ноль членств — заведение, а не отказ: тропа заведения не должна
     * зависеть от того, первый это пользователь или нет
     * (docs/architecture/tenant-and-exchange.md §«Пользователи и роли»).
     */
    @Test
    @DisplayName("Ноль членств — заведение тенанта с членством владельца")
    void anAbsentMembershipProvisionsATenant() {
        MembershipRepository repository = mock(MembershipRepository.class);
        TenantProvisioningService provisioning = mock(TenantProvisioningService.class);
        when(repository.findAllByUserId(USER))
                .thenReturn(List.of())
                .thenReturn(List.of(membership("tenant-new")));
        when(provisioning.provision(anyString(), anyString())).thenReturn("tenant-new");

        List<MembershipEntity> resolved =
                new MembershipResolutionService(repository, provisioning).resolveOrProvision(USER, USER);

        assertThat(resolved).hasSize(1);
        verify(provisioning).provision(USER, USER);
    }

    /**
     * Служебная идентичность кластера тропу не проходит: она
     * предъявляется на межсервисных вызовах, где пользователя нет вовсе,
     * и заведение по ней создало бы тенанта на каждый сервис.
     */
    @Test
    @DisplayName("Токен служебного клиента тропу заведения не проходит")
    void aServiceAccountTokenIsRefused() {
        MembershipResolutionService resolution = mock(MembershipResolutionService.class);
        MembershipController controller = new MembershipController(resolution, identity(BROWSER_CLIENT));

        assertThatThrownBy(() -> controller.resolveSelf(tokenOf("trading-core")))
                .isInstanceOf(ResponseStatusException.class);
        verify(resolution, never()).resolveOrProvision(anyString(), anyString());
    }

    /**
     * Ненастроенная браузерная тропа отвергает любой токен: незаданное
     * есть отказ, а не разрешение.
     */
    @Test
    @DisplayName("Ненастроенный клиент браузера отвергает даже свой токен")
    void anUnconfiguredBrowserClientRefusesEveryToken() {
        MembershipResolutionService resolution = mock(MembershipResolutionService.class);
        MembershipController controller = new MembershipController(resolution, identity(""));

        assertThatThrownBy(() -> controller.resolveSelf(tokenOf(BROWSER_CLIENT)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Токен браузерного клиента доходит до резолва")
    void aBrowserTokenReachesTheResolution() {
        MembershipResolutionService resolution = mock(MembershipResolutionService.class);
        when(resolution.resolveOrProvision(anyString(), anyString()))
                .thenReturn(List.of(membership("tenant-7")));
        MembershipController controller = new MembershipController(resolution, identity(BROWSER_CLIENT));

        List<MembershipApiResponse> answer = controller.resolveSelf(tokenOf(BROWSER_CLIENT));

        assertThat(answer).singleElement()
                .satisfies(item -> assertThat(item.tenantId()).isEqualTo("tenant-7"));
    }

    private IdentityProperties identity(String browserClientId) {
        IdentityProperties properties = new IdentityProperties();
        properties.setBrowserClientId(browserClientId);
        return properties;
    }

    private Jwt tokenOf(String authorizedParty) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER)
                .claim("azp", authorizedParty)
                .claim("preferred_username", USER)
                .build();
    }

    private MembershipEntity membership(String tenantId) {
        MembershipEntity entity = new MembershipEntity();
        entity.setInternalId("m-1");
        entity.setUserId(USER);
        entity.setTenantId(tenantId);
        entity.setRole(Membership.Role.OWNER.name());
        return entity;
    }
}
