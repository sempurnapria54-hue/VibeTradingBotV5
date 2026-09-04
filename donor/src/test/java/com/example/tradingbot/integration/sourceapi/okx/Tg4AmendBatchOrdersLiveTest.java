package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG4. Amend batch orders — {@code POST /api/v5/trade/amend-batch-orders}
 * (Trade, {@code signed:true}). Тело — массив amend-item'ов. WRITE: цепочка
 * place-batch[2] → amend-batch[2] → getOrder ×2 → cancel-batch с teardown +
 * Verify.end (поллинг).
 */
@Order(25)
class Tg4AmendBatchOrdersLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/amend-batch-orders";
    private static final String PLACE_PATH = "/api/v5/trade/batch-orders";
    private static final String CANCEL_PATH = "/api/v5/trade/cancel-batch-orders";

    @Test
    @Order(10)
    @DisplayName("TG4.1 прямой — place-batch[2] → amend-batch[2] → getOrder ×2 → cancel-batch")
    void tg4_1_amendBatchLifecycle() {
        // Snapshot.start — чистый старт, нет живых ордеров по инструменту.
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("TG4.snapshot: clean start").isTrue();

        BigDecimal last = lastPrice();
        String px = tickPrice(last, 0.5);
        String newPx = tickPrice(last, 0.4);
        String ordId1 = null;
        String ordId2 = null;
        try {
            RawResponse place = post(PLACE_PATH, List.of(
                    map("instId", INST_ID, "tdMode", "isolated", "side", "buy",
                            "ordType", "limit", "sz", MIN_SZ, "px", px, "clOrdId", newId("tg")),
                    map("instId", INST_ID, "tdMode", "isolated", "side", "buy",
                            "ordType", "limit", "sz", MIN_SZ, "px", px, "clOrdId", newId("tg"))), SIGNED);
            assertOk(place);
            assertThat(place.at(0).path("sCode").asText()).as("TG4.place item0 sCode").isEqualTo("0");
            assertThat(place.at(1).path("sCode").asText()).as("TG4.place item1 sCode").isEqualTo("0");
            ordId1 = place.at(0).path("ordId").asText();
            ordId2 = place.at(1).path("ordId").asText();
            assertThat(ordId1).as("TG4.place ordId1").isNotBlank();
            assertThat(ordId2).as("TG4.place ordId2").isNotBlank();
            final String captured1 = ordId1;
            final String captured2 = ordId2;

            // amend-batch — оба приняты поэлементно.
            RawResponse amend = post(PATH, List.of(
                    map("instId", INST_ID, "ordId", captured1, "newSz", "0.02", "newPx", newPx),
                    map("instId", INST_ID, "ordId", captured2, "newPx", newPx)), SIGNED);
            assertOk(amend);
            assertThat(amend.at(0).path("sCode").asText()).as("TG4.amend item0 sCode").isEqualTo("0");
            assertThat(amend.at(1).path("sCode").asText()).as("TG4.amend item1 sCode").isEqualTo("0");

            // get1/get2 — amend применён (поллинг до отражения newSz/newPx).
            // Amend на OKX асинхронен (ACK ≠ runtime truth): отражение в getOrder
            // на demo иногда > дефолтных 25с — как у одиночного amend (TG3.1),
            // даём «медленному» кейсу 60с (план: per-case poll-таймаут длиннее).
            waitUntil("TG4.get1 amend reflected", Duration.ofSeconds(60), () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured1), SIGNED);
                return g.codeZero()
                        && "0.02".equals(g.d0().path("sz").asText())
                        && newPx.equals(g.d0().path("px").asText());
            });

            // get2 — amend item2 применён (поллинг до отражения newPx).
            waitUntil("TG4.get2 amend reflected", Duration.ofSeconds(60), () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured2), SIGNED);
                return g.codeZero() && newPx.equals(g.d0().path("px").asText());
            });
        } finally {
            if (ordId1 != null || ordId2 != null) {
                post(CANCEL_PATH, List.of(
                        map("instId", INST_ID, "ordId", ordId1 == null ? "" : ordId1),
                        map("instId", INST_ID, "ordId", ordId2 == null ? "" : ordId2)), SIGNED);
            }
        }

        // Verify.end — живых ордеров нет (== Snapshot.start); sweep+halt при невозврате.
        final String captured1 = ordId1;
        final String captured2 = ordId2;
        assertRestoredOrHalt("TG4.verify", "live orders (ordId=" + captured1 + "," + captured2 + ")",
                () -> pendingHasNo(captured1) && pendingHasNo(captured2),
                () -> {
                    cancelOrderQuietly(captured1);
                    cancelOrderQuietly(captured2);
                });
    }

    @Test
    @Order(20)
    @DisplayName("TG4.2 негатив — частичный реджект (битый item: несущ. ordId)")
    void tg4_2_partialReject() {
        // Snapshot.start — чистый старт, нет живых ордеров по инструменту.
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("TG4.2.snapshot: clean start").isTrue();

        BigDecimal last = lastPrice();
        String px = tickPrice(last, 0.5);
        String newPx = tickPrice(last, 0.4);
        String ordId1 = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", newId("tg"), "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId1 = place.d0().path("ordId").asText();
            assertThat(ordId1).as("TG4.2.place ordId").isNotBlank();
            final String captured1 = ordId1;

            // amend-batch — item1 валидный, item2 — несущ. ordId.
            RawResponse r = post(PATH, List.of(
                    map("instId", INST_ID, "ordId", captured1, "newPx", newPx),
                    map("instId", INST_ID, "ordId", "9999999999999999", "newPx", newPx)), SIGNED);
            assertHttp200(r);
            observe("TG4.2", r);
            assertThat(r.at(1).path("sCode").asText()).as("TG4.2 item1 reject").isNotEqualTo("0");
        } finally {
            cancelOrderQuietly(ordId1);
        }

        // Verify.end — живых ордеров нет (== Snapshot.start); sweep+halt при невозврате.
        final String captured1 = ordId1;
        assertRestoredOrHalt("TG4.2.verify", "live orders (ordId=" + captured1 + ")",
                () -> pendingHasNo(captured1), () -> cancelOrderQuietly(captured1));
    }

    @Test
    @Order(30)
    @DisplayName("TG4.3 негатив — пропуск изменений и идентификатора в item (OKX-слой)")
    void tg4_3_missingIdentifierAndChanges() {
        RawResponse r = post(PATH, List.of(map("instId", INST_ID)), SIGNED);

        assertRejectedAnyLevel("TG4.3", r);
    }
}
