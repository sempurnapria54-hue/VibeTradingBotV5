package com.example.bff.domain;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.bff.config.PerimeterProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Билет подписки — вторая форма предъявления, и только на тропе потока
 * (docs/architecture/contracts.md §«Подписку открывает билет, а не сам
 * токен»).
 *
 * <p><b>Зачем он вообще.</b> Браузерный {@code EventSource} заголовка
 * {@code Authorization} не ставит — интерфейс его не принимает, — а
 * токен, уехавший в query-параметр, попадает в логи ингресса, историю
 * браузера и {@code Referer}. Билет выдаётся обычным вызовом под
 * токеном и живёт коротко.
 *
 * <p><b>Проверяется ПОДПИСЬЮ, а не таблицей.</b> Таблица выданных
 * билетов сделала бы периметр носителем состояния, а подписка может
 * прийти не на ту реплику, что выдала билет: секрет подписи общий у
 * реплик и приходит из конфигурации.
 *
 * <p><b>Билет в пределах срока переиспользуем, и это несущее свойство:</b>
 * штатное переподключение {@code EventSource} идёт ТЕМ ЖЕ адресом, и
 * одноразовый билет рвал бы его на первом же разрыве. Одноразовость без
 * таблицы и не выразима — а таблица запрещена доводом выше.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionTicketService {

    /** Алгоритм подписи. */
    private static final String MAC_ALGORITHM = "HmacSHA256";

    /** Разделитель полей внутри подписываемого значения. */
    private static final String FIELD_SEPARATOR = "|";

    /** Разделитель значения и его подписи. */
    private static final String SIGNATURE_SEPARATOR = ".";

    /** Число полей подписываемого значения: субъект, тенант, срок. */
    private static final int FIELD_COUNT = 3;

    private final PerimeterProperties properties;

    /**
     * Выдать билет предъявителю.
     *
     * @param subject  идентичность предъявителя у провайдера
     * @param tenantId тенант, чьи события уйдут в поток
     * @return билет, годный до истечения срока
     */
    public String issue(String subject, String tenantId) {
        requireConfiguredSecret();
        requireSeparatorFree(subject);
        requireSeparatorFree(tenantId);
        Instant expiresAt = Instant.now().plus(properties.getTicket().getTtl());
        String value = String.join(FIELD_SEPARATOR, subject, tenantId, String.valueOf(expiresAt.getEpochSecond()));
        return encode(value) + SIGNATURE_SEPARATOR + encode(sign(value));
    }

    /**
     * Разобрать и проверить билет.
     *
     * <p>Негодность не различается наружу: испорченная подпись,
     * истёкший срок и отсутствие билета отвечают одинаково — вызывающий
     * о контуре не узнаёт ничего сверх класса отказа.
     *
     * @param ticket предъявленный билет
     * @return субъект и тенант, за которые билет отвечает
     */
    public SubscriptionTicket verify(String ticket) {
        requireConfiguredSecret();
        if (isBlank(ticket)) {
            throw new TicketRejectedException("Билет подписки не предъявлен");
        }
        int separatorAt = ticket.lastIndexOf(SIGNATURE_SEPARATOR);
        if (separatorAt < 0) {
            throw new TicketRejectedException("Билет подписки не разбирается");
        }
        String value = decode(ticket.substring(0, separatorAt));
        String signature = decode(ticket.substring(separatorAt + 1));
        if (isFalse(MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)))) {
            throw new TicketRejectedException("Подпись билета не сходится");
        }
        String[] fields = value.split("\\" + FIELD_SEPARATOR);
        if (fields.length != FIELD_COUNT) {
            throw new TicketRejectedException("Состав билета не тот");
        }
        if (Instant.ofEpochSecond(Long.parseLong(fields[2])).isBefore(Instant.now())) {
            throw new TicketRejectedException("Срок билета истёк");
        }
        return new SubscriptionTicket(fields[0], fields[1]);
    }

    /**
     * Пустой секрет означает, что выдача билетов не настроена, — и тогда
     * подписка не открывается вовсе: незаданное есть отказ, а не
     * разрешение.
     */
    private void requireConfiguredSecret() {
        if (isBlank(properties.getTicket().getSecret())) {
            throw new TicketRejectedException("Выдача билетов подписки не настроена");
        }
    }

    /**
     * Значение поля не должно нести разделитель: иначе разбор вернул бы
     * не те поля, которые подписывались.
     */
    private void requireSeparatorFree(String field) {
        if (field.contains(FIELD_SEPARATOR)) {
            throw new IllegalArgumentException("Значение билета несёт служебный разделитель");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getTicket().getSecret().getBytes(StandardCharsets.UTF_8),
                    MAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Подпись билета подписки не собирается", failure);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw new TicketRejectedException("Билет подписки не разбирается");
        }
    }
}
