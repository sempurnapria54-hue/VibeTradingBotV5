package com.example.tradingbot.integration.sourceapi.okx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M7. order details — {@code GET /api/v5/trade/order} (Order details),
 * {@code signed:true}. Здесь — только no-state негативы; прямые по
 * ordId/clOrdId (M7.3/M7.4) покрыты цепочкой Climit (M16).
 */
@Order(7)
class M7OrderDetailsLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/order";

    @Test
    @Order(10)
    @DisplayName("M7.1 негатив (no-state) — фейковый ordId")
    void m7_1_fakeOrdId() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordId", "9999999999999999"), SIGNED);

        assertRejectOrEmpty("M7.1", r);
    }

    @Test
    @Order(20)
    @DisplayName("M7.2 негатив (no-state) — пропуск обязательного instId (OKX-слой)")
    void m7_2_missingInstId() {
        RawResponse r = get(PATH, map("ordId", "9999999999999999"), SIGNED);

        assertBusinessReject(r);
        observe("M7.2", r);
    }

    // M7.3/M7.4 (прямой по ordId/clOrdId) — покрыты цепочкой Climit (M16).
}
