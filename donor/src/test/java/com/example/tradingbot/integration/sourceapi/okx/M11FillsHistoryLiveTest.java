package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M11. fills-history 3m — {@code GET /api/v5/trade/fills-history} (Fills 3m),
 * {@code signed:true}. Прямой богатый (M11.4) покрыт цепочкой Cmarket (M16).
 */
@Order(11)
class M11FillsHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/fills-history";

    @Test
    @Order(10)
    @DisplayName("M11.1 прямой (no-state) — пустой/валидный")
    void m11_1_emptyValid() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M11.2 негатив — фильтр из будущего")
    void m11_2_filterFromFuture() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID, "begin", "99999999999999"), SIGNED);

        observe("M11.2", r);
        assertThat(r.dataEmpty()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("M11.3 негатив — пропуск обязательного instType (OKX-слой)")
    void m11_3_missingInstType() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        observe("M11.3", r);
        assertThat(r.businessReject() || r.codeZero())
                .as("instType обязателен в fills-history (офдок) → reject; исход — наблюдение")
                .isTrue();
    }

    // M11.4 (прямой богатый) — покрыт цепочкой Cmarket (M16).
}
