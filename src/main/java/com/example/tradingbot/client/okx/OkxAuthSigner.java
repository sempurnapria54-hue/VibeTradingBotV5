package com.example.tradingbot.client.okx;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class OkxAuthSigner {
    private final OkxConfig okxConfig;

    public OkxAuthSigner(OkxConfig okxConfig) {
        this.okxConfig = okxConfig;
    }

    public String sign(String timestamp, String method, String requestPath, String body) {
        String payload = timestamp + method.toUpperCase() + requestPath + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(okxConfig.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign OKX request", ex);
        }
    }
}
