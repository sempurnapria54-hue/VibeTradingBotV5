package com.example.tradingbot.integration.sourceapi.okx;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M20. cancelAlgos — {@code POST /api/v5/trade/cancel-algos} (Cancel algo
 * ordinary), {@code signed:true}. Тело — массив {@code [{instId, algoId}]}.
 * Прямой (ordinary {@code sCode=0}) покрыт цепочками M19 cond/tp/oco
 * (.cancel, не дублируется). Здесь — no-state негатив.
 */
@Order(20)
class M20CancelAlgosLiveTest extends OkxSourceApiLiveTestBase {

    @Test
    @Order(10)
    @DisplayName("M20.1 негатив (no-state) — cancel несущ. algoId (ordinary)")
    void m20_1_cancelNonexistent() {
        RawResponse r = post(CANCEL_ALGOS_PATH,
                List.of(map("instId", INST_ID, "algoId", "9999999999999999")), SIGNED);

        assertRejectedAnyLevel("M20.1", r);
    }
}
