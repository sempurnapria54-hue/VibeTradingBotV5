package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Добыча факта: условные заявки и защиты — группа {@code B4} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Род условия — ОПЕРАНД запроса, а не удобство.</b> У эндпоинта
 * живых условных заявок {@code ordType} обязателен, и запрос без него
 * вернул бы не «все», а одну произвольную семью; отсюда и форма
 * счёт-широкого среза — вызов на каждую ставимую контуром семью
 * ({@code docs/models/mapping/AlgoOrder.md}).
 *
 * <p><b>Усечение мерится у КАЖДОЙ страницы, а не у склейки.</b> Потолок
 * задан на вызов, поэтому сумма трёх семей превышает его штатно: мера по
 * склейке объявляла бы полностью добытый срез неполным — и поднимала бы
 * биржевую ступень на здоровом проходе.
 */
class AlgoFactsBoxTest extends SharedConnectorBox {

    private static final String PENDING_ALL = "/algo-orders/pending";

    private static final String PENDING_INSTRUMENT = "/algo-orders/pending/instrument?externalInstrumentId="
            + INSTRUMENT;

    private static final String HISTORY = "/algo-orders/history?externalInstrumentId=" + INSTRUMENT;

    private static final String LOOKUP = "/algo-orders/lookup?externalInstrumentId=" + INSTRUMENT;

    private static final String PROTECTIONS_PENDING =
            "/attached-protections/pending?externalInstrumentId=" + INSTRUMENT;

    private static final String PROTECTIONS_HISTORY =
            "/attached-protections/history?externalInstrumentId=" + INSTRUMENT;

    @Test
    @DisplayName("B4.1 — род условия — операнд запроса, а не удобство")
    void b4_1_theConditionKindIsARequestOperand() {
        exchange.answers(OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH, Okx.ok(Okx
                .algoOrder(INSTRUMENT, Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID, "live").text()));

        Answer answer = get(account(PENDING_INSTRUMENT + "&conditionType=STOP_LOSS"));

        assertThat(answer.status()).isEqualTo(200);
        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH);
        assertThat(sent.getUrl()).contains("instId=" + INSTRUMENT, "ordType=conditional");

        assertThat(get(account(PENDING_INSTRUMENT)).status()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("B4.2 — счёт-широкий срез algo складывается из трёх вызовов по семьям")
    void b4_2_theAccountWideAlgoSliceIsMadeOfThreeFamilyCalls() {
        answerFamily("conditional", 1);
        answerFamily("oco", 1);
        answerFamily("move_order_stop", 1);

        Answer answer = get(account(PENDING_ALL));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(3);
        List<LoggedRequest> sent = exchange.requests(OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH);
        assertThat(sent).hasSize(3);
        assertThat(sent.stream().map(request -> familyOf(request)).toList())
                .containsExactly("conditional", "oco", "move_order_stop");
        sent.forEach(request -> assertThat(request.getUrl()).contains("instType=SWAP", "limit=100"));
    }

    @Test
    @DisplayName("B4.3 — усечение проверяется у каждой страницы, а не у склейки")
    void b4_3_truncationIsCheckedPerPageNotPerJoin() {
        answerFamily("conditional", 60);
        answerFamily("oco", 60);
        answerFamily("move_order_stop", 60);

        Answer joined = get(account(PENDING_ALL));

        assertThat(joined.status()).isEqualTo(200);
        assertThat(joined.asList()).hasSize(180);

        exchange.reset();
        answerFamily("conditional", 100);
        answerFamily("oco", 1);
        answerFamily("move_order_stop", 1);

        Answer truncated = get(account(PENDING_ALL));

        assertThat(truncated.carriesErrorDto()).isTrue();
        assertThat(truncated.errorCode()).isEqualTo("EXCHANGE_ERROR");
        assertThat(String.valueOf(truncated.asObject().get("message")))
                .contains("orders-algo-pending", "100");
    }

    @Test
    @DisplayName("B4.4 — история algo с идентификатором читается одним вызовом")
    void b4_4_algoHistoryWithIdentifierIsReadByOneCall() {
        exchange.answers(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, Okx.ok(Okx
                .algoOrder(INSTRUMENT, Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID, "effective").text()));

        Answer answer = get(account(HISTORY + "&conditionType=STOP_LOSS&externalId="
                + Bodies.ALGO_EXTERNAL_ID));

        assertThat(answer.asList()).hasSize(1);
        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH);
        assertThat(sent.getUrl()).contains("instId=" + INSTRUMENT, "ordType=conditional",
                "algoId=" + Bodies.ALGO_EXTERNAL_ID);
        assertThat(sent.getUrl()).doesNotContain("state=");
    }

    @Test
    @DisplayName("B4.5 — история algo без идентификатора читается двумя терминальными состояниями")
    void b4_5_algoHistoryWithoutIdentifierIsReadByTwoTerminalStates() {
        exchange.answersWhen(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, "state", "effective",
                Okx.ok(Okx.algoOrder(INSTRUMENT, "algo-eff", "vtb-eff", "effective").text()));
        exchange.answersWhen(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, "state", "canceled",
                Okx.ok(Okx.algoOrder(INSTRUMENT, "algo-can", "vtb-can", "canceled").text()));

        Answer answer = get(account(HISTORY + "&conditionType=STOP_LOSS"));

        assertThat(answer.asList()).hasSize(2);
        List<LoggedRequest> sent = exchange.requests(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH);
        assertThat(sent).hasSize(2);
        assertThat(sent.stream().map(request -> stateOf(request)).toList())
                .containsExactly("effective", "canceled");
        sent.forEach(request -> assertThat(request.getUrl()).doesNotContain("algoId="));
    }

    @Test
    @DisplayName("B4.6 — живые материализованные защиты читаются только обычной семьёй")
    void b4_6_pendingMaterializedProtectionsAreReadByTheOrdinaryFamilyOnly() {
        exchange.answers(OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH, Okx.ok(Okx
                .algoOrder(INSTRUMENT, "algo-att-1", "vtb-att-1", "live").text()));

        Answer answer = get(account(PROTECTIONS_PENDING));

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> protection = answer.single();
        assertThat(protection.get("internalId")).isEqualTo("vtb-att-1");
        assertThat(protection.get("stopLossTriggerPrice")).isNotNull();
        assertThat(protection.get("triggerPriceType")).isEqualTo("MARK");
        assertThat(protection.get("status")).isNull();
        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH);
        assertThat(sent.getUrl()).contains("ordType=conditional");
    }

    @Test
    @DisplayName("B4.7 — нога истории защит выбирает состояние запроса")
    void b4_7_theProtectionHistoryLegChoosesTheRequestState() {
        exchange.answersWhen(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, "state", "effective",
                Okx.ok(Okx.algoOrder(INSTRUMENT, "algo-eff", "vtb-att-1", "effective").text()));
        exchange.answersWhen(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, "state", "canceled",
                Okx.ok(Okx.algoOrder(INSTRUMENT, "algo-can", "vtb-att-1", "canceled").text()));
        exchange.answersWhen(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH, "state", "order_failed",
                Okx.ok(Okx.algoOrder(INSTRUMENT, "algo-fail", "vtb-att-1", "order_failed").text()));

        assertThat(get(account(PROTECTIONS_HISTORY + "&leg=EFFECTIVE")).status()).isEqualTo(200);
        assertThat(get(account(PROTECTIONS_HISTORY + "&leg=CANCELED")).status()).isEqualTo(200);
        assertThat(get(account(PROTECTIONS_HISTORY + "&leg=ORDER_FAILED")).status()).isEqualTo(200);

        assertThat(exchange.requests(OkxConstants.TRADE_ORDERS_ALGO_HISTORY_PATH).stream()
                .map(request -> stateOf(request)).toList())
                .containsExactly("effective", "canceled", "order_failed");
    }

    @Test
    @DisplayName("B4.8 — проблемный статус algo несёт свою причину")
    void b4_8_aProblematicAlgoStatusCarriesItsOwnReason() {
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH, Okx.ok(Okx
                .algoOrder(INSTRUMENT, Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID, "order_failed").text()));

        Answer failed = get(account(LOOKUP + "&externalId=" + Bodies.ALGO_EXTERNAL_ID));

        assertThat(failed.errorCode()).isEqualTo("EXTERNAL_STATUS");
        assertThat(failed.errorReason()).isEqualTo("ORDER_FAILED");
        assertThat(failed.body()).doesNotContain("\"conditionType\"");

        exchange.reset();
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH, Okx.ok(Okx
                .algoOrder(INSTRUMENT, Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID,
                        "partially_failed").text()));

        Answer partial = get(account(LOOKUP + "&externalId=" + Bodies.ALGO_EXTERNAL_ID));

        assertThat(partial.errorCode()).isEqualTo("EXTERNAL_STATUS");
        assertThat(partial.errorReason()).isEqualTo("PARTIALLY_FAILED");
    }

    /** Ответ на живые условные заявки названной семьи: названное число записей. */
    private void answerFamily(String family, Integer size) {
        String[] records = IntStream.range(0, size)
                .mapToObj(index -> Okx.algoOrder(INSTRUMENT, family + "-" + index,
                        "vtb-" + family + "-" + index, "live").text())
                .toArray(String[]::new);
        exchange.answersWhen(OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH, "ordType", family,
                Okx.ok(records));
    }

    /** Значение {@code ordType} ушедшего запроса. */
    private static String familyOf(LoggedRequest request) {
        return request.queryParameter("ordType").firstValue();
    }

    /** Значение {@code state} ушедшего запроса. */
    private static String stateOf(LoggedRequest request) {
        return request.queryParameter("state").firstValue();
    }
}
