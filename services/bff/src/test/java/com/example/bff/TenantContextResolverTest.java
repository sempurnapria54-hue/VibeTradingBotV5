package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.MembershipCache;
import com.example.bff.domain.TenantContext;
import com.example.bff.domain.TenantContextResolver;
import com.example.bff.integration.AuthMembershipClient;
import com.example.bff.integration.MembershipApiModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Вывод контекста тенанта из членств предъявителя
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p>Проверяются все три ветви вывода и свойство кэша, ради которого он
 * заведён: годная запись владельца не переспрашивает, просроченная —
 * переспрашивает.
 */
class TenantContextResolverTest {

    private static final String SUBJECT = "user-42";
    private static final String BEARER = "Bearer token";

    @Test
    @DisplayName("Ровно одно членство — оно и есть контекст")
    void aSingleMembershipIsTheContext() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"));

        TenantContext context = resolverOver(client, Duration.ofMinutes(1)).resolve(SUBJECT, BEARER);

        assertThat(context.tenantId()).isEqualTo("tenant-7");
        assertThat(context.role()).isEqualTo("OWNER");
    }

    /**
     * Пустой ответ сюда не доходит: владелец заводит тенанта той же
     * точкой резолва. Ветвь остаётся охраной — молчаливого контекста
     * из ничего не бывает.
     */
    @Test
    @DisplayName("Ноль членств — отказ, а не выдуманный контекст")
    void anEmptyAnswerIsRefused() {
        AuthMembershipClient client = clientReturning();

        assertThatThrownBy(() -> resolverOver(client, Duration.ofMinutes(1)).resolve(SUBJECT, BEARER))
                .isInstanceOf(ResponseStatusException.class);
    }

    /**
     * Больше одного членства недостижимо до второго субъекта. Ветвь
     * отвечает отказом, а не выбирает произвольное: молчаливый выбор дал
     * бы контекст, о котором никто не решал.
     */
    @Test
    @DisplayName("Больше одного членства — отказ, а не произвольный выбор")
    void severalMembershipsAreRefusedRatherThanPicked() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"),
                new MembershipApiModel("m-2", "tenant-8", "TRADER"));

        assertThatThrownBy(() -> resolverOver(client, Duration.ofMinutes(1)).resolve(SUBJECT, BEARER))
                .isInstanceOf(ResponseStatusException.class);
    }

    /** Годная запись кэша владельца не переспрашивает — ради этого он и заведён. */
    @Test
    @DisplayName("Внутри срока годности владелец членств не переспрашивается")
    void afreshCacheEntryDoesNotAskTheOwnerAgain() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"));
        TenantContextResolver resolver = resolverOver(client, Duration.ofMinutes(1));

        resolver.resolve(SUBJECT, BEARER);
        resolver.resolve(SUBJECT, BEARER);

        verify(client, times(1)).resolveSelf(anyString());
    }

    /**
     * Просроченная запись перечитывается: срок годности — страховка на
     * пропущенный сброс, и без перечитывания он ничего не страхует.
     */
    @Test
    @DisplayName("За сроком годности контекст перечитывается у владельца")
    void anExpiredCacheEntryIsReread() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"));
        TenantContextResolver resolver = resolverOver(client, Duration.ZERO);

        resolver.resolve(SUBJECT, BEARER);
        resolver.resolve(SUBJECT, BEARER);

        verify(client, times(2)).resolveSelf(anyString());
    }

    private AuthMembershipClient clientReturning(MembershipApiModel... memberships) {
        AuthMembershipClient client = mock(AuthMembershipClient.class);
        when(client.resolveSelf(anyString())).thenReturn(List.of(memberships));
        return client;
    }

    private TenantContextResolver resolverOver(AuthMembershipClient client, Duration cacheTtl) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.getMembership().setCacheTtl(cacheTtl);
        return new TenantContextResolver(client, new MembershipCache(properties));
    }
}
