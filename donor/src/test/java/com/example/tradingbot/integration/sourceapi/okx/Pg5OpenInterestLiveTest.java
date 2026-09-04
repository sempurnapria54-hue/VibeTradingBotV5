package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG5. open interest — {@code GET /api/v5/public/open-interest} (Public Data),
 * {@code signed:false}. Открытый интерес контрактов.
 */
@Order(57)
class Pg5OpenInterestLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/open-interest";

    @Test
    @Order(10)
    @DisplayName("PG5.1 Прямой — open interest по instType=SWAP + instId")
    void pg5_1_direct() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("instType").asText()).isEqualTo(INST_TYPE);
        assertThat(r.d0().path("oi").isMissingNode()).isFalse();
        assertThat(r.d0().path("oiCcy").isMissingNode()).isFalse();
        assertThat(r.d0().path("oiUsd").isMissingNode()).isFalse();
        assertThat(r.d0().path("ts").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PG5.2 Негатив — несуществующий instId (реджект ИЛИ пустой data)")
    void pg5_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", "NOPE-USDT-SWAP"), PUBLIC);

        assertRejectOrEmpty("PG5.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("PG5.3 instType необязателен при instId (RUN: code=0, не реджект)")
    void pg5_3_missingInstType() {
        // RUN-факт (2026-06-19): open-interest принимает instId без instType
        // (b.code=0, instType выводится из instId) — реджекта нет, в отличие от
        // предположения плана. Находка C3.
        RawResponse r = get(PATH, map("instId", INST_ID), PUBLIC);

        assertOk(r);
        observe("PG5.3", r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
    }

    @Test
    @Order(40)
    @DisplayName("PG5.4 Негатив — битое значение instType (OKX-реджект)")
    void pg5_4_brokenInstType() {
        RawResponse r = get(PATH, map("instType", "WRONG", "instId", INST_ID), PUBLIC);

        assertBusinessReject(r);
        observe("PG5.4", r);
    }
}
