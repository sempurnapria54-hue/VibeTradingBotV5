package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов — группа {@code B10} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Группа мерит НЕ-построенное, и без неё клейм «стейтлесс»
 * держался бы чтением.</b> У коннектора нет базы, нет брокера, нет
 * вызовов к соседям и нет памяти между вызовами, кроме кэша ключей;
 * каждое из этих отрицаний наблюдаемо — контейнер базы прогоном не
 * поднят вовсе, брокер не поднят, стаб соседа поднят и молчит.
 *
 * <p><b>Отрицание «базы нет» стои́т на СУБСТРАТЕ, а не на ассерте.</b>
 * Прогон не поднимает СУБД ни одним контейнером ({@link ConnectorSubstrate}),
 * и всякая зелёная клетка этого пакета есть доказательство того, что
 * тропа прошла без неё.
 */
class AbsentOutputsBoxTest extends SharedConnectorBox {

    private static final String OTHER_INSTRUMENT = "ETH-USDT-SWAP";

    @Test
    @DisplayName("B10.1 — базы у сервиса нет вовсе")
    void b10_1_theServiceHasNoDatabaseAtAll() {
        answerEveryRead();

        assertThat(get(account("/positions")).status()).isEqualTo(200);
        assertThat(get(market("/instruments?externalInstrumentType=SWAP")).status()).isEqualTo(200);
        assertThat(get(market("/time")).status()).isEqualTo(200);
        assertThat(getAnonymously("/actuator/health").status()).isEqualTo(200);
        assertThat(getAnonymously("/actuator/health").body()).doesNotContain("\"db\"", "dataSource");
    }

    @Test
    @DisplayName("B10.2 — событий сервис не публикует и не потребляет")
    void b10_2_theServiceNeitherPublishesNorConsumesEvents() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH,
                Okx.ok(Okx.acceptedAck("ord-1", Bodies.ORDER_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ORDER_PATH,
                Okx.ok(Okx.acceptedAck(Bodies.ORDER_EXTERNAL_ID, Bodies.ORDER_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CLOSE_POSITION_PATH, Okx.ok(Okx.acceptedAck("ord-c", "")));
        exchange.answers(OkxConstants.ACCOUNT_SET_LEVERAGE_PATH, Okx.ok(Okx.record(
                "instId", INSTRUMENT, "lever", "5", "mgnMode", "isolated", "posSide", "net").text()));

        String instrument = "?externalInstrumentId=" + INSTRUMENT;
        assertThat(post(account("/orders" + instrument), Bodies.limitOrder()).status()).isEqualTo(200);
        assertThat(post(account("/orders/cancellations" + instrument), Bodies.orderToCancel())
                .status()).isEqualTo(200);
        assertThat(post(account("/algo-orders" + instrument), Bodies.algoOrderToPlace("STOP_LOSS"))
                .status()).isEqualTo(200);
        assertThat(post(account("/algo-orders/cancellations" + instrument),
                Bodies.algoOrderToCancel("STOP_LOSS")).status()).isEqualTo(200);
        assertThat(post(account("/attached-protections/cancellations" + instrument),
                Bodies.attachedProtectionToCancel()).status()).isEqualTo(200);
        assertThat(post(account("/positions/closures" + instrument + "&settleCurrency=USDT"), "")
                .status()).isEqualTo(200);
        assertThat(post(account("/leverage" + instrument + "&leverage=5"), "").status()).isEqualTo(200);

        assertThat(getAnonymously("/actuator/health").body()).doesNotContain("kafka", "outbox");
    }

    @Test
    @DisplayName("B10.3 — соседей по системе сервис не зовёт")
    void b10_3_theServiceCallsNoNeighbours() {
        answerEveryRead();

        get(account("/positions"));
        get(market("/instruments?externalInstrumentType=SWAP"));
        get(market("/time"));
        getAnonymously("/actuator/health");

        assertThat(neighbour.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B10.4 — сырая форма источника за границу не выходит")
    void b10_4_theRawSourceShapeDoesNotCrossTheBoundary() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(Okx
                .order(INSTRUMENT, "ord-1", "vtb-1", "live")
                .with("reduceOnly", "true", "tgtCcy", "quote_ccy", "quickMgnType", "manual").text()));
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok(Okx
                .position(INSTRUMENT, "3").with("adl", "1", "bizRefId", "x").text()));
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(Okx
                .instrument(INSTRUMENT).with("listTime", "1758240000000").text()));
        exchange.answers(OkxConstants.MARKET_TICKERS_PATH, Okx.ok(Okx
                .ticker(INSTRUMENT, "50000").with("sodUtc0", "1").text()));
        exchange.answers(OkxConstants.CANDLES_PATH, Okx.ok(Okx.candle("1758240000000", "1")));
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH, Okx.ok(Okx
                .algoOrder(INSTRUMENT, "algo-1", "vtb-algo-1", "live").with("closeFraction", "1").text()));

        List<Answer> answers = List.of(
                get(account("/orders/pending/instrument?externalInstrumentId=" + INSTRUMENT)),
                get(account("/algo-orders/lookup?externalInstrumentId=" + INSTRUMENT
                        + "&externalId=algo-1")),
                get(account("/positions")),
                get(market("/instruments?externalInstrumentType=SWAP")),
                get(market("/tickers?externalInstrumentType=SWAP")),
                get(market("/candles?externalInstrumentId=" + INSTRUMENT
                        + "&timeframe=ONE_MINUTE&limit=3")));

        // Охрана предмета: на теле ОТКАЗА отрицание держалось бы вхолостую —
        // чужих полей там нет по построению, а клетка утверждает о чтении.
        answers.forEach(answer -> assertThat(answer.status()).isEqualTo(200));
        for (String body : answers.stream().map(Answer::body).toList()) {
            assertThat(body).doesNotContain("\"msg\"", "\"data\"", "reduceOnly", "tgtCcy",
                    "quickMgnType", "adl", "bizRefId", "listTime", "sodUtc0", "closeFraction",
                    "confirm", "instId");
        }
    }

    @Test
    @DisplayName("B10.5 — между вызовами сервис не помнит ничего, кроме ключей")
    void b10_5_theServiceRemembersNothingBetweenCallsButKeys() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(
                Okx.order(INSTRUMENT, "ord-first", "vtb-first", "live").text()));

        Answer first = get(account("/orders/pending"));

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.asList()).hasSize(1);
        assertThat(first.single().get("externalId")).isEqualTo("ord-first");

        exchange.reset();
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(
                Okx.order(OTHER_INSTRUMENT, "ord-second", "vtb-second", "live").text()));

        Answer second = get(account("/orders/pending"));

        assertThat(second.status()).isEqualTo(200);
        Map<String, Object> order = second.single();
        assertThat(order.get("externalId")).isEqualTo("ord-second");
        assertThat(order.get("externalInstrumentId")).isEqualTo(OTHER_INSTRUMENT);
        assertThat(second.body()).doesNotContain("ord-first");
    }

    /** Штатные ответы площадки на тропы, которыми ходит эта группа. */
    private void answerEveryRead() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok(Okx.position(INSTRUMENT, "1").text()));
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(Okx.instrument(INSTRUMENT).text()));
        exchange.answers(OkxConstants.PUBLIC_TIME_PATH, Okx.ok(Okx.record("ts", "1758240000000").text()));
    }
}
