package com.example.tradingbot.integration.service.okx;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.config.OkxProperties;
import com.example.tradingbot.util.Constants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Подпись приватных запросов OKX (HMAC-SHA256): добавляет заголовки
 * OK-ACCESS-KEY/SIGN/TIMESTAMP/PASSPHRASE на приватном RestClient.
 * prehash = timestamp + method + requestPath(+query) + body; подпись =
 * base64(HMAC-SHA256(secret, prehash)). Секреты не логируются и берутся из
 * Vault per-profile.
 *
 * <p>При отсутствии кредов (apiKey/secret/passphrase не заданы) — fail-fast
 * {@link IllegalStateException} «OKX credentials not configured» до подписи и
 * сети, а не голый NPE на {@code secret.getBytes()} (backlog §I3).
 */
@Component
@RequiredArgsConstructor
public class OkxSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final OkxProperties properties;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        requireCredentials();
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String requestPath = request.getURI().getRawPath();
        if (isNotBlank(request.getURI().getRawQuery())) {
            requestPath = requestPath + "?" + request.getURI().getRawQuery();
        }
        String bodyContent = new String(body, StandardCharsets.UTF_8);
        String prehash = timestamp + request.getMethod().name() + requestPath + bodyContent;
        request.getHeaders().add(Constants.Okx.ACCESS_KEY_HEADER, properties.getApiKey());
        request.getHeaders().add(Constants.Okx.ACCESS_SIGN_HEADER, sign(prehash));
        request.getHeaders().add(Constants.Okx.ACCESS_TIMESTAMP_HEADER, timestamp);
        request.getHeaders().add(Constants.Okx.ACCESS_PASSPHRASE_HEADER, properties.getPassphrase());
        return execution.execute(request, body);
    }

    /** Fail-fast при незаданных кредах: внятная ошибка вместо NPE на подписи (§I3). */
    private void requireCredentials() {
        if (isBlank(properties.getApiKey()) || isBlank(properties.getSecret()) || isBlank(properties.getPassphrase())) {
            throw new IllegalStateException("OKX credentials not configured");
        }
    }

    private String sign(String prehash) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OKX request signing failed", e);
        }
    }
}
