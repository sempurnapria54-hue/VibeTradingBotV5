package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M14. algo-pending — {@code GET /api/v5/trade/orders-algo-pending}
 * (Algo pending), {@code signed:true}. Вариант — {@code ordType}
 * (conditional/oco/move_order_stop). Прямой богатый (M14.6) покрыт M19.
 */
@Order(14)
class M14AlgoPendingLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/orders-algo-pending";

    @Test
    @Order(10)
    @DisplayName("M14.1 прямой (no-state) — ordType=conditional, пустой/валидный")
    void m14_1_conditional() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "conditional"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M14.2 вариант — ordType=oco")
    void m14_2_oco() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "oco"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("M14.3 вариант — ordType=move_order_stop (advance)")
    void m14_3_moveOrderStop() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "move_order_stop"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(40)
    @DisplayName("M14.4 негатив — ordType вне домена (OKX-слой)")
    void m14_4_ordTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("M14.4", r);
    }

    @Test
    @Order(50)
    @DisplayName("M14.5 негатив — пропуск обязательного ordType (OKX-слой)")
    void m14_5_missingOrdType() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("M14.5", r);
    }

    // M14.6 (прямой богатый) — покрыт M19.
}
