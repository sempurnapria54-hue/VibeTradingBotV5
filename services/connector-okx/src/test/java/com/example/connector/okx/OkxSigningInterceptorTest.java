package com.example.connector.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.integration.client.OkxSigningInterceptor;
import com.example.connector.okx.util.OkxConstants;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

/**
 * Подпись приватного запроса ключами конкретного счёта.
 *
 * <p><b>Проверяется само вычисление, а не факт «заголовок стоит».</b>
 * Подпись пересчитывается тестом независимо и сравнивается со ставшей в
 * заголовок: тест, смотрящий лишь на наличие заголовка, прошёл бы и на
 * подписи от чужих ключей.
 */
class OkxSigningInterceptorTest {

    private static final String API_KEY = "key-1";
    private static final String SECRET = "secret-1";
    private static final String PASSPHRASE = "pass-1";

    @Test
    void signsRequestWithAccountKeys() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("https://example.test/api/v5/account/balance?ccy=USDT"));
        byte[] body = new byte[0];

        new OkxSigningInterceptor(credentials(ExchangeAccount.Contour.LIVE))
                .intercept(request, body, (executed, executedBody) ->
                        new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        String timestamp = request.getHeaders().getFirst(OkxConstants.ACCESS_TIMESTAMP_HEADER);
        String expected = sign(timestamp + "GET/api/v5/account/balance?ccy=USDT");
        assertThat(request.getHeaders().getFirst(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(API_KEY);
        assertThat(request.getHeaders().getFirst(OkxConstants.ACCESS_PASSPHRASE_HEADER)).isEqualTo(PASSPHRASE);
        assertThat(request.getHeaders().getFirst(OkxConstants.ACCESS_SIGN_HEADER)).isEqualTo(expected);
    }

    /**
     * Тело входит в подпись: запрос с телом и без него подписаны
     * по-разному, иначе подмена тела осталась бы незамеченной площадкой.
     */
    @Test
    void bodyIsPartOfTheSignature() throws Exception {
        MockClientHttpRequest withBody = new MockClientHttpRequest(HttpMethod.POST,
                URI.create("https://example.test/api/v5/trade/order"));
        new OkxSigningInterceptor(credentials(ExchangeAccount.Contour.LIVE))
                .intercept(withBody, "{\"instId\":\"BTC-USDT-SWAP\"}".getBytes(StandardCharsets.UTF_8),
                        (executed, executedBody) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        String timestamp = withBody.getHeaders().getFirst(OkxConstants.ACCESS_TIMESTAMP_HEADER);
        assertThat(withBody.getHeaders().getFirst(OkxConstants.ACCESS_SIGN_HEADER))
                .isEqualTo(sign(timestamp + "POST/api/v5/trade/order{\"instId\":\"BTC-USDT-SWAP\"}"))
                .isNotEqualTo(sign(timestamp + "POST/api/v5/trade/order"));
    }

    /**
     * Неполные ключи — отказ до сети: иначе площадка ответила бы «неверные
     * креды», и отказ хранилища стал бы неотличим от отказа площадки.
     */
    @Test
    void incompleteKeysAreRejectedBeforeTheNetwork() {
        ExchangeCredentials incomplete =
                new ExchangeCredentials(API_KEY, "", PASSPHRASE, ExchangeAccount.Contour.LIVE);
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("https://example.test/api/v5/account/balance"));

        assertThatThrownBy(() -> new OkxSigningInterceptor(incomplete)
                .intercept(request, new byte[0], (executed, body) -> {
                    throw new IllegalStateException("запрос не должен был уйти в сеть");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ключи счёта неполны");
    }

    /** Сообщение отказа секрета не несёт (.claude/rules/codestyle.md §Логирование). */
    @Test
    void failureMessageCarriesNoSecret() {
        ExchangeCredentials incomplete =
                new ExchangeCredentials(API_KEY, "", PASSPHRASE, ExchangeAccount.Contour.LIVE);
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("https://example.test/api/v5/account/balance"));

        assertThatThrownBy(() -> new OkxSigningInterceptor(incomplete)
                .intercept(request, new byte[0], (executed, body) ->
                        new MockClientHttpResponse(new byte[0], HttpStatus.OK)))
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining(PASSPHRASE);
    }

    /**
     * Текстовое представление ключей секрета не несёт.
     *
     * <p>Проверка стои́т отдельно от запрета логировать: запрет адресован
     * пишущему лог, а он ошибается — достаточно объекта, попавшего в
     * шаблон сообщения целиком. Здесь проверяется, что такая ошибка
     * секрета не раскрывает.
     */
    @Test
    void textualFormCarriesNoSecret() {
        String text = credentials(ExchangeAccount.Contour.DEMO).toString();

        assertThat(text)
                .doesNotContain(SECRET)
                .doesNotContain(PASSPHRASE)
                .doesNotContain(API_KEY)
                .contains("DEMO");
    }

    private ExchangeCredentials credentials(ExchangeAccount.Contour contour) {
        return new ExchangeCredentials(API_KEY, SECRET, PASSPHRASE, contour);
    }

    private String sign(String prehash) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
    }
}
