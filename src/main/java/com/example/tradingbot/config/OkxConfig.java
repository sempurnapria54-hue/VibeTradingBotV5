package com.example.tradingbot.config;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.util.Constants;
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

    @Bean
    public RestClient okxRestClientHttp(OkxProperties properties, RestClient.Builder builder) {
        RestClient.Builder configured = builder.baseUrl(properties.getBaseUrl());
        if (isNotBlank(properties.getSimulated())) {
            configured = configured.defaultHeader(Constants.Okx.SIMULATED_HEADER, properties.getSimulated());
        }
        return configured.build();
    }
}
