package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
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

        // Исход кейса — ПЕРЕЧЕНЬ, а не мощность (предусловия CODE пп. 2 и 16):
        // прогон 2026-06-20 сохранил data.size=32 и потерял содержание. Сверх
        // того, 32 — счёт ВЕРХНИХ типов; пар type/subType, которые и являются
        // операндом отображения, кратно больше.
        observeContent("AG6.1", r);

        List<String> pairs = new ArrayList<>();
        for (JsonNode type : r.data()) {
            String typeCode = type.path("type").asText("");
            String typeDesc = type.path("typeDesc").asText("");
            assertThat(typeCode).as("AG6.1 → элемент справочника несёт type").isNotBlank();
            JsonNode details = type.path("subTypeDetails");
            assertThat(details.isArray()).as("AG6.1 → type=%s несёт subTypeDetails массивом", typeCode).isTrue();
            for (JsonNode sub : details) {
                String subCode = sub.path("subType").asText("");
                assertThat(subCode).as("AG6.1 → type=%s: подтип несёт subType", typeCode).isNotBlank();
                pairs.add(typeCode + "/" + subCode + " — " + typeDesc + " / " + sub.path("subTypeDesc").asText(""));
            }
        }
        observeValue("AG6.1", "typeCount", r.dataSize());
        observeValue("AG6.1", "typeSubTypePairCount", pairs.size());
        pairs.forEach(pair -> observeValue("AG6.1", "pair", pair));
        assertThat(pairs).as("AG6.1 → перечень пар type/subType непуст").isNotEmpty();
    }
}
