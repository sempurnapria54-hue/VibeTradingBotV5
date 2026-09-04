package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M10. fills 3d — {@code GET /api/v5/trade/fills} (Fills 3d),
 * {@code signed:true}. Прямой богатый (M10.4) покрыт цепочкой Cmarket (M16).
 */
@Order(10)
class M10FillsLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/fills";

    @Test
    @Order(10)
    @DisplayName("M10.1 прямой (no-state) — пустой/валидный до исполнения")
    void m10_1_emptyValid() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M10.2 негатив — фильтр из будущего / вне окна")
    void m10_2_filterFromFuture() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID, "begin", "99999999999999"), SIGNED);

        observe("M10.2", r);
        assertThat(r.dataEmpty()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("M10.3 негатив — пропуск обязательного instId (OKX-слой)")
    void m10_3_missingInstId() {
        RawResponse r = get(PATH, map("instType", INST_TYPE), SIGNED);

        observe("M10.3", r);
        assertThat(r.codeZero() || r.businessReject())
                .as("instId опционален в OKX fills: ok-by-all-instId либо reject (наблюдение)")
                .isTrue();
    }

    // M10.4 (прямой богатый через исполнение) — покрыт цепочкой Cmarket (M16).
}
