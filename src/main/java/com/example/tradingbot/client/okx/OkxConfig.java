package com.example.tradingbot.client.okx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "okx")
public class OkxConfig {
    private String apiKey;
    private String secretKey;
    private String passphrase;
    private String baseUrl = "https://www.okx.com";
    private boolean simulatedTrading;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isSimulatedTrading() {
        return simulatedTrading;
    }

    public void setSimulatedTrading(boolean simulatedTrading) {
        this.simulatedTrading = simulatedTrading;
    }
}
