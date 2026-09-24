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
 * Добыча факта: заявки — группа {@code B3} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Предмет группы — контракт чтения.</b> Пустой ответ означает «не
 * найдено в этом источнике», а не отказ; доменный статус резолвится здесь,
 * потому что словарь площадки знает только эта сторона; причина закрытия
 * НЕ проставляется — её операнды живут у ядра
 * ({@code docs/rules/external-status-resolution.md} §«Где резолвится —
 * сторона выбирается по словарю источника»).
 *
 * <p><b>Полная страница счёт-широкого среза есть УСЕЧЕНИЕ, а не срез.</b>
 * Граница проверки лежит ровно на потолке: 99 записей проходят, 100 —
 * отказ. Принятая за полный, усечённая страница гасит детекторы читателя —
 * то есть пропуск выглядел бы чистым проходом.
 *
 * <p><b>Стаб отвечает реальным типом исполнения площадки</b>
 * ({@code ordType}), и чтение заявки его не разбирает: род заявки наш и из
 * эха площадки не выводится ({@code docs/models/mapping/Order.md}
 * §«{@code OrderExternalSnapshot} → {@code Order}»).
 */
class OrderFactsBoxTest extends SharedConnectorBox {

    private static final String LOOKUP = "/orders/lookup?externalInstrumentId=" + INSTRUMENT;

    private static final String PENDING_ALL = "/orders/pending";

    private static final String PENDING_INSTRUMENT = "/orders/pending/instrument?externalInstrumentId="
            + INSTRUMENT;

    private static final String HISTORY = "/orders/history?externalInstrumentId=" + INSTRUMENT;

    private static final String OTHER_INSTRUMENT = "ETH-USDT-SWAP";

    @Test
    @DisplayName("B3.1 — заявка по идентификатору с резолвом доменного статуса")
    void b3_1_anOrderByIdentifierCarriesTheResolvedDomainStatus() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH, Okx.ok(Okx
                .order(INSTRUMENT, Bodies.ORDER_EXTERNAL_ID, Bodies.ORDER_INTERNAL_ID, "filled").text()));

        Answer answer = get(account(LOOKUP + "&externalId=" + Bodies.ORDER_EXTERNAL_ID));

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> order = answer.asObject();
        assertThat(order.get("status")).isEqualTo("COMPLETED");
        assertThat(order.get("closeReason")).isNull();
        assertThat(order.get("externalId")).isEqualTo(Bodies.ORDER_EXTERNAL_ID);
        assertThat(order).doesNotContainKeys("code", "msg", "data");

        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDER_PATH);
        assertThat(sent.getUrl()).contains("instId=" + INSTRUMENT,
                "ordId=" + Bodies.ORDER_EXTERNAL_ID);
        assertThat(sent.getUrl()).doesNotContain("clOrdId");
    }

    @Test
    @DisplayName("B3.2 — заявка по нашему идентификатору")
    void b3_2_anOrderByOurOwnIdentifier() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH, Okx.ok(Okx
                .order(INSTRUMENT, Bodies.ORDER_EXTERNAL_ID, Bodies.ORDER_INTERNAL_ID, "live").text()));

        Answer answer = get(account(LOOKUP + "&internalId=" + Bodies.ORDER_INTERNAL_ID));

        assertThat(answer.asObject().get("status")).isEqualTo("ACTIVE");
        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDER_PATH);
        assertThat(sent.getUrl()).contains("instId=" + INSTRUMENT,
                "clOrdId=" + Bodies.ORDER_INTERNAL_ID);
        assertThat(sent.getUrl()).doesNotContain("ordId=");
    }

    @Test
    @DisplayName("B3.3 — «не найдено» — это пусто, а не отказ")
    void b3_3_notFoundMeansEmptyNotFailure() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH, Okx.ok());
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok());

        Answer single = get(account(LOOKUP + "&externalId=" + Bodies.ORDER_EXTERNAL_ID));
        Answer many = get(account(PENDING_INSTRUMENT));

        assertThat(single.status()).isEqualTo(200);
        assertThat(single.body()).isBlank();
        assertThat(many.status()).isEqualTo(200);
        assertThat(many.asList()).isEmpty();
        assertThat(exchange.requests(OkxConstants.TRADE_ORDER_PATH)).hasSize(1);
        assertThat(exchange.requests(OkxConstants.TRADE_ORDERS_PENDING_PATH)).hasSize(1);
    }

    @Test
    @DisplayName("B3.4 — неизвестный сырой статус роняет весь ответ и несёт причину полем")
    void b3_4_anUnknownRawStatusDropsTheWholeAnswerAndCarriesItsReason() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(
                Okx.order(INSTRUMENT, "ord-1", "vtb-1", "live").text(),
                Okx.order(INSTRUMENT, "ord-2", "vtb-2", "super_filled").text(),
                Okx.order(INSTRUMENT, "ord-3", "vtb-3", "live").text()));

        Answer answer = get(account(PENDING_INSTRUMENT));

        assertThat(answer.errorCode()).isEqualTo("EXTERNAL_STATUS");
        assertThat(answer.errorReason()).isEqualTo("UNKNOWN_EXTERNAL_STATUS");
        assertThat(answer.body()).doesNotContain("ord-1", "ord-3");
    }

    @Test
    @DisplayName("B3.5 — счёт-широкий срез несёт биржевое имя инструмента")
    void b3_5_theAccountWideSliceCarriesTheExchangeInstrumentName() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(
                Okx.order(INSTRUMENT, "ord-1", "vtb-1", "live").text(),
                Okx.order(OTHER_INSTRUMENT, "ord-2", "vtb-2", "live").text()));

        Answer answer = get(account(PENDING_ALL));

        assertThat(answer.status()).isEqualTo(200);
        List<Map<String, Object>> orders = answer.asList();
        assertThat(orders).hasSize(2);
        assertThat(orders.stream().map(order -> order.get("externalInstrumentId")).toList())
                .containsExactly(INSTRUMENT, OTHER_INSTRUMENT);
        orders.forEach(order -> assertThat(order.get("instrumentId")).isNull());

        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDERS_PENDING_PATH);
        assertThat(sent.getUrl()).contains("instType=SWAP", "limit=100");
        assertThat(sent.getUrl()).doesNotContain("instId=");
    }

    @Test
    @DisplayName("B3.6 — полная страница среза объявляется усечением, а не полным срезом")
    void b3_6_aFullPageIsDeclaredTruncatedNotComplete() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(orders(100)));

        Answer answer = get(account(PENDING_ALL));

        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ERROR");
        assertThat(String.valueOf(answer.asObject().get("message")))
                .contains("orders-pending", "100");
        assertThat(answer.body()).doesNotContain("\"externalId\"");
    }

    @Test
    @DisplayName("B3.7 — страница меньше потолка усечением не объявляется")
    void b3_7_aPageBelowTheCeilingIsNotDeclaredTruncated() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(orders(99)));

        Answer answer = get(account(PENDING_ALL));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(99);
    }

    @Test
    @DisplayName("B3.8 — живые заявки инструмента и история читаются разными путями")
    void b3_8_pendingAndHistoryAreReadByDifferentPaths() {
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH, Okx.ok(
                Okx.order(INSTRUMENT, "ord-live", "vtb-live", "live").text()));
        exchange.answers(OkxConstants.TRADE_ORDERS_HISTORY_PATH, Okx.ok(
                Okx.order(INSTRUMENT, "ord-done", "vtb-done", "filled").text(),
                Okx.order(INSTRUMENT, "ord-gone", "vtb-gone", "canceled").text()));

        Answer pending = get(account(PENDING_INSTRUMENT));
        Answer history = get(account(HISTORY));

        assertThat(pending.status()).isEqualTo(200);
        assertThat(history.status()).isEqualTo(200);
        assertThat(pending.single().get("externalId")).isEqualTo("ord-live");
        assertThat(history.asList()).hasSize(2);
        LoggedRequest pendingRequest = exchange.single(OkxConstants.TRADE_ORDERS_PENDING_PATH);
        LoggedRequest historyRequest = exchange.single(OkxConstants.TRADE_ORDERS_HISTORY_PATH);
        assertThat(pendingRequest.getUrl()).contains("instId=" + INSTRUMENT).doesNotContain("limit=");
        assertThat(historyRequest.getUrl()).contains("instId=" + INSTRUMENT).doesNotContain("limit=");
    }

    /** Названное число заявок одного инструмента: ими подаётся страница среза. */
    private static String[] orders(Integer size) {
        return IntStream.range(0, size)
                .mapToObj(index -> Okx.order(INSTRUMENT, "ord-" + index, "vtb-" + index, "live").text())
                .toArray(String[]::new);
    }
}
