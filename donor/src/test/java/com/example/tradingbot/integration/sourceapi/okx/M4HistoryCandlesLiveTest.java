package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M4. history-candles — {@code GET /api/v5/market/history-candles}
 * (Market Data), {@code signed:false}. Пагинация назад по {@code after}.
 */
@Order(4)
class M4HistoryCandlesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/history-candles";

    @Test
    @Order(10)
    @DisplayName("M4.1 прямой — history-candles(ETH-USDT-SWAP, 1m, limit=10)")
    void m4_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "1m", "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().isArray()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M4.2 вариант — пагинация назад по after")
    void m4_2_paginationByAfter() {
        RawResponse base = get(PATH, map("instId", INST_ID, "bar", "1m", "limit", "10"), PUBLIC);
        assertOk(base);
        assertThat(base.dataSize()).isPositive();

        String oldestTs = base.at(base.dataSize() - 1).path(0).asText();
        assertThat(oldestTs).isNotBlank();
        BigDecimal after = new BigDecimal(oldestTs);

        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "1m", "after", oldestTs, "limit", "10"), PUBLIC);

        assertOk(r);
        observe("M4.2", r);
        for (int i = 0; i < r.dataSize(); i++) {
            assertThat(new BigDecimal(r.at(i).path(0).asText()))
                    .as("candle ts strictly older than after=%s", oldestTs)
                    .isLessThan(after);
        }
    }

    @Test
    @Order(30)
    @DisplayName("M4.3 негатив — фильтр из будущего (вне окна)")
    void m4_3_filterFromFuture() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "1m", "after", "99999999999999"), PUBLIC);

        observe("M4.3", r);
        if (!r.dataEmpty()) {
            log.info("[M4.3] non-empty data on future anchor — поведение фиксируем фактом");
        }
    }

    @Test
    @Order(40)
    @DisplayName("M4.4 негатив — пропуск обязательного instId (OKX-слой)")
    void m4_4_missingInstId() {
        RawResponse r = get(PATH, map("bar", "1m"), PUBLIC);

        assertBusinessReject(r);
        observe("M4.4", r);
    }
}
