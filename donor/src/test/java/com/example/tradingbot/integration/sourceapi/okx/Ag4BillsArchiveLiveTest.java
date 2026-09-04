package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG4. Bills archive 3m — {@code GET /api/v5/account/bills-archive} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой частично достижим:
 * запрос проходит, но на свежем demo окно 3м пусто → проверяем форму массива,
 * не значения; пустой {@code data} — не ошибка.
 */
@Order(34)
class Ag4BillsArchiveLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/bills-archive";

    @Test
    @Order(10)
    @DisplayName("AG4.1 Прямой — окно 3м, пустой массив валиден (форма)")
    void ag4_1_directEmptyValid() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "ccy", "USDT"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
        if (!r.dataEmpty()) {
            assertThat(r.d0().path("billId").isMissingNode()).isFalse();
            assertThat(r.d0().path("type").isMissingNode()).isFalse();
            assertThat(r.d0().path("ts").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(20)
    @DisplayName("AG4.2 Негатив — пагинация after по billId вне окна (after=1 → пусто)")
    void ag4_2_paginationOutOfWindow() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "after", 1, "limit", 10), SIGNED);

        observe("AG4.2", r);
        assertThat(r.codeZero() || r.businessReject()).isTrue();
        if (r.codeZero()) {
            assertThat(r.data().isArray()).isTrue();
        }
    }

    @Test
    @Order(30)
    @DisplayName("AG4.3 Негатив — битое значение instType (вне домена)")
    void ag4_3_instTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("AG4.3", r);
    }
}
