package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG6. position tiers — {@code GET /api/v5/public/position-tiers}
 * (Public Data), {@code signed:false}. Путь PUBLIC, не {@code account/}
 * (находка прогона 3). Лимиты размера позиции, ставки маржи, max плечо по
 * тирам.
 */
@Order(58)
class Pg6PositionTiersLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/position-tiers";

    @Test
    @Order(10)
    @DisplayName("PG6.1 Прямой — тиры по instType=SWAP + tdMode + instFamily")
    void pg6_1_direct() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "tdMode", "isolated", "instFamily", INST_FAMILY), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().path("tier").isMissingNode()).isFalse();
        assertThat(r.d0().path("minSz").isMissingNode()).isFalse();
        assertThat(r.d0().path("maxSz").isMissingNode()).isFalse();
        assertThat(r.d0().path("imr").isMissingNode()).isFalse();
        assertThat(r.d0().path("mmr").isMissingNode()).isFalse();
        assertThat(r.d0().path("maxLever").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PG6.2 Негатив — пропуск обязательного tdMode (OKX-реджект)")
    void pg6_2_missingTdMode() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instFamily", INST_FAMILY), PUBLIC);

        assertBusinessReject(r);
        observe("PG6.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("PG6.3 Негатив — пропуск обязательного instFamily для SWAP (OKX-реджект)")
    void pg6_3_missingInstFamily() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "tdMode", "isolated"), PUBLIC);

        assertBusinessReject(r);
        observe("PG6.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("PG6.4 Негатив — битое значение tdMode (OKX-реджект)")
    void pg6_4_brokenTdMode() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "tdMode", "WRONG", "instFamily", INST_FAMILY), PUBLIC);

        assertBusinessReject(r);
        observe("PG6.4", r);
    }
}
