package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG9. Leverage info — {@code GET /api/v5/account/leverage-info} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: leverage
 * инструмента при заданном {@code mgnMode} (обязателен).
 */
@Order(39)
class Ag9LeverageInfoLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/leverage-info";

    @Test
    @Order(10)
    @DisplayName("AG9.1 Прямой — leverage инструмента (isolated)")
    void ag9_1_directIsolated() {
        RawResponse r = get(PATH, map("instId", INST_ID, "mgnMode", "isolated"), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("mgnMode").asText()).isEqualTo("isolated");
        assertThat(r.d0().path("lever").asText("")).isNotBlank();
        assertThat(r.d0().path("posSide").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("AG9.2 Негатив — пропуск обязательного mgnMode")
    void ag9_2_missingMgnMode() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("AG9.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("AG9.3 Негатив — битое значение mgnMode (вне домена)")
    void ag9_3_mgnModeOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INST_ID, "mgnMode", "bogus"), SIGNED);

        assertBusinessReject(r);
        observe("AG9.3", r);
    }
}
