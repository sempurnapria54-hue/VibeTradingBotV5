package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG5. Order history 3m — {@code GET /api/v5/trade/orders-history-archive}
 * (Trade, {@code signed:true}). READ. Прямой = валидный массив (пустой допустим
 * на свежем demo — содержимое не выдумывается); негативы достижимы.
 */
@Order(26)
class Tg5OrdersHistoryArchiveLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/orders-history-archive";

    @Test
    @Order(10)
    @DisplayName("TG5.1 прямой — orders-history-archive(SWAP, instId) (пустой валиден)")
    void tg5_1_directArchive() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);

        assertOk(r);
        // empty data valid on fresh demo — content not invented
        assertThat(r.data().isArray()).as("TG5.1 data is array").isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("TG5.2 негатив — пропуск обязательного instType (OKX-слой)")
    void tg5_2_missingInstType() {
        RawResponse r = get(PATH, map("instId", INST_ID), SIGNED);

        assertBusinessReject(r);
        observe("TG5.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("TG5.3 негатив — instType вне домена (OKX-слой)")
    void tg5_3_instTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("TG5.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("TG5.4 негатив — state вне домена (OKX-слой)")
    void tg5_4_stateOutOfDomain() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "state", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("TG5.4", r);
    }
}
