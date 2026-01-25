package com.example.tradingbot.client.okx;

import com.example.tradingbot.config.OkxConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OkxAuthSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OkxConfig okxConfig;

    public String sign(String timestamp, String method, String requestPath, String body) {
        String payload = timestamp + method.toUpperCase() + requestPath + body;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(okxConfig.getSecretKey().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign OKX request", exception);
        }
    }
}
