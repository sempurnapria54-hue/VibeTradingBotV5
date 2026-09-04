package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG1. Positions history — {@code GET /api/v5/account/positions-history}
 * (Account), {@code signed:true}. Read-only, без teardown. Прямой частично
 * достижим: запрос проходит, но на свежем demo {@code data} валидно пуст
 * (закрытых позиций нет) → проверяем форму массива, не значения.
 */
@Order(31)
class Ag1PositionsHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/positions-history";

    @Test
    @Order(10)
    @DisplayName("AG1.1 Прямой — пустое окно валидно (форма)")
    void ag1_1_directEmptyWindow() {
        RawResponse r = get(PATH, map("instType", INST_TYPE), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
        if (!r.dataEmpty()) {
            assertThat(r.d0().path("posId").isMissingNode()).isFalse();
            assertThat(r.d0().path("instId").isMissingNode()).isFalse();
            assertThat(r.d0().path("mgnMode").isMissingNode()).isFalse();
            assertThat(r.d0().path("posSide").isMissingNode()).isFalse();
            assertThat(r.d0().path("realizedPnl").isMissingNode()).isFalse();
            assertThat(r.d0().path("uTime").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(20)
    @DisplayName("AG1.2 Негатив — битое значение mgnMode (вне домена)")
    void ag1_2_mgnModeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "mgnMode", "bogus"), SIGNED);

        assertBusinessReject(r);
        observe("AG1.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("AG1.3 Негатив — битое значение type (вне домена 1..6)")
    void ag1_3_typeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "type", 99), SIGNED);

        assertBusinessReject(r);
        observe("AG1.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("AG1.4 Негатив — пагинация по uTime вне окна (after=1 → пусто)")
    void ag1_4_paginationOutOfWindow() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "after", 1, "limit", 10), SIGNED);

        observe("AG1.4", r);
        assertThat(r.codeZero() || r.businessReject()).isTrue();
        if (r.codeZero()) {
            assertThat(r.data().isArray()).isTrue();
        }
    }
}
