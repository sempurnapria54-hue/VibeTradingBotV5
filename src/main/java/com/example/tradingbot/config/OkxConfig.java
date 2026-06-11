package com.example.tradingbot.config;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.integration.service.okx.OkxSigningInterceptor;
import com.example.tradingbot.util.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Собирает REST-клиенты к OKX: публичный (рыночные данные шага 1) и
 * приватный с подписью ({@link OkxSigningInterceptor}) для торговых и
 * account endpoint'ов. Для demo-окружения добавляется header
 * x-simulated-trading (docs/integrations/okx/contracts/service-urls.md).
 * {@code OkxProperties} регистрируется через @ConfigurationPropertiesScan.
 */
@Configuration
public class OkxConfig {

    @Bean
    public RestClient okxRestClientHttp(OkxProperties properties, RestClient.Builder builder) {
        RestClient.Builder configured = builder.baseUrl(properties.getBaseUrl());
        if (isNotBlank(properties.getSimulated())) {
            configured = configured.defaultHeader(Constants.Okx.SIMULATED_HEADER, properties.getSimulated());
        }
        return configured.build();
    }

    @Bean
    public RestClient okxAuthRestClientHttp(OkxProperties properties, RestClient.Builder builder,
                                            OkxSigningInterceptor signingInterceptor) {
        RestClient.Builder configured = builder.baseUrl(properties.getBaseUrl())
                .requestInterceptor(signingInterceptor)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (isNotBlank(properties.getSimulated())) {
            configured = configured.defaultHeader(Constants.Okx.SIMULATED_HEADER, properties.getSimulated());
        }
        return configured.build();
    }
}
