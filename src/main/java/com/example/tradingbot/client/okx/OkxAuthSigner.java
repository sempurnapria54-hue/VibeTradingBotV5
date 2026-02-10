package com.example.tradingbot.client.okx;

import com.example.tradingbot.config.OkxConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class OkxAuthSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final OkxConfig okxConfig;

    public HttpHeaders buildHeaders(HttpMethod method, String requestPathWithQuery, String body) {
        HttpHeaders headers = new HttpHeaders();
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String payload = timestamp + method.name() + requestPathWithQuery + body;
        String sign = sign(payload, okxConfig.getSecretKey());
        headers.add("OK-ACCESS-KEY", okxConfig.getApiKey());
        headers.add("OK-ACCESS-SIGN", sign);
        headers.add("OK-ACCESS-TIMESTAMP", timestamp);
        headers.add("OK-ACCESS-PASSPHRASE", okxConfig.getPassphrase());
        return headers;
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign OKX request", exception);
        }
    }
}
