package com.example.bff.unit.domain;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.SubscriptionTicketService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Базовая сборка групп `U1` и `U2` документа
 * `.claude/tests/cases/bff-perimeter-logic.md` и кузница значений
 * верного подписания.
 *
 * <p><b>Зачем кузница.</b> Часть кейсов разбора спрашивает охраны,
 * лежащие ПОСЛЕ проверки подписи: состав значения, разбор момента
 * негодности и его диапазон. Предъявителю эти состояния закрыты подписью
 * — значение верного подписания строит только знающий секрет, — и
 * собрать их может лишь тот, кому секрет известен, то есть тест
 * (§«Состояния, недостижимые предъявителю»). Красный прогон на таком
 * кейсе продового дефекта не предъявляет.
 */
final class TicketForge {

    /** Алгоритм подписи — тот же, которым подписывает предмет. */
    private static final String MAC_ALGORITHM = "HmacSHA256";

    /** Разделитель полей внутри подписываемого значения. */
    static final String FIELD_SEPARATOR = "|";

    /** Разделитель значения и его подписи. */
    static final String SIGNATURE_SEPARATOR = ".";

    /** Секрет базовой сборки: непустой литерал теста. */
    static final String SECRET = "secret-of-replicas";

    private TicketForge() {
    }

    /**
     * Сервис базовой сборки.
     *
     * @param secret секрет подписи
     * @param ttl    срок билета
     * @return сервис билетов
     */
    static SubscriptionTicketService serviceWith(String secret, Duration ttl) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.getTicket().setSecret(secret);
        properties.getTicket().setTtl(ttl);
        return new SubscriptionTicketService(properties);
    }

    /** Сервис базовой сборки: секрет непуст, срок заведомо больше кейса. */
    static SubscriptionTicketService service() {
        return serviceWith(SECRET, Duration.ofMinutes(10));
    }

    /**
     * Собрать значение верного подписания из названных полей.
     *
     * @param secret секрет, которым подписывается значение
     * @param fields поля подписываемой части, в порядке следования
     * @return билет, который разбор примет за свой
     */
    static String forge(String secret, String... fields) {
        String value = String.join(FIELD_SEPARATOR, fields);
        return encode(value) + SIGNATURE_SEPARATOR + encode(sign(secret, value));
    }

    /** Значимая часть билета, как она едет. */
    static String significantPartOf(String ticket) {
        return ticket.substring(0, ticket.lastIndexOf(SIGNATURE_SEPARATOR));
    }

    /** Часть подписи билета, как она едет. */
    static String signaturePartOf(String ticket) {
        return ticket.substring(ticket.lastIndexOf(SIGNATURE_SEPARATOR) + 1);
    }

    /** Поля подписываемой части: субъект, тенант, момент негодности. */
    static String[] fieldsOf(String ticket) {
        String value = new String(Base64.getUrlDecoder().decode(significantPartOf(ticket)), StandardCharsets.UTF_8);
        return value.split("\\" + FIELD_SEPARATOR, -1);
    }

    private static String sign(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), MAC_ALGORITHM));
            return encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Подпись кузницы не собирается", failure);
        }
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
