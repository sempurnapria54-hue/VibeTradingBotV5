package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M12. positions — {@code GET /api/v5/account/positions} (Get positions),
 * {@code signed:true}. Прямой богатый (M12.3) покрыт цепочкой Cmarket (M16).
 */
@Order(12)
class M12PositionsLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/positions";

    @Test
    @Order(10)
    @DisplayName("M12.1 прямой (no-state) — пустой/валидный (нет позиции)")
    void m12_1_emptyValid() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);

        assertOk(r);
        assertThat(r.dataEmpty() || !r.d0().path("posId").isMissingNode())
                .as("пустой data либо data[0].posId присутствует")
                .isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M12.2 негатив — несущ. instId")
    void m12_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", "FOO-BAR"), SIGNED);

        assertRejectOrEmpty("M12.2", r);
    }

    // M12.3 (прямой богатый через исполнение) — покрыт цепочкой Cmarket (M16).
}
