package com.example.bff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.bff.api.AccessDenialHandler;
import com.example.bff.api.GlobalExceptionHandler;
import com.example.bff.api.controller.PerimeterController;
import com.example.bff.api.controller.ProxyController;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.config.SecurityConfig;
import com.example.bff.domain.MembershipCache;
import com.example.bff.domain.SubscriptionTicketService;
import com.example.bff.domain.TenantContextResolver;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.integration.internal.api.AuthMembershipClient;
import com.example.bff.integration.internal.api.OwnerProxyClient;
import com.example.bff.integration.internal.api.model.MembershipApiModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Минимальный контекст собственной поверхности периметра.
 *
 * <p>Здесь нет ни Kafka, ни владельцев за периметром: предмет обеих
 * проверок — контур доступа и тропа потока, а авто-конфигурация Boot
 * тянула бы за собой брокер и соседей, которых предмету не требуется.
 * Поэтому контекст собирается <b>spring-test</b>, а не
 * {@code @SpringBootTest}.
 *
 * <p><b>Подменены только коллабораторы границы</b> — разборщик токена и
 * клиент владельца членств: у первого своя проверка у провайдера, второй
 * есть чужая поверхность. Поведение периметра — билет, контекст, окно
 * потока — считается настоящим.
 */
@Configuration
@EnableWebMvc
@EnableWebSecurity
@Import(SecurityConfig.class)
class PerimeterSurfaceContext {

    /** Тенант единственного членства подменённого владельца. */
    static final String TENANT = "tenant-7";

    /** Секрет подписи билета: общий у реплик, здесь — у одной. */
    static final String TICKET_SECRET = "secret-of-replicas";

    /**
     * Подпись токена разбирает провайдер идентичности, которого в
     * контуре теста нет. Бин обязателен самой конфигурацией
     * ресурс-сервера; предъявленный токен подставляет оснастка теста,
     * поэтому разборщик не зовётся ни разу.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    AccessDenialHandler accessDenialHandler(ObjectMapper objectMapper) {
        return new AccessDenialHandler(objectMapper);
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    PerimeterProperties perimeterProperties() {
        PerimeterProperties properties = new PerimeterProperties();
        properties.setOwnerUrlTemplate("http://{owner}:8080");
        properties.setReadRetries(1);
        properties.getMembership().setCacheTtl(Duration.ofMinutes(1));
        properties.getStream().setReplayWindow(10);
        properties.getStream().setConnectionTimeout(Duration.ofMinutes(1));
        properties.getStream().setMaxSubscriptionsPerTenant(32);
        properties.getStream().setPulseInterval(Duration.ofSeconds(15));
        properties.getTicket().setSecret(TICKET_SECRET);
        properties.getTicket().setTtl(Duration.ofMinutes(10));
        return properties;
    }

    /** Владелец «кто есть кто» подменён: у него своя проверка. */
    @Bean
    AuthMembershipClient authMembershipClient() {
        AuthMembershipClient client = mock(AuthMembershipClient.class);
        when(client.resolveSelf(anyString()))
                .thenReturn(List.of(new MembershipApiModel("m-1", TENANT, "OWNER")));
        return client;
    }

    @Bean
    MembershipCache membershipCache(PerimeterProperties properties) {
        return new MembershipCache(properties);
    }

    @Bean
    TenantContextResolver tenantContextResolver(AuthMembershipClient client, MembershipCache cache) {
        return new TenantContextResolver(client, cache);
    }

    @Bean
    SubscriptionTicketService subscriptionTicketService(PerimeterProperties properties) {
        return new SubscriptionTicketService(properties);
    }

    @Bean
    StreamRegistry streamRegistry(PerimeterProperties properties) {
        return new StreamRegistry(properties);
    }

    /** Пересылка подменена: у владельцев за периметром своя проверка. */
    @Bean
    OwnerProxyClient ownerProxyClient() {
        OwnerProxyClient client = mock(OwnerProxyClient.class);
        when(client.forward(anyString(), any(), anyString(), any(), any()))
                .thenReturn(ResponseEntity.ok(new byte[0]));
        return client;
    }

    @Bean
    ProxyController proxyController(TenantContextResolver resolver, OwnerProxyClient proxyClient) {
        return new ProxyController(resolver, proxyClient);
    }

    @Bean
    PerimeterController perimeterController(TenantContextResolver resolver,
                                            SubscriptionTicketService ticketService,
                                            StreamRegistry registry,
                                            PerimeterProperties properties) {
        return new PerimeterController(resolver, ticketService, registry, properties);
    }
}
