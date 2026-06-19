package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG6. Bill types — {@code GET /api/v5/account/subtypes} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: справочник
 * {@code type}/{@code subType} bill-записей, всегда непустой перечень.
 */
@Order(36)
class Ag6BillSubtypesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/subtypes";

    @Test
    @Order(10)
    @DisplayName("AG6.1 Прямой — справочник типов bills")
    void ag6_1_directDictionary() {
        RawResponse r = get(PATH, null, SIGNED);

        assertOk(r);
        assertThat(r.data().isArray()).isTrue();
        assertThat(r.dataEmpty()).isFalse();
        observe("AG6.1", r);
    }
}
