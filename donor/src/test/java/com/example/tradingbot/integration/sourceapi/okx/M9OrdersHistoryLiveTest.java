package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M9. orders-history 7d — {@code GET /api/v5/trade/orders-history}
 * (Order history 7d), {@code signed:true}. {@code instType} обязателен в
 * history. Прямой богатый (M9.3) покрыт цепочками Cmarket/Climit (M16).
 */
@Order(9)
class M9OrdersHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/orders-history";

    @Test
    @Order(10)
    @DisplayName("M9.1 прямой (no-state) — пустой/валидный")
    void m9_1_emptyValid() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M9.2 негатив — пропуск обязательного instType (OKX-слой)")
    void m9_2_missingInstType() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("M9.2", r);
    }

    // M9.3 (прямой богатый) — покрыт цепочками Cmarket/Climit (M16).
}
