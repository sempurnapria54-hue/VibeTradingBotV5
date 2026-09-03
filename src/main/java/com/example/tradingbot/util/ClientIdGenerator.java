package com.example.tradingbot.util;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Генератор stable client id (internalId) сущностей: 32 hex-символа без
 * разделителей — укладывается в ограничения OKX clOrdId/algoClOrdId.
 * Генерируется CREATE-исполнителем один раз и персистится;
 * SUBMIT-recovery ищет по нему.
 *
 * <p><b>Идентификаторы, уезжающие НА БИРЖУ, несут маркер контура.</b>
 * Он и есть дискриминатор «наше против чужого» для проактивной детекции:
 * заявка, ушедшая на биржу до того, как её строка закоммичена, переживает
 * рестарт посреди операции, поэтому признак «в БД строки нет» опознавать
 * чужое не годится — по нему детектор снёс бы биржу за нашу собственную
 * заявку. Маркер — свойство стороны биржи, и наш рестарт его изменить не
 * может (docs/components/AnomalyJob.md §«Что ищет», разбор чужой заявки).
 *
 * <p>Идентификаторы, на биржу не уезжающие (сделка, транш, отчёт),
 * маркера не несут: опознавать там нечего, а общий префикс съедал бы
 * энтропию без потребителя.
 */
@UtilityClass
public class ClientIdGenerator {

    private static final String DASH = "-";
    private static final String EMPTY = "";

    /**
     * Длина случайной части у биржевого идентификатора: маркер плюс она
     * обязаны уложиться в потолок источника (32 символа).
     */
    private static final int EXCHANGE_RANDOM_LENGTH = 32 - Constants.Okx.CLIENT_ID_MARKER.length();

    public static String generate() {
        return UUID.randomUUID().toString().replace(DASH, EMPTY);
    }

    /**
     * Идентификатор для сущности, уезжающей на биржу: маркер контура плюс
     * случайная часть.
     */
    public static String generateExchangeFacing() {
        return Constants.Okx.CLIENT_ID_MARKER + generate().substring(0, EXCHANGE_RANDOM_LENGTH);
    }

    /**
     * Клиентский идентификатор поставлен нашим контуром. Пустой — не наш:
     * свои мы ставим всегда, а источник пустым его отдаёт ровно у записи,
     * которую заводили не мы.
     */
    public static Boolean isOurs(String clientId) {
        return isNotBlank(clientId) && clientId.startsWith(Constants.Okx.CLIENT_ID_MARKER);
    }
}
