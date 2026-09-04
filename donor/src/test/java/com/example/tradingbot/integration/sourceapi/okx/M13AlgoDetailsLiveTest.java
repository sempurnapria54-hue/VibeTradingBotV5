package com.example.tradingbot.integration.sourceapi.okx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M13. algo details — {@code GET /api/v5/trade/order-algo} (Algo details),
 * {@code signed:true}. Здесь — только no-state негативы; прямые по
 * algoId/algoClOrdId (M13.3/M13.4) покрыты цепочками M19.
 */
@Order(13)
class M13AlgoDetailsLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/order-algo";

    @Test
    @Order(10)
    @DisplayName("M13.1 негатив (no-state) — фейковый algoId")
    void m13_1_fakeAlgoId() {
        RawResponse r = get(PATH, map("instId", INST_ID, "algoId", "9999999999999999"), SIGNED);

        assertRejectOrEmpty("M13.1", r);
    }

    @Test
    @Order(20)
    @DisplayName("M13.2 негатив (no-state) — ни algoId, ни algoClOrdId (OKX-слой)")
    void m13_2_missingAlgoIdentifier() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("M13.2", r);
    }

    // M13.3/M13.4 (прямой по algoId/algoClOrdId) — покрыты цепочками M19.
}
