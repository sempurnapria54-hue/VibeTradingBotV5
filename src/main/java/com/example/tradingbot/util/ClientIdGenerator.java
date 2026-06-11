package com.example.tradingbot.util;

import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Генератор stable client id (internalId) создаваемых на бирже сущностей
 * (Order / AlgoOrder / attached): 32 hex-символа без разделителей —
 * укладывается в ограничения OKX clOrdId/algoClOrdId. Генерируется
 * CREATE-исполнителем один раз и персистится; SUBMIT-recovery ищет по
 * нему.
 */
@UtilityClass
public class ClientIdGenerator {

    private static final String DASH = "-";
    private static final String EMPTY = "";

    public static String generate() {
        return UUID.randomUUID().toString().replace(DASH, EMPTY);
    }
}
