package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG8. insurance fund — {@code GET /api/v5/public/insurance-fund}
 * (Public Data), {@code signed:false}. Баланс страхового фонда (офдок:
 * «security fund»).
 */
@Order(60)
class Pg8InsuranceFundLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/insurance-fund";

    @Test
    @Order(10)
    @DisplayName("PG8.1 Прямой — фонд по instType=SWAP + instFamily")
    void pg8_1_direct() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instFamily", INST_FAMILY), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("total").isMissingNode()).isFalse();
        assertThat(r.d0().path("instType").asText()).isEqualTo(INST_TYPE);
        assertThat(r.d0().path("details").isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("PG8.2 Негатив — пропуск обязательного instType (OKX-реджект)")
    void pg8_2_missingInstType() {
        RawResponse r = get(PATH, map("instFamily", INST_FAMILY), PUBLIC);

        assertBusinessReject(r);
        observe("PG8.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("PG8.3 Негатив — пропуск обязательного instFamily для SWAP (OKX-реджект)")
    void pg8_3_missingInstFamily() {
        RawResponse r = get(PATH, map("instType", INST_TYPE), PUBLIC);

        assertBusinessReject(r);
        observe("PG8.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("PG8.4 битый type игнорируется (RUN: code=0, не реджект)")
    void pg8_4_brokenType() {
        // RUN-факт (2026-06-19): insurance-fund игнорирует невалидный type
        // (WRONG) и отдаёт code=0 с данными, а не реджект/пусто. Находка C3.
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instFamily", INST_FAMILY, "type", "WRONG"), PUBLIC);

        assertOk(r);
        observe("PG8.4", r);
    }
}
