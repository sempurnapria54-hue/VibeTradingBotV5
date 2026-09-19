package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Провайдер служебного токена: группы `U9`, `U12.2` и клетка `U14.5`
 * документа `.claude/tests/cases/platform-shared-logic.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии</b> ({@code trading-core}, {@code strategies}). Третья копия —
 * {@code market-data} — в тождество не входит: она расходится по существу
 * тремя осями, и у неё своя группа `U10`.
 *
 * <p><b>Менеджер авторизованных клиентов подменён, и это признак
 * предмета.</b> Он — граница: ходит к провайдеру идентичности. Подменяется
 * ровно она, а не логика провайдера (.claude/rules/codestyle.md §«Тесты
 * доменных моделей»).
 *
 * <p><b>Клейм «пустой токен — отказ, а не анонимный вызов» держится одной
 * ветвью из четырёх.</b> Три остальные помечены {@code @Tag("debt")}: их
 * ожидание взято из дома, а код отказывает классами библиотеки, по которым
 * вызывающий не ветвится (находка `F3`).
 */
public abstract class ServiceTokenProviderContract {

    /** Идентификатор регистрации: он же уезжает в сообщение отказа. */
    protected static final String REGISTRATION_ID = "market-data";

    private static final String TOKEN_VALUE = "abc";

    /** Порт к своей копии провайдера. */
    protected abstract String tokenValue(OAuth2AuthorizedClientManager manager, String registrationId);

    /** Имя принципала своей копии — имя своего сервиса; объявленное различие. */
    protected abstract String expectedPrincipalName();

    /** Класс отказа своего дерева. */
    protected abstract Class<? extends RuntimeException> readExceptionType();

    /** Исходник своей копии — вход клетки `U14.5`. */
    protected abstract Path providerSource();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- U9: живая охрана и три чужих класса ------------------------------

    @Test
    @DisplayName("U9.1 — добытый токен возвращается значением")
    void u9_1_theAcquiredTokenIsReturned() {
        assertThat(tokenValue(managerReturning(authorizedClient(TOKEN_VALUE)), REGISTRATION_ID))
                .isEqualTo(TOKEN_VALUE);
    }

    @Test
    @DisplayName("U9.2 — менеджер отдал пустоту: единственная живая ветвь собственной охраны")
    void u9_2_anEmptyAuthorizationIsRefusedByOurOwnGuard() {
        assertThatThrownBy(() -> tokenValue(managerReturning(null), REGISTRATION_ID))
                .as("пустой токен — отказ, а не анонимный вызов")
                .isInstanceOf(readExceptionType())
                .hasMessageContaining(REGISTRATION_ID);
    }

    @Test
    @Tag("debt")
    @DisplayName("U9.4 — неизвестная регистрация: ожидание из дома — наш класс (долг F3)")
    void u9_4_anUnknownRegistrationFailsWithOurOwnClass() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenThrow(
                new IllegalArgumentException("clientRegistration cannot be null"));

        assertThatThrownBy(() -> tokenValue(manager, "unknown"))
                .as("ненастроенная идентичность есть наш дефект, и вызывающий ветвится по классу")
                .isInstanceOf(readExceptionType());
    }

    @Test
    @Tag("debt")
    @DisplayName("U9.5 — пустой идентификатор регистрации: отказ приходит до менеджера (долг F3)")
    void u9_5_anEmptyRegistrationIdFailsBeforeTheManagerIsAsked() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);

        assertThatThrownBy(() -> tokenValue(manager, ""))
                .isInstanceOf(readExceptionType());
    }

    @Test
    @Tag("debt")
    @DisplayName("U9.6 — провайдер идентичности отверг выдачу (долг F3)")
    void u9_6_aRejectedIssuanceFailsWithOurOwnClass() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenThrow(new ClientAuthorizationException(
                new OAuth2Error("invalid_client"), REGISTRATION_ID));

        assertThatThrownBy(() -> tokenValue(manager, REGISTRATION_ID))
                .as("класс библиотеки вызывающему чужой — по нему он не ветвится")
                .isInstanceOf(readExceptionType());
    }

    @Test
    @DisplayName("U9.7 — в запрос уходит имя СЕРВИСА и переданная регистрация")
    void u9_7_theRequestCarriesTheServiceNameAndTheGivenRegistration() {
        OAuth2AuthorizedClientManager manager = managerReturning(authorizedClient(TOKEN_VALUE));

        tokenValue(manager, REGISTRATION_ID);

        OAuth2AuthorizeRequest request = capturedRequest(manager);
        assertThat(request.getClientRegistrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(request.getPrincipal().getName())
                .as("межсервисный вызов идёт без пользователя — принципал служебный")
                .isEqualTo(expectedPrincipalName());
    }

    @Test
    @DisplayName("U9.8 — два вызова: своего кэша у провайдера нет")
    void u9_8_everyCallAsksTheManagerAnew() {
        OAuth2AuthorizedClientManager manager = managerReturning(authorizedClient(TOKEN_VALUE));

        tokenValue(manager, REGISTRATION_ID);
        tokenValue(manager, REGISTRATION_ID);

        verify(manager, times(2))
                .authorize(any());
    }

    @Test
    @DisplayName("U9.9 — контекст вызывающего в запрос токена не попадает")
    void u9_9_theCallerContextDoesNotLeakIntoTheTokenRequest() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "holder", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
        OAuth2AuthorizedClientManager manager = managerReturning(authorizedClient(TOKEN_VALUE));

        tokenValue(manager, REGISTRATION_ID);

        OAuth2AuthorizeRequest request = capturedRequest(manager);
        assertThat(request.getPrincipal().getName())
                .as("тенант и пользователь едут операндом вызова, а не токеном")
                .isEqualTo(expectedPrincipalName());
        assertThat(request.getAttributes())
                .as("ничего сверх регистрации и служебного принципала в запрос не кладётся")
                .doesNotContainKey(OAuth2AuthorizationContext.class.getName());
    }

    // --- U12.2: тождество копий, кроме имени принципала -------------------

    @Test
    @DisplayName("U12.2 — различие копий ровно одно: имя принципала — имя своего сервиса")
    void u12_2_theOnlyDifferenceBetweenCopiesIsThePrincipalName() {
        OAuth2AuthorizedClientManager manager = managerReturning(authorizedClient(TOKEN_VALUE));

        tokenValue(manager, REGISTRATION_ID);

        assertThat(capturedRequest(manager).getPrincipal().getName())
                .as("объявленное различие: имя принципала есть имя своего сервиса")
                .isEqualTo(expectedPrincipalName());
        assertThat(readExceptionType().getSimpleName())
                .as("класс отказа у обеих копий один и тот же по имени — различается дерево")
                .isEqualTo("PeerReadException");
    }

    // --- U14.5: значение токена наружу не уходит --------------------------

    @Test
    @DisplayName("U14.5 — значение токена не попадает ни в лог, ни в сообщение отказа")
    void u14_5_theTokenValueNeverLeavesTheProvider() throws IOException {
        Path source = providerSource();
        assertThat(Files.isRegularFile(source))
                .as("исходника %s нет — проба мерила бы пустоту", source)
                .isTrue();

        assertThat(Files.readString(source, StandardCharsets.UTF_8))
                .as("секреты не логируются (.claude/rules/codestyle.md §Логирование)")
                .doesNotContain("Slf4j", "Logger", "log.");
        assertThatThrownBy(() -> tokenValue(managerReturning(null), REGISTRATION_ID))
                .as("сообщение отказа называет регистрацию, а не значение")
                .hasMessageNotContaining(TOKEN_VALUE);
    }

    // --- оснастка ---------------------------------------------------------

    private static OAuth2AuthorizedClientManager managerReturning(OAuth2AuthorizedClient client) {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(client);
        return manager;
    }

    private static OAuth2AuthorizeRequest capturedRequest(OAuth2AuthorizedClientManager manager) {
        ArgumentCaptor<OAuth2AuthorizeRequest> captured =
                ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(manager).authorize(captured.capture());
        return captured.getValue();
    }

    private OAuth2AuthorizedClient authorizedClient(String tokenValue) {
        Instant issued = Instant.now();
        return new OAuth2AuthorizedClient(
                ClientRegistration.withRegistrationId(REGISTRATION_ID)
                        .clientId("service")
                        .clientSecret("secret")
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .tokenUri("https://idp.local/oauth2/token")
                        .build(),
                expectedPrincipalName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
                        issued, issued.plus(5, ChronoUnit.MINUTES)));
    }
}
