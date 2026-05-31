package com.example.tradingbot.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Собирает REST-клиент к OKX: базовый URL и (для demo-окружения)
 * header x-simulated-trading. Без header запросы уходят в production
 * даже при demo-ключах (docs/integrations/okx/contracts/service-urls.md).
 * {@code OkxProperties} регистрируется через @ConfigurationPropertiesScan.
 */
@Configuration
public class OkxConfig {

    private static final String SIMULATED_HEADER = "x-simulated-trading";

    @Bean
    public RestClient okxRestClientHttp(OkxProperties properties, RestClient.Builder builder) {
        RestClient.Builder configured = builder.baseUrl(properties.getBaseUrl());
        if (StringUtils.isNotBlank(properties.getSimulated())) {
            configured = configured.defaultHeader(SIMULATED_HEADER, properties.getSimulated());
        }
        return configured.build();
    }
}
