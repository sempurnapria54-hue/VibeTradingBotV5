package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG6. Cancel All After (DMS) — {@code POST /api/v5/trade/cancel-all-after}
 * (Trade, {@code signed:true}). WRITE, но безопасно: {@code timeOut="0"}
 * выключает DMS (self-restoring — отдельный Snapshot/Verify не нужен).
 */
@Order(27)
class Tg6CancelAllAfterLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/cancel-all-after";

    @Test
    @Order(10)
    @DisplayName("TG6.1 прямой — timeOut=0 (DMS выключен)")
    void tg6_1_dmsOff() {
        RawResponse r = post(PATH, map("timeOut", "0"), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("triggerTime").asText()).as("TG6.1 triggerTime").isEqualTo("0");
        assertThat(r.d0().path("ts").isMissingNode()).as("TG6.1 ts present").isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("TG6.2 негатив — timeOut вне допустимого диапазона (OKX-слой)")
    void tg6_2_timeOutOutOfRange() {
        RawResponse r = post(PATH, map("timeOut", "5"), SIGNED);

        assertBusinessReject(r);
        observe("TG6.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("TG6.3 негатив — битый timeOut (нечисловой, OKX-слой)")
    void tg6_3_timeOutNonNumeric() {
        RawResponse r = post(PATH, map("timeOut", "abc"), SIGNED);

        assertBusinessReject(r);
        observe("TG6.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("TG6.4 негатив — пропуск обязательного timeOut (OKX-слой)")
    void tg6_4_missingTimeOut() {
        RawResponse r = post(PATH, map(), SIGNED);

        assertBusinessReject(r);
        observe("TG6.4", r);
    }
}
