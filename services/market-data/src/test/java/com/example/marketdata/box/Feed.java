package com.example.marketdata.box;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Ответы стаба коннектора — вход ящика со стороны соседа.
 *
 * <p><b>Формы собраны от КОНТРАКТА соседа</b>, а не сняты с живого
 * коннектора: предмет здесь — market-data, и ответ соседа ему вход. Что
 * сосед именно эти формы и отдаёт, мерит ящик соседа
 * (.claude/tests/cases/connector-okx.md).
 *
 * <p><b>Собирается строкой, а не сериализацией доменной модели.</b>
 * Сериализованная модель проверяла бы наш маппер против него же; строка
 * же есть то, что реально приезжает по проводу, — включая поля, которых
 * у модели нет, и пустоты, которых сериализатор не напишет.
 */
final class Feed {

    private Feed() {
    }

    /** Перечень объектов одним телом. */
    static String array(String... items) {
        return Arrays.stream(items).collect(Collectors.joining(",", "[", "]"));
    }

    /** Пустой перечень: «площадка отдала пусто». */
    static String empty() {
        return "[]";
    }

    /** Пустая карта: «карта не резолвилась ни по одному ключу». */
    static String emptyMap() {
        return "{}";
    }

    /** Инструмент листинга во всей полноте спецификации. */
    static String instrument(String externalId, String base, String quote) {
        return """
                {
                  "externalId": "%s",
                  "externalType": "SWAP",
                  "externalStatus": "live",
                  "externalBaseCurrency": "%s",
                  "externalQuoteCurrency": "%s",
                  "externalSettlementCurrency": "%s",
                  "externalMarginMode": "cross",
                  "externalLeverage": "50"
                }
                """.formatted(externalId, base, quote, quote);
    }

    /** Инструмент листинга с названным биржевым статусом. */
    static String instrumentWithState(String externalId, String base, String quote, String externalStatus) {
        return """
                {
                  "externalId": "%s",
                  "externalType": "SWAP",
                  "externalStatus": "%s",
                  "externalBaseCurrency": "%s",
                  "externalQuoteCurrency": "%s",
                  "externalSettlementCurrency": "%s"
                }
                """.formatted(externalId, externalStatus, base, quote, quote);
    }

    /**
     * Инструмент листинга без части полей спецификации: вход клетки
     * {@code B2.3} — пустота ответа известного не затирает.
     */
    static String bareInstrument(String externalId) {
        return """
                {
                  "externalId": "%s",
                  "externalType": "SWAP",
                  "externalStatus": "live"
                }
                """.formatted(externalId);
    }

    /** Справочные правила инструмента. */
    static String rules(String externalId) {
        return """
                {
                  "externalInstrumentId": "%s",
                  "externalInstrumentType": "SWAP",
                  "instrumentType": "SWAP",
                  "contractType": "LINEAR",
                  "status": "LIVE",
                  "externalContractType": "linear",
                  "externalContractValue": "0.01",
                  "externalContractValueCurrency": "BTC",
                  "externalTickSize": "0.1",
                  "externalLotSize": "0.1",
                  "externalMinSize": "0.1",
                  "externalMaxLimitSize": "1000",
                  "externalMaxMarketSize": "500",
                  "externalState": "live",
                  "externalFeeGroupId": "1"
                }
                """.formatted(externalId);
    }

    /** Закрытая свеча: открытие бара и его цены. */
    static String candle(Long openTimestamp, String close) {
        return """
                {
                  "openTimestamp": %d,
                  "open": "%s",
                  "high": "%s",
                  "low": "%s",
                  "close": "%s",
                  "volume": "10"
                }
                """.formatted(openTimestamp, close, close, close, close);
    }

    /** Ряд закрытых свечей шагом таймфрейма, начиная с названного бара. */
    static String candles(Long firstOpenTimestamp, Long stepMillis, Integer count, Integer basePrice) {
        String[] bars = new String[count];
        for (int index = 0; index < count; index++) {
            bars[index] = candle(firstOpenTimestamp + index * stepMillis, String.valueOf(basePrice + index));
        }
        return array(bars);
    }

    /**
     * Ряд закрытых свечей С ПИЛОЙ И ДРЕЙФОМ: растущие максимумы и
     * растущие минимумы на растущих закрытиях.
     *
     * <p><b>Форма ряда здесь — вход, а не украшение.</b> Классификация
     * структуры требует не менее двух свингов каждой стороны, а у ряда с
     * монотонными экстремумами локальных пиков нет вовсе: всякое окно
     * такого ряда классифицируется как {@code UNKNOWN}, и клетка «вход не
     * объявлен — тип настоящий» стала бы неотличима от клетки «вход не
     * готов — тип {@code UNKNOWN}».
     *
     * @param firstOpenTimestamp открытие первого бара
     * @param stepMillis         длительность бара
     * @param count              число баров
     * @param basePrice          цена первого бара
     * @return тело перечня свечей
     */
    static String trendingCandles(Long firstOpenTimestamp, Long stepMillis, Integer count, Integer basePrice) {
        String[] bars = new String[count];
        for (int index = 0; index < count; index++) {
            int close = basePrice + index;
            int high = close + (index % 8 == 4 ? 50 : 1);
            int low = close - (index % 8 == 0 ? 50 : 1);
            bars[index] = """
                    {
                      "openTimestamp": %d,
                      "open": "%d",
                      "high": "%d",
                      "low": "%d",
                      "close": "%d",
                      "volume": "10"
                    }
                    """.formatted(firstOpenTimestamp + index * stepMillis, close, high, low, close);
        }
        return array(bars);
    }

    /** Тикер инструмента: карта «инструмент площадки — тикер». */
    static String ticker(String externalId, Long externalTimestamp, String lastPrice) {
        return """
                "%s": {
                  "externalTimestamp": %d,
                  "lastPrice": "%s",
                  "volume": "1000"
                }
                """.formatted(externalId, externalTimestamp, lastPrice);
    }

    /**
     * Тикер БЕЗ биржевой метки времени: вход клетки {@code B5.6} — строка
     * без половины естественного ключа не пишется, и её отказ стои́т одну
     * строку, а не проход.
     */
    static String tickerWithoutTimestamp(String externalId, String lastPrice) {
        return """
                "%s": {
                  "lastPrice": "%s",
                  "volume": "1000"
                }
                """.formatted(externalId, lastPrice);
    }

    /** Карта из названных пар. */
    static String map(String... entries) {
        return Arrays.stream(entries).collect(Collectors.joining(",", "{", "}"));
    }

    /** Пара «ключ — число»: марк-цены и цены индексов. */
    static String price(String key, String value) {
        return "\"%s\": \"%s\"".formatted(key, value);
    }

    /** Книга заявок названной глубины. */
    static String orderBook(Long externalTimestamp, Integer depth) {
        String[] bids = new String[depth];
        String[] asks = new String[depth];
        for (int index = 0; index < depth; index++) {
            bids[index] = level(100 - index, index + 1);
            asks[index] = level(101 + index, index + 1);
        }
        return """
                {
                  "externalTimestamp": %d,
                  "bids": %s,
                  "asks": %s
                }
                """.formatted(externalTimestamp, array(bids), array(asks));
    }

    /** Цены момента инструмента: последняя, лучшие стороны и метка площадки. */
    static String prices(String externalId, String lastPrice) {
        return """
                {
                  "externalInstrumentId": "%s",
                  "externalInstrumentType": "SWAP",
                  "externalLastPrice": "%s",
                  "externalAskPrice": "%s",
                  "externalBidPrice": "%s",
                  "externalAskSize": "5",
                  "externalBidSize": "5",
                  "externalTimestamp": "2026-09-20T10:00:00Z"
                }
                """.formatted(externalId, lastPrice, lastPrice, lastPrice);
    }

    /** Отказ коннектора в форме его единого error-DTO. */
    static String refusal(String code) {
        return """
                {
                  "code": "%s",
                  "message": "stubbed refusal",
                  "occurredAt": "2026-09-20T10:00:00Z"
                }
                """.formatted(code);
    }

    private static String level(Integer price, Integer size) {
        return """
                {"price": "%d", "size": "%d", "orderCount": 1}
                """.formatted(price, size);
    }
}
