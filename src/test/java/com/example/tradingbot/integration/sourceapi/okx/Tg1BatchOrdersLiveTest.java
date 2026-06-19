package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG1. Place batch orders — {@code POST /api/v5/trade/batch-orders} (Trade,
 * {@code signed:true}). Тело — массив order-item'ов, строится руками по
 * контракту. WRITE: цепочка place-batch[2] → cancel-batch с teardown +
 * Verify.end (поллинг).
 */
@Order(22)
class Tg1BatchOrdersLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/batch-orders";

    @Test
    @Order(10)
    @DisplayName("TG1.1 прямой — цепочка place-batch[2 неисполнимых limit] → cancel-batch")
    void tg1_1_batchLifecycle() {
        // Snapshot.start — чистый старт, нет живых ордеров по инструменту.
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("TG1.snapshot: clean start").isTrue();

        String px = tickPrice(0.5);
        String clId1 = newId("tg");
        String clId2 = newId("tg");
        String ordId1 = null;
        String ordId2 = null;
        try {
            RawResponse place = post(PATH, List.of(
                    map("instId", INST_ID, "tdMode", "isolated", "side", "buy",
                            "ordType", "limit", "sz", MIN_SZ, "px", px, "clOrdId", clId1),
                    map("instId", INST_ID, "tdMode", "isolated", "side", "buy",
                            "ordType", "limit", "sz", MIN_SZ, "px", px, "clOrdId", clId2)), SIGNED);
            assertOk(place);
            assertThat(place.at(0).path("sCode").asText()).as("TG1.place item0 sCode").isEqualTo("0");
            assertThat(place.at(1).path("sCode").asText()).as("TG1.place item1 sCode").isEqualTo("0");
            ordId1 = place.at(0).path("ordId").asText();
            ordId2 = place.at(1).path("ordId").asText();
            assertThat(ordId1).as("TG1.place ordId1").isNotBlank();
            assertThat(ordId2).as("TG1.place ordId2").isNotBlank();
            final String captured1 = ordId1;
            final String captured2 = ordId2;

            // pending — оба живых ордера в pending (поллинг до появления).
            waitUntil("TG1.pending both live", () -> {
                RawResponse g = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
                return g.codeZero() && containsField(g, "ordId", captured1)
                        && containsField(g, "ordId", captured2);
            });

            // cancel — оба отменены пакетом (покрывает TG2 прямой).
            RawResponse cancel = post("/api/v5/trade/cancel-batch-orders", List.of(
                    map("instId", INST_ID, "ordId", captured1),
                    map("instId", INST_ID, "ordId", captured2)), SIGNED);
            assertOk(cancel);
            assertThat(cancel.at(0).path("sCode").asText()).as("TG1.cancel item0 sCode").isEqualTo("0");
            assertThat(cancel.at(1).path("sCode").asText()).as("TG1.cancel item1 sCode").isEqualTo("0");

            // flat — pending без наших ордеров (поллинг до отсутствия).
            waitUntil("TG1.flat both gone", () -> pendingHasNo(captured1) && pendingHasNo(captured2));
        } finally {
            cancelOrderQuietly(ordId1);
            cancelOrderQuietly(ordId2);
        }

        // Verify.end — живых ордеров нет (== Snapshot.start); sweep+halt при невозврате.
        final String captured1 = ordId1;
        final String captured2 = ordId2;
        assertRestoredOrHalt("TG1.verify", "live orders (ordId=" + captured1 + "," + captured2 + ")",
                () -> pendingHasNo(captured1) && pendingHasNo(captured2),
                () -> {
                    cancelOrderQuietly(captured1);
                    cancelOrderQuietly(captured2);
                });
    }

    @Test
    @Order(20)
    @DisplayName("TG1.2 негатив — частичный реджект (битый item в пакете)")
    void tg1_2_partialReject() {
        String px = tickPrice(0.5);
        String strayOrdId = null;
        try {
            RawResponse r = post(PATH, List.of(
                    map("instId", INST_ID, "tdMode", "isolated", "side", "buy",
                            "ordType", "limit", "sz", MIN_SZ, "px", px, "clOrdId", newId("tg")),
                    map("instId", INST_ID, "tdMode", "isolated", "side", "buy",
                            "ordType", "limit", "sz", "-1", "px", px, "clOrdId", newId("tg"))), SIGNED);
            assertHttp200(r);
            observe("TG1.2", r);
            // Второй item (sz вне домена) реджектится поэлементно.
            assertThat(r.at(1).path("sCode").asText()).as("TG1.2 item1 reject").isNotEqualTo("0");
            // Первый item — прошёл (наблюдение) → захватить для teardown.
            if ("0".equals(r.at(0).path("sCode").asText())) {
                strayOrdId = r.at(0).path("ordId").asText();
            }
        } finally {
            cancelOrderQuietly(strayOrdId);
        }
    }

    @Test
    @Order(30)
    @DisplayName("TG1.3 негатив — пропуск обязательного instId в item (OKX-слой)")
    void tg1_3_missingInstId() {
        RawResponse r = post(PATH, List.of(
                map("tdMode", "isolated", "side", "buy", "ordType", "limit",
                        "sz", MIN_SZ, "px", tickPrice(0.5), "clOrdId", newId("tg"))), SIGNED);

        assertRejectedAnyLevel("TG1.3", r);
    }
}
