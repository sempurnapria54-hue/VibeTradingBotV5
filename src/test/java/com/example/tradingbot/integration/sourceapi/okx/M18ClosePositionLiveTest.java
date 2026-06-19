package com.example.tradingbot.integration.sourceapi.okx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M18. closePosition — {@code POST /api/v5/trade/close-position} (Close
 * position), {@code signed:true}. Прямой (закрытие реальной позиции) покрыт
 * цепочкой Cmarket (M16, не дублируется). Здесь — no-state негативы.
 */
@Order(18)
class M18ClosePositionLiveTest extends OkxSourceApiLiveTestBase {

    @Test
    @Order(10)
    @DisplayName("M18.1 негатив (no-state) — close без позиции (состояние-конфликт)")
    void m18_1_closeWithoutPosition() {
        RawResponse r = post(CLOSE_POSITION_PATH, map(
                "instId", INST_ID, "mgnMode", "isolated", "posSide", "net",
                "autoCxl", true, "ccy", "USDT"), SIGNED);

        assertRejectedAnyLevel("M18.1", r);
    }

    @Test
    @Order(20)
    @DisplayName("M18.2 негатив — пропуск обязательного instId (OKX-слой)")
    void m18_2_missingInstId() {
        RawResponse r = post(CLOSE_POSITION_PATH, map(
                "mgnMode", "isolated", "posSide", "net", "autoCxl", true, "ccy", "USDT"), SIGNED);

        assertRejectedAnyLevel("M18.2", r);
    }
}
