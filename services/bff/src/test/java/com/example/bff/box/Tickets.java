package com.example.bff.box;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Билет подписки, собранный и разобранный СНАРУЖИ ящика.
 *
 * <p><b>Зачем стороннему сборщику вообще существовать.</b> Секрет
 * подписи общий у реплик, и подписка может прийти не на ту реплику, что
 * выдала билет (docs/architecture/contracts.md §«Подписку открывает
 * билет, а не сам токен»). Реплика, знающая секрет и билета не
 * выдававшая, — это ровно тот, кого играет здесь прогон: принятый ею
 * билет предъявляет свойство, которое в одном контексте иначе не
 * наблюдаемо. Обратная сторона того же — билет ЧУЖОГО секрета, и он
 * собирается тем же кодом другим ключом.
 *
 * <p><b>Разбор нужен клетке о СОСТАВЕ.</b> Утверждение «полей три, роли
 * среди них нет» проверяется по подписываемой части, а не по поведению:
 * роль, положенная в билет, поведения сегодня не меняет ничем — её
 * никто не читает, — и потому поведением её присутствие неразличимо.
 *
 * <p><b>Кодировка повторена по коду сервиса, и это названная копия.</b>
 * Общего носителя у формы билета нет: она внутренняя, читателя на другой
 * стороне провода у неё ровно один — сам периметр. Копия расходится с
 * оригиналом молча, и ловит расхождение клетка {@code B2.4}: билет,
 * собранный этим кодом на ШТАТНОМ секрете, обязан быть принят.
 */
final class Tickets {

    private static final String MAC_ALGORITHM = "HmacSHA256";

    private static final String FIELD_SEPARATOR = "|";

    private static final String SIGNATURE_SEPARATOR = ".";

    private Tickets() {
    }

    /**
     * Собирает билет названным секретом.
     *
     * @param secret    секрет подписи
     * @param subject   субъект, за которого билет отвечает
     * @param tenantId  тенант, чьи факты уйдут в поток
     * @param expiresAt момент, после которого билет негоден
     */
    static String forge(String secret, String subject, String tenantId, Instant expiresAt) {
        String value = String.join(FIELD_SEPARATOR, subject, tenantId,
                String.valueOf(expiresAt.getEpochSecond()));
        return encode(value) + SIGNATURE_SEPARATOR + encode(sign(secret, value));
    }

    /** Подписываемая часть билета, разобранная на поля. */
    static List<String> fieldsOf(String ticket) {
        return List.of(valueOf(ticket).split("\\" + FIELD_SEPARATOR));
    }

    /** Подписываемая часть билета дословно. */
    static String valueOf(String ticket) {
        return decode(ticket.substring(0, ticket.lastIndexOf(SIGNATURE_SEPARATOR)));
    }

    /**
     * Портит ЗНАЧИМУЮ часть билета на один знак, подпись оставляя
     * прежней.
     *
     * <p>Меняется последний знак тенанта: так испорченная часть остаётся
     * разбираемой, и отказ приходит от несовпадения подписи, а не от
     * развалившейся кодировки — иначе клетка мерила бы разбор Base64.
     */
    static String tampered(String ticket) {
        int separatorAt = ticket.lastIndexOf(SIGNATURE_SEPARATOR);
        List<String> fields = fieldsOf(ticket);
        String changed = String.join(FIELD_SEPARATOR, fields.get(0), fields.get(1) + "x", fields.get(2));
        return encode(changed) + ticket.substring(separatorAt);
    }

    private static String sign(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), MAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Подпись билета не собирается", failure);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
