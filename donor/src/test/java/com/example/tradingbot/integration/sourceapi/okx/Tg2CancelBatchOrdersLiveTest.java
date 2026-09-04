package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG2. Cancel batch orders — {@code POST /api/v5/trade/cancel-batch-orders}
 * (Trade, {@code signed:true}). Тело — массив {@code {instId, ordId|clOrdId}}.
 * Прямой богатый покрыт цепочкой TG1 (TG1.cancel); здесь — no-state негативы.
 */
@Order(23)
class Tg2CancelBatchOrdersLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/cancel-batch-orders";

    // TG2.1 прямой — покрыт TG1 (TG1.cancel by ordId), отдельным @Test не дублируется.

    @Test
    @Order(10)
    @DisplayName("TG2.2 негатив (no-state) — cancel несуществующих ордеров")
    void tg2_2_nonexistentOrders() {
        RawResponse r = post(PATH, List.of(
                map("instId", INST_ID, "ordId", "9999999999999999"),
                map("instId", INST_ID, "ordId", "9999999999999998")), SIGNED);

        assertHttp200(r);
        observe("TG2.2", r);
        assertThat(r.at(0).path("sCode").asText()).as("TG2.2 item0 reject").isNotEqualTo("0");
        assertThat(r.at(1).path("sCode").asText()).as("TG2.2 item1 reject").isNotEqualTo("0");
    }

    @Test
    @Order(20)
    @DisplayName("TG2.3 негатив — пропуск идентификатора в item (OKX-слой)")
    void tg2_3_missingIdentifier() {
        RawResponse r = post(PATH, List.of(map("instId", INST_ID)), SIGNED);

        assertRejectedAnyLevel("TG2.3", r);
    }
}
