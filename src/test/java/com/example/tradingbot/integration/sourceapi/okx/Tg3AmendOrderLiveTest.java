package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG3. Amend order — {@code POST /api/v5/trade/amend-order} (Trade,
 * {@code signed:true}). WRITE: цепочка place(limit) → amend(px/sz) → getOrder →
 * cancel с teardown + Verify.end (поллинг).
 */
@Order(24)
class Tg3AmendOrderLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/amend-order";

    @Test
    @Order(10)
    @DisplayName("TG3.1 прямой — цепочка place(limit) → amend(px/sz) → getOrder → cancel")
    void tg3_1_amendLifecycle() {
        // Snapshot.start — чистый старт, нет живых ордеров по инструменту.
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("TG3.snapshot: clean start").isTrue();

        BigDecimal last = lastPrice();
        String px = tickPrice(last, 0.5);
        String newPx = tickPrice(last, 0.4);
        String clId = newId("tg");
        String ordId = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clId, "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            assertThat(ordId).as("TG3.place ordId").isNotBlank();
            final String captured = ordId;

            // amend — ACK изменения px/sz.
            RawResponse amend = post(PATH, map(
                    "instId", INST_ID, "ordId", captured,
                    "newSz", "0.02", "newPx", newPx, "cxlOnFail", false), SIGNED);
            assertOk(amend);
            assertFirstElementOk(amend);
            assertThat(amend.d0().path("ordId").asText()).as("TG3.amend ordId").isEqualTo(captured);

            // getOrder — amend отражён (поллинг до newSz/newPx).
            waitUntil("TG3.get amend reflected", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                return g.codeZero()
                        && captured.equals(g.d0().path("ordId").asText())
                        && "0.02".equals(g.d0().path("sz").asText())
                        && newPx.equals(g.d0().path("px").asText())
                        && "live".equals(g.d0().path("state").asText());
            });
        } finally {
            cancelOrderQuietly(ordId);
        }

        // Verify.end — живых ордеров нет (== Snapshot.start); sweep+halt при невозврате.
        final String captured = ordId;
        assertRestoredOrHalt("TG3.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }

    @Test
    @Order(20)
    @DisplayName("TG3.2 негатив — amend несуществующего ордера")
    void tg3_2_nonexistentOrder() {
        RawResponse r = post(PATH, map(
                "instId", INST_ID, "ordId", "9999999999999999",
                "newPx", tickPrice(0.4)), SIGNED);

        assertRejectedAnyLevel("TG3.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("TG3.3 негатив — amend без изменений (ни newSz, ни newPx)")
    void tg3_3_noChanges() {
        RawResponse r = post(PATH, map(
                "instId", INST_ID, "ordId", "9999999999999999"), SIGNED);

        assertRejectedAnyLevel("TG3.3", r);
    }
}
