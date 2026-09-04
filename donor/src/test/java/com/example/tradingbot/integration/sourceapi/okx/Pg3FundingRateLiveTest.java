package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG3. funding rate — {@code GET /api/v5/public/funding-rate} (Public Data),
 * {@code signed:false}. Прогнозная ставка ближайшего расчёта SWAP.
 */
@Order(55)
class Pg3FundingRateLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/funding-rate";

    @Test
    @Order(10)
    @DisplayName("PG3.1 Прямой — funding rate по instId SWAP")
    void pg3_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("fundingRate").isMissingNode()).isFalse();
        assertThat(r.d0().path("fundingTime").isMissingNode()).isFalse();
        assertThat(r.d0().path("nextFundingTime").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PG3.2 Негатив — несуществующий instId (реджект ИЛИ пустой data)")
    void pg3_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "NOPE-USDT-SWAP"), PUBLIC);

        assertRejectOrEmpty("PG3.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("PG3.3 Негатив — пропуск обязательного instId (OKX-реджект)")
    void pg3_3_missingInstId() {
        RawResponse r = get(PATH, null, PUBLIC);

        assertBusinessReject(r);
        observe("PG3.3", r);
    }
}
