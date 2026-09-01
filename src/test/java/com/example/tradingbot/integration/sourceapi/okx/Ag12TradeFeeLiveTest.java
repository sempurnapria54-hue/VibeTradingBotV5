package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG12. Fee rates — {@code GET /api/v5/account/trade-fee} (Account),
 * {@code signed:true}. Read-only, без teardown. Прямой достижим: ставки
 * комиссий аккаунта; {@code instType} обязателен. Знаковая конвенция
 * (минус = комиссия) — наблюдение при RUN.
 */
@Order(42)
class Ag12TradeFeeLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/trade-fee";

    @Test
    @Order(10)
    @DisplayName("AG12.1 Прямой — ставки комиссий + знаковая конвенция")
    void ag12_1_directFeeRates() {
        RawResponse r = get(PATH, map("instType", INST_TYPE, "instFamily", INST_FAMILY), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("level").asText("")).isNotBlank();
        assertThat(r.d0().path("maker").isMissingNode()).isFalse();
        assertThat(r.d0().path("taker").isMissingNode()).isFalse();
        assertThat(r.d0().path("feeGroup").isArray()).isTrue();
        assertThat(r.d0().path("instType").asText()).isEqualTo(INST_TYPE);
        observeContent("AG12.1", r);
        persistObservation("AG12.1", "ставки комиссий по группам (instType=" + INST_TYPE + ")", feeGroups(r));
    }

    /**
     * Перечень групп со ставками — исход кейса есть ПЕРЕЧЕНЬ, а не мощность:
     * ставка резолвится по паре (instType, groupId), а знак источника
     * определяет, снимается ли он маппингом. И то и другое проверяемо только
     * по содержанию ответа.
     */
    private List<String> feeGroups(RawResponse r) {
        List<String> lines = new ArrayList<>();
        lines.add("level=" + r.d0().path("level").asText(""));
        lines.add("instType=" + r.d0().path("instType").asText(""));
        lines.add("плоские (deprecated) taker=" + r.d0().path("taker").asText("")
                + " maker=" + r.d0().path("maker").asText(""));
        for (JsonNode group : r.d0().path("feeGroup")) {
            lines.add("groupId=" + group.path("groupId").asText("")
                    + " taker=" + group.path("taker").asText("")
                    + " maker=" + group.path("maker").asText("")
                    + " elpMaker=" + group.path("elpMaker").asText("")
                    + " rpiMaker=" + group.path("rpiMaker").asText(""));
        }
        return lines;
    }

    @Test
    @Order(20)
    @DisplayName("AG12.2 Негатив — пропуск обязательного instType")
    void ag12_2_missingInstType() {
        RawResponse r = get(PATH, map("instFamily", INST_FAMILY), SIGNED);

        assertBusinessReject(r);
        observe("AG12.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("AG12.3 Негатив — битое значение instType (вне домена)")
    void ag12_3_instTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", "BOGUS"), SIGNED);

        assertBusinessReject(r);
        observe("AG12.3", r);
    }
}
