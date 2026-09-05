package com.example.connector.okx.integration.client;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.util.OkxConstants;
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

/**
 * Подпись приватного запроса площадки ключами ОДНОГО счёта (HMAC-SHA256).
 *
 * <p>prehash = timestamp + метод + путь(+query) + тело; подпись =
 * base64(HMAC-SHA256(secret, prehash)).
 *
 * <p><b>Не бин, и это несущее.</b> В доноре перехватчик был компонентом
 * контекста и читал ключи из конфигурации процесса — форма, верная ровно
 * пока счёт один. Здесь перехватчик создаётся на ключи конкретного счёта
 * ({@link OkxSignedRestClientFactory}) и живёт столько же, сколько
 * клиент, которому он поставлен: бин с ключами в поле подписал бы запрос
 * второго тенанта ключами первого.
 *
 * <p><b>Секреты не логируются</b> ({@code .claude/rules/codestyle.md}
 * §Логирование): ни в сообщении отказа, ни в диагностике. Отказ называет
 * только то, чего не хватило.
 */
@RequiredArgsConstructor
public class OkxSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final ExchangeCredentials credentials;

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
        request.getHeaders().add(OkxConstants.ACCESS_KEY_HEADER, credentials.getApiKey());
        request.getHeaders().add(OkxConstants.ACCESS_SIGN_HEADER, sign(prehash));
        request.getHeaders().add(OkxConstants.ACCESS_TIMESTAMP_HEADER, timestamp);
        request.getHeaders().add(OkxConstants.ACCESS_PASSPHRASE_HEADER, credentials.getPassphrase());
        return execution.execute(request, body);
    }

    /**
     * Неполные ключи — отказ до подписи и до сети.
     *
     * <p>Пустое поле дало бы NPE на {@code secret.getBytes()} либо, что
     * хуже, запрос с пустым заголовком: площадка отвергла бы его как
     * «неверные креды», и отказ хранилища стал бы неотличим от отказа
     * площадки.
     */
    private void requireCredentials() {
        if (isBlank(credentials.getApiKey())
                || isBlank(credentials.getSecret())
                || isBlank(credentials.getPassphrase())) {
            throw new IllegalStateException("Ключи счёта неполны: подпись запроса невозможна");
        }
    }

    private String sign(String prehash) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(credentials.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Подпись запроса площадки не удалась", e);
        }
    }
}
