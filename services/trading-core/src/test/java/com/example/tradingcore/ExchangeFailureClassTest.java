package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.tradingbot.domain.exchange.ExchangeFailureClass;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.internal.api.PeerReadException;
import com.example.tradingcore.integration.internal.api.PeerServiceUnavailableException;
import com.example.tradingcore.integration.internal.api.ServiceTokenProvider;
import com.example.tradingcore.integration.internal.api.exchange.ControlledExchangeException;
import com.example.tradingcore.integration.internal.api.exchange.CredentialsRejectedException;
import com.example.tradingcore.integration.internal.api.exchange.CredentialsUnavailableException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeIntegrationException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.integration.internal.api.exchange.ExternalInvariantViolationException;
import com.example.tradingcore.integration.internal.api.exchange.ExternalStatusException;
import com.example.tradingcore.integration.internal.api.exchange.SecretStoreUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Класс отказа переезжает границу и снова становится исключением.
 *
 * <p><b>Предмет проверки — то, ради чего класс вообще объявлен отдельным
 * полем.</b> Реакция на отказ живёт у ядра
 * (docs/rules/controlled-exchange-exceptions.md), и коннектор для того и
 * не сворачивает разные отказы в общий статус
 * (docs/components/IntegrationService.md §«Классы отказа на границе — дом
 * здесь»). Если разбор здесь свернёт их обратно, объявление останется
 * верным, а поведение — нет: две противоположные причины получат одну
 * реакцию.
 *
 * <p><b>Молчащий коннектор проверяется отдельно</b> — это ровно тот
 * случай, где классифицировать нечего, и превращение его в отказ площадки
 * увело бы в {@code ERROR} каждую живую сделку при плановой выкатке
 * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
 * свой класс, и сделку в ошибку он не уводит»).
 */
class ExchangeFailureClassTest {

    private static final String BASE = "http://connector-okx:8080";
    private static final String ACCOUNT = "ea-0001";
    private static final String POSITIONS = BASE + "/api/v1/accounts/" + ACCOUNT + "/positions";

    private final ServiceTokenProvider tokenProvider = mock(ServiceTokenProvider.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ExchangeOperationsClient client =
            new ExchangeOperationsClient(builder, tokenProvider, properties());

    /**
     * «Ключей нет» и «хранилище не ответило» остаются разными классами:
     * в первом случае повтор бесполезен — ключи не заводили, во втором он
     * единственно верен.
     */
    @Test
    void missingKeysAndSilentStoreStayApart() {
        givenFailure(HttpStatus.UNPROCESSABLE_CONTENT, ExchangeFailureClass.CREDENTIALS_UNAVAILABLE, null);
        givenFailure(HttpStatus.SERVICE_UNAVAILABLE, ExchangeFailureClass.SECRET_STORE_UNAVAILABLE, null);

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(CredentialsUnavailableException.class);
        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(SecretStoreUnavailableException.class);
    }

    /**
     * Отвергнутые площадкой ключи — свой класс, и он остаётся сбоем
     * ИНТЕГРАЦИИ: незнающий обработчик увидит обычный отказ взаимодействия,
     * а не пропустит его молча.
     */
    @Test
    void rejectedKeysAreTheirOwnClassAndStillAnIntegrationFailure() {
        givenFailure(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXCHANGE_CREDENTIALS_REJECTED, null);

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(CredentialsRejectedException.class)
                .isInstanceOf(ExchangeIntegrationException.class);
    }

    /**
     * Контролируемый отказ приезжает своей иерархией: у неё предмет —
     * сущность, и реакция на неё безусловная биржевая ступень 2, а не
     * повтор.
     */
    @Test
    void controlledFailuresKeepTheirOwnHierarchy() {
        givenFailure(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXTERNAL_INVARIANT_VIOLATION, null);
        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(ExternalInvariantViolationException.class)
                .isInstanceOf(ControlledExchangeException.class)
                .isNotInstanceOf(ExchangeIntegrationException.class);
    }

    /**
     * Причина внутри {@code EXTERNAL_STATUS} доезжает полем: ядро ставит её
     * причиной закрытия сущности, и выуживать её из текста сообщения ему
     * было бы нечем.
     */
    @Test
    void externalStatusCarriesItsReason() {
        givenFailure(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXTERNAL_STATUS,
                ExternalStatusReason.ORDER_FAILED.name());

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(ExternalStatusException.class)
                .satisfies(failure -> assertThat(((ExternalStatusException) failure).getReasonCode())
                        .isEqualTo(ExternalStatusReason.ORDER_FAILED));
    }

    /**
     * Неизвестная причина не подставляется догадкой: исход сущности
     * объяснялся бы тем, чего источник не сообщал.
     */
    @Test
    void unknownReasonDoesNotBecomeAGuess() {
        givenFailure(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXTERNAL_STATUS, "SOMETHING_NEW");

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(ExternalStatusException.class)
                .satisfies(failure -> assertThat(((ExternalStatusException) failure).getReasonCode())
                        .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS));
    }

    /**
     * Коннектор не ответил вовсе — классифицировать нечего: проход
     * пропускается, а не уводит сделку в {@code ERROR}. Safety-flow ходит
     * через тот же коннектор, поэтому такой перевод не дал бы и защиты.
     */
    @Test
    void silentConnectorSkipsThePassInsteadOfFailingTheDeal() {
        when(tokenProvider.getTokenValue(anyString())).thenReturn("t");
        server.expect(requestTo(POSITIONS)).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(PeerServiceUnavailableException.class);
    }

    /**
     * {@code 5xx} без узнаваемого класса — то же молчание: тело отказа
     * коннектор в этом случае не собирал, и выводить класс из статуса
     * значило бы держать второй разбор той же истины.
     */
    @Test
    void serverFailureWithoutClassIsSilenceToo() {
        when(tokenProvider.getTokenValue(anyString())).thenReturn("t");
        server.expect(requestTo(POSITIONS)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(PeerServiceUnavailableException.class);
    }

    /**
     * Негодный вход и отвергнутая идентичность — наш дефект: тем же
     * запросом придёт тот же отказ, и повторять его незачем.
     */
    @Test
    void refusalOfOurRequestIsOurDefect() {
        when(tokenProvider.getTokenValue(anyString())).thenReturn("t");
        server.expect(requestTo(POSITIONS)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"INVALID_REQUEST\",\"message\":\"bad operand\"}"));

        assertThatThrownBy(() -> client.getPositions(ACCOUNT))
                .isInstanceOf(PeerReadException.class);
    }

    private void givenFailure(HttpStatus status, ExchangeFailureClass failureClass, String reason) {
        when(tokenProvider.getTokenValue(anyString())).thenReturn("t");
        String reasonField = isNull(reason) ? "" : "\"reason\":\"" + reason + "\",";
        server.expect(requestTo(POSITIONS)).andRespond(withStatus(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"" + failureClass.name() + "\"," + reasonField
                        + "\"message\":\"boom\",\"occurredAt\":\"2026-09-05T10:00:00Z\"}"));
    }

    private static NeighbourProperties properties() {
        NeighbourProperties properties = new NeighbourProperties();
        properties.getConnector().setBaseUrl(BASE);
        properties.getConnector().setClientRegistrationId("platform-services");
        return properties;
    }
}
