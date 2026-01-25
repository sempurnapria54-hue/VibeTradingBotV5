package com.example.tradingbot.client.okx;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OkxClientConfiguration {
    @Bean
    public RestTemplate okxRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
