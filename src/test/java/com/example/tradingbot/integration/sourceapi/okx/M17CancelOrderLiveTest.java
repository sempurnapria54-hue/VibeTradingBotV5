package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M17. cancelOrder — {@code POST /api/v5/trade/cancel-order} (Cancel order),
 * {@code signed:true}. Прямой by {@code ordId} покрыт цепочкой Climit
 * (M16, не дублируется). Здесь: no-state негативы (M17.1/M17.2),
 * состояние-конфликт «отмена отменённого» (M17.3) и вариант by {@code clOrdId}
 * самостоятельной мини-цепочкой (M17.4) — обе с teardown + Verify.end.
 *
 * <p>Отклонение от плана: M17.3 в плане ссылается на состояние Climit
 * (M16). Код-тесты самодостаточны (каждая цепочка несёт свой
 * snapshot/teardown), поэтому M17.3 реализован как самостоятельная
 * последовательность place → cancel → re-cancel.
 */
@Order(17)
class M17CancelOrderLiveTest extends OkxSourceApiLiveTestBase {

    @Test
    @Order(10)
    @DisplayName("M17.1 негатив (no-state) — cancel несуществующего ordId")
    void m17_1_cancelNonexistent() {
        RawResponse r = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", "9999999999999999"), SIGNED);

        assertRejectedAnyLevel("M17.1", r);
    }

    @Test
    @Order(20)
    @DisplayName("M17.2 негатив — пропуск обязательного instId (OKX-слой)")
    void m17_2_missingInstId() {
        RawResponse r = post(CANCEL_ORDER_PATH, map("ordId", "9999999999999999"), SIGNED);

        assertRejectedAnyLevel("M17.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("M17.3 негатив — отмена отменённого (состояние-конфликт)")
    void m17_3_cancelOfCanceled() {
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("M17.3.snapshot: clean start").isTrue();

        String clOrdId = newId("m17c");
        String px = tickPrice(0.5);
        String ordId = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            final String captured = ordId;

            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);
            waitUntil("M17.3 order canceled", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });

            // Повторная отмена уже отменённого — реджект (already canceled / not exist).
            RawResponse recancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertRejectedAnyLevel("M17.3", recancel);
        } finally {
            cancelOrderQuietly(ordId);
        }

        final String captured = ordId;
        assertRestoredOrHalt("M17.3.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }

    @Test
    @Order(40)
    @DisplayName("M17.4 прямой + вариант clOrdId — cancel by clOrdId (мини-цепочка)")
    void m17_4_cancelByClOrdId() {
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("M17.4.snapshot: clean start").isTrue();

        String clOrdId = newId("m17");
        String px = tickPrice(0.5);
        String ordId = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            assertThat(ordId).as("M17.4.place ordId").isNotBlank();

            // cancel by clOrdId (без ordId).
            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "clOrdId", clOrdId), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);

            // canceled — финал через поллинг по clOrdId.
            waitUntil("M17.4 canceled by clOrdId", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "clOrdId", clOrdId), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });
        } finally {
            cancelOrderQuietly(ordId);
        }

        final String captured = ordId;
        assertRestoredOrHalt("M17.4.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }
}
