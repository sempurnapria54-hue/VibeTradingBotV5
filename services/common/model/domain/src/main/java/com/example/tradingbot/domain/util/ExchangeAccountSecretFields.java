package com.example.tradingbot.domain.util;

import lombok.experimental.UtilityClass;

/**
 * Состав секрета биржевого счёта в хранилище.
 *
 * <p><b>Носитель один на два сервиса — по тому же доводу, что и
 * {@link ExchangeAccountKeyPath}.</b> Имена полей пишет {@code auth} и
 * читает коннектор; разойдись они — секрет лежал бы на месте, а читатель
 * получал бы «ключей нет», и отказ выглядел бы как незаведённый счёт.
 * Дом объявления — {@code docs/architecture/tenant-and-exchange.md}
 * §Ключи.
 *
 * <p><b>Контур лежит в секрете вместе с ключами</b>, а не рядом в базе:
 * ключ и контур суть одна истина, и разведённые по двум носителям они
 * разойдутся молча — демо-ключ уедет на боевую площадку.
 */
@UtilityClass
public class ExchangeAccountSecretFields {

    /** API-ключ счёта. */
    public static final String API_KEY = "apiKey";

    /** Секрет ключа: им подписывается запрос. */
    public static final String SECRET = "secret";

    /** Passphrase ключа. */
    public static final String PASSPHRASE = "passphrase";

    /** Контур площадки, которому ключи принадлежат: {@code LIVE} либо {@code DEMO}. */
    public static final String CONTOUR = "contour";
}
