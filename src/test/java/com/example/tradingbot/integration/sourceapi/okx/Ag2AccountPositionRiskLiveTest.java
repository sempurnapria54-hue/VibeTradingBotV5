package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG2. Account & position risk —
 * {@code GET /api/v5/account/account-position-risk} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: единый
 * временной срез {@code ts} + {@code balData[]} + {@code posData[]}.
 */
@Order(32)
class Ag2AccountPositionRiskLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/account-position-risk";

    @Test
    @Order(10)
    @DisplayName("AG2.1 Прямой — единый временной срез")
    void ag2_1_directSnapshot() {
        RawResponse r = get(PATH, map("instType", INST_TYPE), SIGNED);

        assertOk(r);
        assertThat(r.d0().isMissingNode()).isFalse();
        assertThat(r.d0().path("ts").asText("")).isNotBlank();
        assertThat(r.d0().path("balData").isArray()).isTrue();
        assertThat(r.d0().path("posData").isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("AG2.2 Негатив — битое значение instType (вне домена)")
    void ag2_2_instTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("AG2.2", r);
    }
}
