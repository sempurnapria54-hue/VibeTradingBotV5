package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG10. Max order size — {@code GET /api/v5/account/max-size} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: серверная
 * оценка {@code maxBuy}/{@code maxSell} (контракты для SWAP); {@code instId} +
 * {@code tdMode} обязательны.
 */
@Order(40)
class Ag10MaxSizeLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/max-size";

    @Test
    @Order(10)
    @DisplayName("AG10.1 Прямой — максимальный sz buy/sell")
    void ag10_1_directMaxSize() {
        RawResponse r = get(PATH, map("instId", INST_ID, "tdMode", "isolated"), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("maxBuy").asText("")).isNotBlank();
        assertThat(r.d0().path("maxSell").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("AG10.2 Негатив — пропуск обязательного tdMode")
    void ag10_2_missingTdMode() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("AG10.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("AG10.3 Негатив — несуществующий instId")
    void ag10_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR-SWAP", "tdMode", "isolated"), SIGNED);

        assertBusinessReject(r);
        observe("AG10.3", r);
    }
}
