package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG11. Max avail size — {@code GET /api/v5/account/max-avail-size} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: доступный
 * баланс/эквити под сделку ({@code availBuy}/{@code availSell}); {@code instId}
 * + {@code tdMode} обязательны.
 */
@Order(41)
class Ag11MaxAvailSizeLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/max-avail-size";

    @Test
    @Order(10)
    @DisplayName("AG11.1 Прямой — доступный баланс/эквити buy/sell")
    void ag11_1_directMaxAvail() {
        RawResponse r = get(PATH, map("instId", INST_ID, "tdMode", "isolated"), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("availBuy").asText("")).isNotBlank();
        assertThat(r.d0().path("availSell").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("AG11.2 Негатив — пропуск обязательного tdMode")
    void ag11_2_missingTdMode() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("AG11.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("AG11.3 Негатив — битое значение tdMode (вне домена)")
    void ag11_3_tdModeOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INST_ID, "tdMode", "bogus"), SIGNED);

        assertBusinessReject(r);
        observe("AG11.3", r);
    }
}
