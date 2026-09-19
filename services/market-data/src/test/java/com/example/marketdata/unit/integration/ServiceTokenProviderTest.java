package com.example.marketdata.unit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.exception.ExchangeReadException;
import com.example.marketdata.integration.internal.api.ServiceTokenProvider;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/**
 * Третья копия провайдера служебного токена: группа `U10` и клетка `U14.5`
 * документа `.claude/tests/cases/platform-shared-logic.md`.
 *
 * <p><b>Группа своя, а не строка в `U9`, и это не оформление.</b> Копия
 * расходится с двумя другими ТРЕМЯ осями сразу: идентификатор регистрации
 * берётся из конфигурации, а не из аргумента; метод параметра не
 * принимает; класс отказа — свой. Слить её с контрактом копий значило бы
 * объявить тождество, которого нет (`U12.4`).
 *
 * <p><b>Два кейса помечены {@code @Tag("debt")}:</b> их ожидание взято из
 * дома, а код несёт иначе. `U10.2` — класс отказа описывает чтение
 * ПЛОЩАДКИ, под которое недобытый служебный токен не подходит (находка
 * `F6`); `U10.3` — незаданная регистрация отказывает классом библиотеки,
 * по которому вызывающий не отличает «чини конфигурацию окружения» от
 * «чини запрос» (подтверждение припаркованного долга).
 */
class ServiceTokenProviderTest {

    private static final String REGISTRATION_ID = "connector";
    private static final String PRINCIPAL_NAME = "market-data";
    private static final String TOKEN_VALUE = "abc";

    @Test
    @DisplayName("U10.1 — токен добывается по регистрации ИЗ СВОЙСТВ, принципал — имя сервиса")
    void u10_1_theRegistrationComesFromThePropertiesAndThePrincipalIsTheServiceName() {
        OAuth2AuthorizedClientManager manager = managerReturning(authorizedClient());

        assertThat(new ServiceTokenProvider(manager, propertiesWith(REGISTRATION_ID)).getTokenValue())
                .isEqualTo(TOKEN_VALUE);

        ArgumentCaptor<OAuth2AuthorizeRequest> captured =
                ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(manager).authorize(captured.capture());
        assertThat(captured.getValue().getClientRegistrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(captured.getValue().getPrincipal().getName()).isEqualTo(PRINCIPAL_NAME);
    }

    @Test
    @Tag("debt")
    @DisplayName("U10.2 — пустая авторизация: ожидание из дома — класс соседского яруса (долг F6)")
    void u10_2_anEmptyAuthorizationFailsWithAPeerTierClass() {
        ServiceTokenProvider provider =
                new ServiceTokenProvider(managerReturning(null), propertiesWith(REGISTRATION_ID));

        assertThatThrownBy(provider::getTokenValue)
                .as("дом класса описывает отказ ЧТЕНИЯ ПЛОЩАДКИ — транспорт, отказ доступа, "
                        + "неразобранный ответ; недобытый служебный токен не подходит ни под одно. "
                        + "КАКИМ класс станет, решает владелец (находка F6); ожидание мерит, что "
                        + "он не этот")
                .isNotInstanceOf(ExchangeReadException.class);
    }

    @Test
    @Tag("debt")
    @DisplayName("U10.3 — регистрация в конфигурации не задана: отказ своим классом (долг)")
    void u10_3_anUnsetRegistrationFailsWithOurOwnClass() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        ServiceTokenProvider provider = new ServiceTokenProvider(manager, propertiesWith(null));

        Throwable thrown = catchThrowable(provider::getTokenValue);

        assertThat(thrown)
                .as("отказ приходит, а не поход в неизвестно чей коннектор")
                .isNotNull();
        assertThat(thrown.getClass().getName())
                .as("умолчаний у адреса и регистрации нет намеренно: незаданное означает ОТКАЗ, "
                        + "и вызывающий отличает «чини конфигурацию окружения» от «чини запрос» "
                        + "по классу — значит класс наш, а не библиотечный")
                .startsWith("com.example.");
    }

    @Test
    @DisplayName("U10.4 — метод параметра не принимает: у копии один адресат")
    void u10_4_theMethodTakesNoRegistrationParameter() throws Exception {
        Method getTokenValue = ServiceTokenProvider.class.getMethod("getTokenValue");

        assertThat(getTokenValue.getParameterCount())
                .as("второй регистрации копия добыть не может по построению: коннектор один на площадку")
                .isZero();
    }

    // --- U14.5: значение токена наружу не уходит --------------------------

    @Test
    @DisplayName("U14.5 — значение токена не попадает ни в лог, ни в сообщение отказа")
    void u14_5_theTokenValueNeverLeavesTheProvider() throws Exception {
        Path source = Path.of("src", "main", "java", "com", "example", "marketdata",
                "integration", "internal", "api", "ServiceTokenProvider.java");
        assertThat(Files.isRegularFile(source))
                .as("исходника %s нет — проба мерила бы пустоту", source)
                .isTrue();

        assertThat(Files.readString(source, StandardCharsets.UTF_8))
                .as("секреты не логируются (.claude/rules/codestyle.md §Логирование)")
                .doesNotContain("Slf4j", "Logger", "log.");
        assertThatThrownBy(
                new ServiceTokenProvider(managerReturning(null), propertiesWith(REGISTRATION_ID))
                        ::getTokenValue)
                .hasMessageNotContaining(TOKEN_VALUE);
    }

    // --- оснастка ---------------------------------------------------------

    private static ConnectorProperties propertiesWith(String clientRegistrationId) {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientRegistrationId(clientRegistrationId);
        return properties;
    }

    private static OAuth2AuthorizedClientManager managerReturning(OAuth2AuthorizedClient client) {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(client);
        return manager;
    }

    private static OAuth2AuthorizedClient authorizedClient() {
        Instant issued = Instant.now();
        return new OAuth2AuthorizedClient(
                ClientRegistration.withRegistrationId(REGISTRATION_ID)
                        .clientId("service")
                        .clientSecret("secret")
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .tokenUri("https://idp.local/oauth2/token")
                        .build(),
                PRINCIPAL_NAME,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN_VALUE,
                        issued, issued.plus(5, ChronoUnit.MINUTES)));
    }
}
