package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Публичные чтения — группа {@code B6} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Класс операции определяется вопросом «нужны ли ключи», а не тем,
 * кто её зовёт.</b> Отсюда и наблюдаемое: у публичного запроса нет
 * заголовков подписи, нет заголовка контура и нет похода в хранилище —
 * отсутствие ключей публичному чтению не мешает вовсе.
 *
 * <p><b>Незакрытый бар наружу не выходит, и фильтр стои́т ЗДЕСЬ.</b>
 * Читатель, получив незакрытый бар неотличимым от закрытого, записал бы
 * его в историю, и всякий расчёт по ней получил бы look-ahead: ошибка в
 * разрешающую сторону ({@code docs/concept.md} П1).
 */
class PublicReadsBoxTest extends SharedConnectorBox {

    private static final String OTHER_INSTRUMENT = "ETH-USDT-SWAP";

    private static final String INDEX_INSTRUMENT = "BTC-USDT";

    private static final String CANDLES = "/candles?externalInstrumentId=" + INSTRUMENT
            + "&timeframe=ONE_MINUTE&limit=3";

    @Test
    @DisplayName("B6.1 — публичное чтение счёта не несёт и не подписывается")
    void b6_1_aPublicReadCarriesNoAccountAndIsNotSigned() {
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(
                Okx.instrument(INSTRUMENT).text(), Okx.instrument(OTHER_INSTRUMENT).text()));
        Integer readsBefore = secrets.reads();

        Answer answer = get(market("/instruments?externalInstrumentType=SWAP"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(2);
        LoggedRequest sent = exchange.single(OkxConstants.INSTRUMENTS_PATH);
        assertThat(sent.containsHeader(OkxConstants.ACCESS_KEY_HEADER)).isFalse();
        assertThat(sent.containsHeader(OkxConstants.ACCESS_SIGN_HEADER)).isFalse();
        assertThat(sent.containsHeader(OkxConstants.ACCESS_TIMESTAMP_HEADER)).isFalse();
        assertThat(sent.containsHeader(OkxConstants.ACCESS_PASSPHRASE_HEADER)).isFalse();
        assertThat(sent.containsHeader(OkxConstants.SIMULATED_HEADER)).isFalse();
        assertThat(secrets.reads() - readsBefore).isEqualTo(0);
    }

    @Test
    @DisplayName("B6.2 — правила инструмента — отдельная операция")
    void b6_2_instrumentRulesAreASeparateOperation() {
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(Okx.instrument(INSTRUMENT).text()));

        Answer rules = get(market("/instruments/" + INSTRUMENT + "/rules?externalInstrumentType=SWAP"));
        Answer instrument = get(market("/instruments/" + INSTRUMENT + "?externalInstrumentType=SWAP"));

        assertThat(rules.status()).isEqualTo(200);
        assertThat(instrument.status()).isEqualTo(200);
        assertThat(rules.asObject()).containsKeys("externalTickSize", "externalLotSize", "externalMinSize");
        assertThat(instrument.asObject()).doesNotContainKeys("externalTickSize", "externalLotSize");
        assertThat(exchange.requests(OkxConstants.INSTRUMENTS_PATH)).hasSize(2);
        exchange.requests(OkxConstants.INSTRUMENTS_PATH).forEach(request ->
                assertThat(request.getUrl()).contains("instId=" + INSTRUMENT));
    }

    @Test
    @DisplayName("B6.3 — незакрытый бар наружу не выходит")
    void b6_3_anUnclosedBarDoesNotTravelOut() {
        exchange.answers(OkxConstants.CANDLES_PATH, Okx.ok(
                Okx.candle("1758240000000", "1"),
                Okx.candle("1758240060000", "1"),
                Okx.candle("1758240120000", "0")));
        exchange.answers(OkxConstants.HISTORY_CANDLES_PATH, Okx.ok(
                Okx.candle("1758230000000", "1"),
                Okx.candle("1758230060000", "0")));

        Answer latest = get(market(CANDLES));
        Answer history = get(market("/candles/history?externalInstrumentId=" + INSTRUMENT
                + "&timeframe=ONE_MINUTE&afterMillis=1758240000000&limit=3"));

        assertThat(latest.asList()).hasSize(2);
        assertThat(history.asList()).hasSize(1);
        assertThat(latest.body()).doesNotContain("confirm");
    }

    /**
     * Ожидание взято из дома: перечень классов границы закрыт, и
     * «негодного входа» в нём нет — ответ ПЛОЩАДКИ пришёл не той формы,
     * а запрос вызывающего корректен: ответ разобран конвертом, но строка
     * нарушает форму контракта.
     */
    @Test
    @DisplayName("B6.4 — свеча источника разбирается по длине массива")
    void b6_4_aSourceCandleIsParsedByArrayLength() {
        exchange.answers(OkxConstants.CANDLES_PATH, Okx.ok(
                "[\"1758240000000\",\"50000\",\"50500\",\"49500\",\"50200\",\"10\",\"20\",\"1\"]"));

        Answer answer = get(market(CANDLES));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isNotEqualTo("INVALID_REQUEST");
        assertThat(answer.errorCode()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
    }

    /** Короткая строка индекса — тот же отказ разбора чужого ответа, что у {@code B6.4}. */
    @Test
    @DisplayName("B6.5 — свеча индекса читается своей формой строки")
    void b6_5_anIndexCandleIsReadByItsOwnRowShape() {
        exchange.answers(OkxConstants.HISTORY_INDEX_CANDLES_PATH,
                Okx.ok(Okx.indexCandle("1758240000000", "1")));

        Answer answer = get(market("/candles/index?indexInstrumentId=" + INDEX_INSTRUMENT
                + "&timeframe=ONE_MINUTE&at=2026-09-19T00:00:00Z"));

        assertThat(answer.status()).isEqualTo(200);
        LoggedRequest sent = exchange.single(OkxConstants.HISTORY_INDEX_CANDLES_PATH);
        assertThat(sent.getUrl()).contains("instId=" + INDEX_INSTRUMENT, "limit=1",
                "after=" + (java.time.Instant.parse("2026-09-19T00:00:00Z").toEpochMilli() + 1L));

        exchange.reset();
        exchange.answers(OkxConstants.HISTORY_INDEX_CANDLES_PATH,
                Okx.ok("[\"1758240000000\",\"50000\",\"50500\",\"49500\",\"50200\"]"));

        Answer short_ = get(market("/candles/index?indexInstrumentId=" + INDEX_INSTRUMENT
                + "&timeframe=ONE_MINUTE&at=2026-09-19T00:00:00Z"));

        assertThat(short_.carriesErrorDto()).isTrue();
        assertThat(short_.errorCode()).isNotEqualTo("INVALID_REQUEST");
        assertThat(short_.errorCode()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
    }

    @Test
    @DisplayName("B6.6 — дубль инструмента в срезе тикеров схлопывается, а не роняет чтение")
    void b6_6_aDuplicateInTheTickerSliceCollapsesInsteadOfFailing() {
        exchange.answers(OkxConstants.MARKET_TICKERS_PATH, Okx.ok(
                Okx.ticker(INSTRUMENT, "50000").text(),
                Okx.ticker(OTHER_INSTRUMENT, "3000").text(),
                Okx.ticker(INSTRUMENT, "99999").text()));

        Answer answer = get(market("/tickers?externalInstrumentType=SWAP"));

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> tickers = answer.asObject();
        assertThat(tickers).hasSize(2).containsKeys(INSTRUMENT, OTHER_INSTRUMENT);
        assertThat(String.valueOf(tickers.get(INSTRUMENT))).contains("50000");
        assertThat(String.valueOf(tickers.get(INSTRUMENT))).doesNotContain("99999");
    }

    @Test
    @DisplayName("B6.7 — марк-цены и цены индексов отдаются картами, строка без цены выпадает")
    void b6_7_markAndIndexPricesAreMapsAndAPricelessRowDropsOut() {
        exchange.answers(OkxConstants.MARK_PRICE_PATH, Okx.ok(
                Okx.record("instId", INSTRUMENT, "markPx", "50100", "ts", "1758240000000").text(),
                Okx.record("instId", OTHER_INSTRUMENT, "markPx", "", "ts", "1758240000000").text()));
        exchange.answers(OkxConstants.INDEX_TICKERS_PATH, Okx.ok(
                Okx.record("instId", INDEX_INSTRUMENT, "idxPx", "50050", "ts", "1758240000000").text(),
                Okx.record("instId", "ETH-USDT", "idxPx", "", "ts", "1758240000000").text()));

        Answer marks = get(market("/mark-prices?externalInstrumentType=SWAP"));
        Answer indexes = get(market("/index-prices?quoteCurrency=USDT"));

        assertThat(marks.asObject()).hasSize(1).containsKey(INSTRUMENT);
        assertThat(indexes.asObject()).hasSize(1).containsKey(INDEX_INSTRUMENT);

        exchange.reset();
        exchange.answers(OkxConstants.MARK_PRICE_PATH, Okx.ok());
        assertThat(get(market("/mark-prices?externalInstrumentType=SWAP")).asObject()).isEmpty();
    }

    @Test
    @DisplayName("B6.8 — книга заявок читается на заданную глубину")
    void b6_8_theOrderBookIsReadToTheGivenDepth() {
        exchange.answers(OkxConstants.MARKET_BOOKS_PATH, Okx.ok(
                "{\"asks\":[[\"50010\",\"1\",\"0\",\"1\"]],\"bids\":[[\"49990\",\"2\",\"0\",\"1\"]],"
                        + "\"ts\":\"1758240000000\"}"));

        Answer answer = get(market("/order-book/" + INSTRUMENT + "?depth=20"));

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> book = answer.asObject();
        assertThat(book).containsKeys("asks", "bids");
        assertThat(book.get("externalTimestamp")).isEqualTo(1758240000000L);
        LoggedRequest sent = exchange.single(OkxConstants.MARKET_BOOKS_PATH);
        assertThat(sent.getUrl()).contains("instId=" + INSTRUMENT, "sz=20");

        exchange.reset();
        exchange.answers(OkxConstants.MARKET_BOOKS_PATH, Okx.ok());
        Answer empty = get(market("/order-book/" + INSTRUMENT + "?depth=20"));
        assertThat(empty.status()).isEqualTo(200);
        assertThat(empty.body()).isBlank();
    }

    @Test
    @DisplayName("B6.9 — время площадки: пустой ответ нарушает инвариант")
    void b6_9_serverTimeWithAnEmptyAnswerViolatesTheInvariant() {
        exchange.answers(OkxConstants.PUBLIC_TIME_PATH, Okx.ok());

        Answer empty = get(market("/time"));

        assertThat(empty.errorCode()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");

        exchange.reset();
        exchange.answers(OkxConstants.PUBLIC_TIME_PATH,
                Okx.ok(Okx.record("ts", "1758240000000").text()));

        Answer moment = get(market("/time"));

        assertThat(moment.status()).isEqualTo(200);
        assertThat(moment.body()).contains("2025-09-19");
    }

    @Test
    @DisplayName("B6.10 — цены момента собираются одним чтением тикера")
    void b6_10_spotPricesAreCollectedByOneTickerRead() {
        exchange.answers(OkxConstants.MARKET_TICKER_PATH,
                Okx.ok(Okx.ticker(INSTRUMENT, "50000").text()));

        Answer answer = get(market("/prices/" + INSTRUMENT));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(String.valueOf(answer.asObject().get("externalLastPrice"))).contains("50000");
        List<LoggedRequest> sent = exchange.requests();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getUrl()).contains(OkxConstants.MARKET_TICKER_PATH,
                "instId=" + INSTRUMENT);
    }
}
