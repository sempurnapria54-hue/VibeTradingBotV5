package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG4. funding rate history —
 * {@code GET /api/v5/public/funding-rate-history} (Public Data),
 * {@code signed:false}. История ставок (глубина 3 мес), пагинация по окну
 * {@code fundingTime}.
 */
@Order(56)
class Pg4FundingRateHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/funding-rate-history";

    @Test
    @Order(10)
    @DisplayName("PG4.1 Прямой — история funding по instId с limit")
    void pg4_1_directWithLimit() {
        RawResponse r = get(PATH, map("instId", INST_ID, "limit", "5"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.dataSize()).isLessThanOrEqualTo(5);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("fundingTime").isMissingNode()).isFalse();
        assertThat(r.d0().path("fundingRate").isMissingNode()).isFalse();
        assertThat(r.d0().path("realizedRate").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PG4.2 Прямой — пагинация по окну (before по fundingTime)")
    void pg4_2_paginationBefore() {
        RawResponse first = get(PATH, map("instId", INST_ID, "limit", "5"), PUBLIC);
        assertOk(first);
        assertThat(first.dataSize()).isPositive();
        String cursor = first.d0().path("fundingTime").asText();
        assertThat(cursor).isNotBlank();
        long cursorTime = Long.parseLong(cursor);

        RawResponse r = get(PATH, map("instId", INST_ID, "before", cursor, "limit", "5"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isLessThanOrEqualTo(5);
        for (JsonNode element : r.data()) {
            String fundingTime = element.path("fundingTime").asText();
            assertThat(fundingTime).isNotBlank();
            assertThat(Long.parseLong(fundingTime))
                    .as("fundingTime strictly greater than before-cursor")
                    .isGreaterThan(cursorTime);
        }
    }

    @Test
    @Order(30)
    @DisplayName("PG4.3 Негатив — пропуск обязательного instId (OKX-реджект)")
    void pg4_3_missingInstId() {
        RawResponse r = get(PATH, map("limit", "5"), PUBLIC);

        assertBusinessReject(r);
        observe("PG4.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("PG4.4 Негатив — несуществующий instId (реджект ИЛИ пустой data)")
    void pg4_4_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "NOPE-USDT-SWAP", "limit", "5"), PUBLIC);

        assertRejectOrEmpty("PG4.4", r);
    }
}
