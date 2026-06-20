package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M15. algo-history — {@code GET /api/v5/trade/orders-algo-history}
 * (Algo history 3m), {@code signed:true}. Вариант — {@code ordType}
 * (обязателен в OKX history). Прямой богатый (M15.6) покрыт M19.
 *
 * <p>RUN-факт (2026-06-19): orders-algo-history требует ещё и {@code state}
 * (или {@code algoId}) — иначе b.code=50015 "Either parameter state or algoId
 * is required". Прямые кейсы шлют {@code state=canceled} (терминальный, валиден
 * для history). Находка C3 в апидок.
 */
@Order(15)
class M15AlgoHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/orders-algo-history";

    @Test
    @Order(10)
    @DisplayName("M15.1 прямой (no-state) — ordType=conditional, пустой/валидный")
    void m15_1_conditional() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "conditional", "state", "canceled"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M15.2 вариант — ordType=oco")
    void m15_2_oco() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "oco", "state", "canceled"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("M15.3 вариант — ordType=move_order_stop")
    void m15_3_moveOrderStop() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "move_order_stop", "state", "canceled"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(40)
    @DisplayName("M15.4 негатив — ordType вне домена (OKX-слой)")
    void m15_4_ordTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INST_ID, "ordType", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("M15.4", r);
    }

    @Test
    @Order(50)
    @DisplayName("M15.5 негатив — пропуск обязательного ordType (OKX-слой)")
    void m15_5_missingOrdType() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("M15.5", r);
    }

    // M15.6 (прямой богатый) — покрыт M19.
}
