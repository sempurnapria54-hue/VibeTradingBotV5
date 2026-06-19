package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG3. Bills 7d — {@code GET /api/v5/account/bills} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: массив
 * bill-записей за 7 дней; пустой массив валиден, если движений не было.
 */
@Order(33)
class Ag3BillsLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/bills";

    @Test
    @Order(10)
    @DisplayName("AG3.1 Прямой — массив bills, пустой валиден (форма)")
    void ag3_1_directEmptyValid() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "ccy", "USDT"), SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
        if (!r.dataEmpty()) {
            assertThat(r.d0().path("billId").isMissingNode()).isFalse();
            assertThat(r.d0().path("type").isMissingNode()).isFalse();
            assertThat(r.d0().path("subType").isMissingNode()).isFalse();
            assertThat(r.d0().path("ts").isMissingNode()).isFalse();
            assertThat(r.d0().path("balChg").isMissingNode()).isFalse();
            assertThat(r.d0().path("ccy").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(20)
    @DisplayName("AG3.2 Негатив — фильтр begin/end вне окна (begin>end → пусто или реджект)")
    void ag3_2_invertedWindow() {
        RawResponse r = get(PATH,
                map("instType", INST_TYPE, "ccy", "USDT", "begin", 9999999999999L, "end", 1), SIGNED);

        observe("AG3.2", r);
        assertThat(r.businessReject() || r.dataEmpty()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("AG3.3 Негатив — битое значение type (вне домена справочника)")
    void ag3_3_typeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "type", 99999), SIGNED);

        observe("AG3.3", r);
        assertThat(r.businessReject() || r.dataEmpty()).isTrue();
    }
}
