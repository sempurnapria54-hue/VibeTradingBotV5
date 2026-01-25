package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "okx")
public class OkxConfig {

    private String apiKey;
    private String secretKey;
    private String passphrase;
    private String baseUrl = "https://www.okx.com";
    private boolean simulatedTrading;
}
