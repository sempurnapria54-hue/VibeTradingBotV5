package com.example.connector.okx.box;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Ожидаемая подпись приватного запроса площадки: тест считает её САМ, по
 * форме дома, а не сверяет с тем, что посчитал предмет.
 *
 * <p><b>Форма prehash взята из кода, и это названный пробел корпуса.</b>
 * Перечень четырёх заголовков подписи дом имеет
 * ({@code docs/integrations/okx/contracts/balance.md}), а из чего
 * складывается подписываемая строка — не имеет ни один: единственный
 * носитель — {@code OkxSigningInterceptor#intercept}. Находка {@code F-5}
 * документа кейсов, парковка —
 * `.claude/work/backlog.md` §«Форма подписи приватного запроса площадки
 * дома в корпусе не имеет». Пока пробел открыт, эта клетка мерит
 * ВОСПРОИЗВОДИМОСТЬ подписи, а не её соответствие доку.
 *
 * <p><b>Метка времени берётся из самого запроса.</b> Она входит в prehash
 * и производится моментом отправки; подставь тест свою — совпадения не
 * было бы никогда, и ассерт пришлось бы ослабить до «заголовок непуст».
 */
final class Signatures {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private Signatures() {
    }

    /**
     * Подпись запроса по форме площадки.
     *
     * @param secret          секрет ключа счёта
     * @param timestamp       значение заголовка метки времени запроса
     * @param method          метод запроса
     * @param pathWithQuery   путь запроса вместе с query, как он ушёл
     * @param body            тело запроса; у чтения пусто
     * @return значение заголовка подписи
     */
    static String expected(String secret, String timestamp, String method, String pathWithQuery, String body) {
        String prehash = timestamp + method + pathWithQuery + body;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC-SHA256 недоступен в этой JVM", failure);
        }
    }
}
