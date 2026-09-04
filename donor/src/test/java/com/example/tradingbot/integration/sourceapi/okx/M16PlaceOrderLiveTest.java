package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M16. placeOrder — {@code POST /api/v5/trade/order} (Place order),
 * {@code signed:true}. Тело строится руками по контракту. Варианты ordType:
 * limit (неисполнимая цена → цепочка Climit) и market (исполняемый → fill →
 * позиция → цепочка Cmarket). Покрывает попутно M7/M8/M9/M10/M11/M12/M17/M18
 * (богатые состояния). WRITE: каждый кейс с teardown + Verify.end (поллинг).
 */
@Order(16)
class M16PlaceOrderLiveTest extends OkxSourceApiLiveTestBase {

    @Test
    @Order(10)
    @DisplayName("M16.limit — Climit lifecycle (неисполнимый limit)")
    void m16Limit_climitLifecycle() {
        // Snapshot.start — чистый старт, нет живых ордеров по инструменту.
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("Climit.snapshot: clean start").isTrue();

        String clOrdId = newId("cl");
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
            assertThat(ordId).as("Climit.place ordId").isNotBlank();
            final String captured = ordId;

            // get(live) — ACK ≠ runtime truth; покрывает M7.3.
            RawResponse live = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertOk(live);
            assertThat(live.d0().path("ordId").asText()).isEqualTo(captured);
            assertThat(live.d0().path("state").asText()).isEqualTo("live");

            // getByClId — резолв по clOrdId; покрывает M7.4.
            RawResponse byClId = get(ORDER_PATH, map("instId", INST_ID, "clOrdId", clOrdId), SIGNED);
            assertOk(byClId);
            assertThat(byClId.d0().path("ordId").asText()).isEqualTo(captured);

            // pending — живой ордер в pending; покрывает M8.3.
            RawResponse pending = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
            assertOk(pending);
            assertThat(containsField(pending, "ordId", captured)).as("Climit.pending live order").isTrue();

            // cancel — ACK отмены; покрывает M17 прямой.
            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);

            // canceled — финал cancel через поллинг до state=canceled.
            waitUntil("Climit.canceled state=canceled", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });

            // history — отменённый в истории 7д; индексинг-задержка = наблюдение.
            RawResponse history = get(ORDERS_HISTORY_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
            assertOk(history);
            if (!containsField(history, "ordId", captured)) {
                log.info("[Climit.history] ordId {} not yet in 7d history (indexing lag) — observation", captured);
            }
        } finally {
            cancelOrderQuietly(ordId);
        }

        // Verify.end — живых ордеров нет (== Snapshot.start); sweep+halt при невозврате.
        final String captured = ordId;
        assertRestoredOrHalt("Climit.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }

    @Test
    @Order(20)
    @DisplayName("M16.market — Cmarket execution lifecycle (исполняемый market)")
    void m16Market_cmarketLifecycle() {
        // Snapshot.start — нет открытой позиции по инструменту.
        assertThat(hasOpenPosition()).as("Cmarket.snapshot: clean start (no position)").isFalse();

        String clOrdId = newId("mk");
        String ordId = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "market", "sz", MIN_SZ,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            assertThat(ordId).as("Cmarket.place ordId").isNotBlank();
            final String captured = ordId;

            // get(filled) — поллинг до state=filled; покрывает M7 богатый.
            waitUntil("Cmarket.get state=filled", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                String state = g.d0().path("state").asText();
                return g.codeZero() && ("filled".equals(state) || "partially_filled".equals(state));
            });

            // position — открытая позиция; покрывает M12.3.
            RawResponse position = get(POSITIONS_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
            assertOk(position);
            assertThat(position.d0().path("posId").isMissingNode()).as("Cmarket.position posId present").isFalse();
            assertThat(position.d0().path("avgPx").isMissingNode()).isFalse();

            // fills — fill виден; покрывает M10.4.
            RawResponse fills = get(FILLS_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
            assertOk(fills);
            if (!containsField(fills, "ordId", captured)) {
                log.info("[Cmarket.fills] fill for ordId {} not visible yet — observation", captured);
            }

            // fills-history — fill в окне 3м; покрывает M11.4.
            RawResponse fillsHistory = get(FILLS_HISTORY_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
            assertOk(fillsHistory);
            if (!containsField(fillsHistory, "ordId", captured)) {
                log.info("[Cmarket.fillsHistory] fill for ordId {} not in 3m window yet — observation", captured);
            }

            // orders-history — filled-ордер; покрывает M9.3 богатый.
            RawResponse history = get(ORDERS_HISTORY_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
            assertOk(history);
            if (!containsField(history, "ordId", captured)) {
                log.info("[Cmarket.history] filled ordId {} not in 7d history yet — observation", captured);
            }

            // close — ACK закрытия (market, autoCxl); покрывает M18 прямой.
            RawResponse close = post(CLOSE_POSITION_PATH, map(
                    "instId", INST_ID, "mgnMode", "isolated", "posSide", "net",
                    "autoCxl", true, "ccy", "USDT"), SIGNED);
            assertOk(close);

            // positionFlat — позиция закрыта (поллинг до flat); покрывает M12.3 flat.
            waitUntil("Cmarket.positionFlat", () -> !hasOpenPosition());
        } finally {
            closePositionQuietly();
        }

        // Verify.end — позиции нет (== Snapshot.start); sweep+halt при невозврате.
        // Комиссионный/PnL-остаток баланса вне охвата (наблюдается, не фейлит).
        assertRestoredOrHalt("Cmarket.verify", "open position",
                () -> !hasOpenPosition(), this::closePositionQuietly);
    }

    @Test
    @Order(30)
    @DisplayName("M16.neg.size — отрицательный размер (вне домена)")
    void m16Neg_size() {
        RawResponse r = post(ORDER_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                "ordType", "limit", "sz", "-1", "px", tickPrice(0.5),
                "clOrdId", newId("ng"), "reduceOnly", false), SIGNED);

        assertRejectedAnyLevel("M16.neg.size", r);
    }

    @Test
    @Order(40)
    @DisplayName("M16.neg.side — некорректный side (вне домена)")
    void m16Neg_side() {
        RawResponse r = post(ORDER_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "side", "BOGUS",
                "ordType", "limit", "sz", MIN_SZ, "px", tickPrice(0.5),
                "clOrdId", newId("ng"), "reduceOnly", false), SIGNED);

        assertRejectedAnyLevel("M16.neg.side", r);
    }

    @Test
    @Order(50)
    @DisplayName("M16.neg.ordType — битый сырой ordType (реальный под /raw)")
    void m16Neg_ordType() {
        RawResponse r = post(ORDER_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                "ordType", "BOGUS", "sz", MIN_SZ, "px", tickPrice(0.5),
                "clOrdId", newId("ng"), "reduceOnly", false), SIGNED);

        assertRejectedAnyLevel("M16.neg.ordType", r);
    }

    @Test
    @Order(60)
    @DisplayName("M16.neg.reqParam — пропуск обязательного sz (OKX-слой)")
    void m16Neg_missingSize() {
        RawResponse r = post(ORDER_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                "ordType", "limit", "px", tickPrice(0.5),
                "clOrdId", newId("ng"), "reduceOnly", false), SIGNED);

        assertRejectedAnyLevel("M16.neg.reqParam", r);
    }

    @Test
    @Order(70)
    @DisplayName("M16.neg.dupClId — дубль clOrdId (stateful, с teardown)")
    void m16NegDupClId_chain() {
        // Snapshot.start.
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("M16.dup.snapshot: clean start").isTrue();

        String clOrdId = newId("dup");
        String px = tickPrice(0.5);
        String ordId1 = null;
        String ordId2 = null;
        try {
            RawResponse place1 = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            assertOk(place1);
            assertFirstElementOk(place1);
            ordId1 = place1.d0().path("ordId").asText();
            assertThat(ordId1).as("M16.dup.place1 ordId").isNotBlank();

            RawResponse place2 = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            observe("M16.dup.place2", place2);
            assertThat(place2.firstElementReject() || place2.businessReject())
                    .as("duplicate clOrdId must be rejected").isTrue();
            if (place2.firstElementOk()) {
                ordId2 = place2.d0().path("ordId").asText(); // неожиданный успех — захватить для очистки
            }
        } finally {
            cancelOrderQuietly(ordId1);
            cancelOrderQuietly(ordId2);
        }

        // Verify.end — sweep+halt при невозврате.
        final String captured = ordId1;
        assertRestoredOrHalt("M16.dup.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }
}
