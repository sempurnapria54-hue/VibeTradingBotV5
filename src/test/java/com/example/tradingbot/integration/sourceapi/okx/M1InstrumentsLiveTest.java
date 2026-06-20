package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M1. instruments — {@code GET /api/v5/public/instruments} (Public Data),
 * {@code signed:false}. Здесь же единственный на весь CORE кейс «сломанный
 * конверт» (M1.6).
 */
@Order(1)
class M1InstrumentsLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/instruments";

    @Test
    @Order(10)
    @DisplayName("M1.1 прямой — instruments(SWAP, ETH-USDT-SWAP)")
    void m1_1_direct() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("minSz").isMissingNode()).isFalse();
        assertThat(r.d0().path("tickSz").isMissingNode()).isFalse();
        assertThat(r.d0().path("lotSz").isMissingNode()).isFalse();
        assertThat(r.d0().path("ctVal").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("M1.2 вариант — instruments(SWAP) без instId (список)")
    void m1_2_listWithoutInstId() {
        RawResponse r = get(PATH, map("instType", INST_TYPE), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(containsField(r, "instId", INST_ID)).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("M1.3 негатив — instType вне домена (OKX-слой)")
    void m1_3_instTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", "BOGUS", "instId", INST_ID), PUBLIC);

        assertBusinessReject(r);
        observe("M1.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("M1.4 негатив — несущ. instId (реджект 51001 или пустой data)")
    void m1_4_nonexistentInstId() {
        // RUN-факт (2026-06-19): на несущ. instId instruments отдаёт реджект
        // b.code=51001 ("Instrument ID ... doesn't exist"), а не пустой data с
        // code=0 (как предполагал план). Поведение совпадает с ticker (M2.2) и
        // candles (M3.3). Принимаем реджект-или-пусто. Находка C3 в апидок.
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("M1.4", r);
    }

    @Test
    @Order(50)
    @DisplayName("M1.5 негатив — пропуск обязательного instType (OKX-слой)")
    void m1_5_missingInstType() {
        RawResponse r = get(PATH, map("instId", INST_ID), PUBLIC);

        assertBusinessReject(r);
        observe("M1.5", r);
    }

    @Test
    @Order(60)
    @DisplayName("M1.6 негатив — сломанный конверт (нераспознаваемый method, прокси-слой)")
    void m1_6_brokenEnvelope() {
        RawResponse r = raw("BOGUS", PATH, map("instType", INST_TYPE), null, PUBLIC);

        log.info("[M1.6] OBSERVE broken-envelope http={} body={}", r.status(), r.rawBody());
        assertThat(r.status())
                .as("broken envelope must not dispatch (HTTP 4xx/5xx, OKX network not reached)")
                .isGreaterThanOrEqualTo(400);
    }
}
