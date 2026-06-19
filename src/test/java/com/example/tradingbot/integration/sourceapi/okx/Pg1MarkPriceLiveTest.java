package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG1. mark price — {@code GET /api/v5/public/mark-price} (Public Data),
 * {@code signed:false}. Текущее значение mark price инструмента.
 */
@Order(53)
class Pg1MarkPriceLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/mark-price";

    @Test
    @Order(10)
    @DisplayName("PG1.1 Прямой — mark price по instType=SWAP + instId")
    void pg1_1_direct() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("instType").asText()).isEqualTo(INST_TYPE);
        assertThat(r.d0().path("markPx").isMissingNode()).isFalse();
        assertThat(new BigDecimal(r.d0().path("markPx").asText())).isNotNull();
        assertThat(r.d0().path("ts").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PG1.2 Негатив — несуществующий instId (реджект ИЛИ пустой data)")
    void pg1_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", "NOPE-USDT-SWAP"), PUBLIC);

        assertRejectOrEmpty("PG1.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("PG1.3 Негатив — пропуск обязательного instType (OKX-реджект)")
    void pg1_3_missingInstType() {
        RawResponse r = get(PATH, map("instId", INST_ID), PUBLIC);

        assertBusinessReject(r);
        observe("PG1.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("PG1.4 Негатив — битое значение instType (OKX-реджект)")
    void pg1_4_brokenInstType() {
        RawResponse r = get(PATH, map("instType", "WRONG", "instId", INST_ID), PUBLIC);

        assertBusinessReject(r);
        observe("PG1.4", r);
    }
}
