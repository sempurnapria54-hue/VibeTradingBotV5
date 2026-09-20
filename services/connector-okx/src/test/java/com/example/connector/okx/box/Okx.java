package com.example.connector.okx.box;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ответы площадки, которыми отвечает стаб: конверт источника и записи
 * внутри него.
 *
 * <p><b>Собираются ТЕКСТОМ, а не объектом DTO источника, и это несущее.</b>
 * Половина клеток подаёт форму, которой типизованный DTO выразить не даёт:
 * конверт без поля {@code code}, свеча из восьми колонок вместо девяти,
 * обрезанный JSON. Собери мы ответ сериализацией нашего же DTO — стаб
 * отвечал бы ровно той формой, которую наш разбор и ожидает, то есть
 * мерил бы сам себя.
 *
 * <p><b>Записи от КОНТРАКТА, а не с площадки:</b> прогонов мишени
 * {@code -D} ещё не было, и цена этого названа документом кейсов
 * (§«Две мишени и суффикс метки»).
 */
final class Okx {

    /** Код успешного ответа площадки. */
    static final String SUCCESS = "0";

    private Okx() {
    }

    /** Конверт успеха с названными записями. */
    static String ok(String... records) {
        return envelope(SUCCESS, "", records);
    }

    /** Конверт с названным кодом и записями. */
    static String envelope(String code, String message, String... records) {
        return "{\"code\":\"" + code + "\",\"msg\":\"" + message + "\",\"data\":["
                + String.join(",", records) + "]}";
    }

    /** Конверт отказа: код и сообщение есть, записей нет. */
    static String failure(String code, String message) {
        return envelope(code, message);
    }

    /** Конверт без поля {@code code} вовсе: вход клетки {@code B7.4}. */
    static String envelopeWithoutCode(String... records) {
        return "{\"msg\":\"нет поля кода\",\"data\":[" + String.join(",", records) + "]}";
    }

    /** Запись из названных полей; пустые значения в форму не попадают. */
    static Record record(String... namesAndValues) {
        return new Record().with(namesAndValues);
    }

    /** Подтверждение приёма команды: принято. */
    static String acceptedAck(String externalId, String internalId) {
        return record("ordId", externalId, "clOrdId", internalId,
                "sCode", SUCCESS, "sMsg", "", "ts", "1758240000000").text();
    }

    /** Подтверждение приёма условной команды: принято. */
    static String acceptedAlgoAck(String externalId, String internalId) {
        return record("algoId", externalId, "algoClOrdId", internalId,
                "sCode", SUCCESS, "sMsg", "").text();
    }

    /** Заявка площадки: обязательные поля чтения плюс названный сырой статус. */
    static Record order(String instrumentId, String externalId, String internalId, String state) {
        return record("instId", instrumentId, "ordId", externalId, "clOrdId", internalId,
                "ordType", "limit", "side", "buy", "state", state,
                "px", "50000", "sz", "1", "accFillSz", "0", "avgPx", "",
                "cTime", "1758240000000", "uTime", "1758240001000");
    }

    /** Условная заявка площадки с названным сырым статусом. */
    static Record algoOrder(String instrumentId, String externalId, String internalId, String state) {
        return record("instId", instrumentId, "algoId", externalId, "algoClOrdId", internalId,
                "state", state, "sz", "1", "slTriggerPx", "49000", "slTriggerPxType", "mark",
                "cTime", "1758240000000", "uTime", "1758240001000");
    }

    /** Живая позиция инструмента. */
    static Record position(String instrumentId, String size) {
        return record("instId", instrumentId, "instType", "SWAP", "posId", "pos-1",
                "pos", size, "avgPx", "50000", "markPx", "50100", "lever", "5",
                "mgnMode", "isolated", "posSide", "net",
                "cTime", "1758240000000", "uTime", "1758240001000");
    }

    /** Запись закрытого эпизода позиции. */
    static Record closedPosition(String instrumentId) {
        return record("instId", instrumentId, "posId", "pos-1", "direction", "long",
                "realizedPnl", "12.5", "ccy", "USDT", "closeAvgPx", "50500",
                "pnl", "12.5", "fee", "-0.5", "fundingFee", "-0.1", "liqPenalty", "0",
                "type", "2", "cTime", "1758240000000", "uTime", "1758240002000");
    }

    /** Инструмент справочника площадки. */
    static Record instrument(String instrumentId) {
        return record("instId", instrumentId, "instType", "SWAP", "baseCcy", "BTC",
                "quoteCcy", "USDT", "settleCcy", "USDT", "lotSz", "1", "minSz", "1",
                "ctVal", "0.01", "ctValCcy", "BTC", "ctMult", "1", "ctType", "linear",
                "tickSz", "0.1", "maxLmtSz", "100000", "maxMktSz", "10000",
                "maxTriggerSz", "10000", "maxStopSz", "10000", "state", "live", "lever", "50");
    }

    /** Тикер инструмента. */
    static Record ticker(String instrumentId, String last) {
        return record("instType", "SWAP", "instId", instrumentId, "last", last,
                "askPx", "50010", "bidPx", "49990", "askSz", "3", "bidSz", "4",
                "ts", "1758240000000", "vol24h", "1000");
    }

    /** Строка свечи площадки: девять колонок. */
    static String candle(String openMillis, String confirm) {
        return "[\"" + openMillis + "\",\"50000\",\"50500\",\"49500\",\"50200\","
                + "\"10\",\"20\",\"30\",\"" + confirm + "\"]";
    }

    /** Строка свечи ИНДЕКСА: шесть колонок, объёма у индекса нет. */
    static String indexCandle(String openMillis, String confirm) {
        return "[\"" + openMillis + "\",\"50000\",\"50500\",\"49500\",\"50200\",\"" + confirm + "\"]";
    }

    /** Запись площадки: имя поля → значение, порядок сохраняется. */
    static final class Record {

        private final Map<String, String> fields = new LinkedHashMap<>();

        /** Добавляет либо заменяет названные поля парами «имя, значение». */
        Record with(String... namesAndValues) {
            for (int index = 0; index < namesAndValues.length; index += 2) {
                fields.put(namesAndValues[index], namesAndValues[index + 1]);
            }
            return this;
        }

        /** Убирает названные поля: ими подаются входы «обязательного поля нет». */
        Record without(String... names) {
            Arrays.stream(names).forEach(fields::remove);
            return this;
        }

        /** Запись как текст JSON. */
        String text() {
            return fields.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
        }
    }
}
