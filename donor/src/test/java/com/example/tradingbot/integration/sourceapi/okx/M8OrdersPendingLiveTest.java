package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M8. orders-pending — {@code GET /api/v5/trade/orders-pending}
 * (Pending orders), {@code signed:true}. Прямой богатый (M8.3) покрыт
 * цепочкой Climit (M16).
 */
@Order(8)
class M8OrdersPendingLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/orders-pending";

    @Test
    @Order(10)
    @DisplayName("M8.1 прямой (no-state) — пустой/валидный")
    void m8_1_emptyValid() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M8.2 негатив — несущ. instId")
    void m8_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), SIGNED);

        assertRejectOrEmpty("M8.2", r);
    }

    // M8.3 (прямой богатый) — покрыт цепочкой Climit (M16).
}
