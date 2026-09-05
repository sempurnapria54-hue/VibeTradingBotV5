package com.example.connector.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.connector.okx.api.GlobalExceptionHandler;
import com.example.connector.okx.api.controller.ExchangeAccountOperationsController;
import com.example.connector.okx.api.controller.MarketDataController;
import com.example.connector.okx.credentials.CredentialsUnavailableException;
import com.example.connector.okx.gateway.ExchangeGateway;
import com.example.connector.okx.integration.CredentialsRejectedException;
import com.example.connector.okx.integration.ExternalStatusException;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.vault.VaultException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Поверхность коннектора на настоящем веб-слое.
 *
 * <p><b>Проверяется провод, а не вызов.</b> Контракт объявляет, что по
 * границе ездят доменные модели ({@code docs/architecture/contracts.md}
 * §«Два канала»); держится это не на объявлении, а на том, что модель
 * действительно разбирается из тела и сериализуется обратно. Тест на
 * замоканном шлюзе такого не показал бы — предмет здесь именно
 * сериализация.
 *
 * <p>Контекст поднимается <b>без БД, Vault и контура доступа</b>: ни от
 * чего из этого форма на проводе не зависит, а доступ проверяется своим
 * тестом.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConnectorSurfaceTest.SurfaceConfig.class)
class ConnectorSurfaceTest {

    private static final String ACCOUNT = "acc-1";
    private static final String OTHER_ACCOUNT = "acc-2";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ExchangeGateway gateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // Контекст переиспользуется между методами, а шлюз в нём один:
        // без сброса заглушка одного метода отвечала бы в другом.
        reset(gateway);
    }

    /**
     * Доменная заявка разбирается из тела запроса, а подтверждение приёма
     * возвращается на провод целиком: {@code ExchangeAck} неизменяем и
     * собирается билдером — форма, на которой сериализация ломается
     * первой.
     */
    @Test
    void domainOrderTravelsInAndAckTravelsOut() throws Exception {
        when(gateway.placeOrder(eq(ACCOUNT), any(), eq(INSTRUMENT))).thenReturn(ExchangeAck.builder()
                .success(Boolean.TRUE)
                .externalId("ord-1")
                .internalId("our-1")
                .code("0")
                .externalCreatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        mockMvc.perform(post("/api/v1/accounts/{account}/orders", ACCOUNT)
                        .param("externalInstrumentId", INSTRUMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"internalId\":\"our-1\",\"side\":\"BUY\",\"type\":\"ENTRY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.externalId").value("ord-1"));

        ArgumentCaptor<Order> sent = ArgumentCaptor.forClass(Order.class);
        verify(gateway).placeOrder(eq(ACCOUNT), sent.capture(), eq(INSTRUMENT));
        assertThat(sent.getValue().getInternalId()).isEqualTo("our-1");
        assertThat(sent.getValue().getSide()).isEqualTo(Order.Side.BUY);
        assertThat(sent.getValue().getType()).isEqualTo(Order.Type.ENTRY);
    }

    /** Счёт приезжает из пути — по нему коннектор и берёт ключи. */
    @Test
    void accountComesFromThePath() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{account}/positions", ACCOUNT))
                .andExpect(status().isOk());

        verify(gateway).getPositions(ACCOUNT);
    }

    /** Публичное чтение счёта не требует. */
    @Test
    void publicReadCarriesNoAccount() throws Exception {
        when(gateway.getServerTime()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC));

        mockMvc.perform(get("/api/v1/market/time")).andExpect(status().isOk());

        verify(gateway).getServerTime();
    }

    /**
     * Отказ хранилища и отказ площадки различимы по коду: реакция на них
     * живёт у ядра, и слить их в один ответ значило бы решить за него.
     */
    @Test
    void storeRefusalAndExchangeRefusalCarryDifferentCodes() throws Exception {
        when(gateway.getPositions(ACCOUNT)).thenThrow(new CredentialsUnavailableException(ACCOUNT));
        when(gateway.getPositions(OTHER_ACCOUNT))
                .thenThrow(new CredentialsRejectedException("отвергнуто площадкой"));

        mockMvc.perform(get("/api/v1/accounts/{account}/positions", ACCOUNT))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDENTIALS_UNAVAILABLE"));

        mockMvc.perform(get("/api/v1/accounts/{account}/positions", OTHER_ACCOUNT))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXCHANGE_CREDENTIALS_REJECTED"));
    }

    /**
     * Транспортный сбой и недоступность хранилища доезжают до ядра
     * КЛАССОМ, а не голым {@code 500}: по коду ядро и выбирает, ретраить
     * или поднимать ступень.
     */
    @Test
    void transportAndStoreOutagesCarryTheirOwnCodes() throws Exception {
        when(gateway.getServerTime()).thenThrow(new ResourceAccessException("соединение оборвалось"));
        when(gateway.getPositions(ACCOUNT)).thenThrow(new VaultException("хранилище недоступно"));

        mockMvc.perform(get("/api/v1/market/time"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXCHANGE_UNREACHABLE"));

        mockMvc.perform(get("/api/v1/accounts/{account}/positions", ACCOUNT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SECRET_STORE_UNAVAILABLE"));
    }

    /**
     * Причина проблемного статуса переезжает ОТДЕЛЬНЫМ полем.
     *
     * <p>Ядро ставит её причиной закрытия сущности; окажись она только в
     * тексте сообщения — исход сделки назначался бы разбором строки.
     */
    @Test
    void statusFailureCarriesItsReasonAsAField() throws Exception {
        when(gateway.getPositions(ACCOUNT)).thenThrow(
                new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, "чего-то новое"));

        mockMvc.perform(get("/api/v1/accounts/{account}/positions", ACCOUNT))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_STATUS"))
                .andExpect(jsonPath("$.reason").value("UNKNOWN_EXTERNAL_STATUS"));
    }

    /** Веб-слой поверхности: контроллеры, единая точка ошибок и подменённый шлюз. */
    @Configuration
    @EnableWebMvc
    static class SurfaceConfig {

        @Bean
        ExchangeGateway gateway() {
            return mock(ExchangeGateway.class);
        }

        @Bean
        ExchangeAccountOperationsController accountOperationsController(ExchangeGateway gateway) {
            return new ExchangeAccountOperationsController(gateway);
        }

        @Bean
        MarketDataController marketDataController(ExchangeGateway gateway) {
            return new MarketDataController(gateway);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
