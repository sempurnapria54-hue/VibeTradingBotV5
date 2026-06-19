package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG10. mark price candles history — {@code GET
 * /api/v5/market/history-mark-price-candles} (Market Data), {@code signed:false}.
 * READ-only, без auth/teardown. {@code data} — массив массивов-строк
 * {@code [ts, o, h, l, c, confirm]} (6 элементов), упорядочен по ts убыв.
 */
@Order(52)
class Mg10MarkPriceCandlesHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/history-mark-price-candles";

    @Test
    @Order(10)
    @DisplayName("MG10.1 прямой — history-mark-price-candles(ETH-USDT-SWAP, 1m, limit=10)")
    void mg10_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "1m", "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().isArray()).isTrue();
        assertThat(r.d0().size()).isEqualTo(6);
        assertThat(r.d0().path(0).asText("")).isNotBlank();
        long firstTs = Long.parseLong(r.d0().path(0).asText());
        long lastTs = Long.parseLong(r.at(r.dataSize() - 1).path(0).asText());
        assertThat(firstTs).as("data ordered by ts desc (first >= last)").isGreaterThanOrEqualTo(lastTs);
    }

    @Test
    @Order(20)
    @DisplayName("MG10.2 вариант — пагинация назад по after")
    void mg10_2_paginateByAfter() {
        RawResponse base = get(PATH, map("instId", INST_ID, "bar", "1m", "limit", "10"), PUBLIC);
        assertOk(base);
        assertThat(base.dataSize()).isPositive();
        String cursor = base.at(base.dataSize() - 1).path(0).asText("");
        assertThat(cursor).as("cursor ts from MG10.1 last candle").isNotBlank();
        long cursorTs = Long.parseLong(cursor);

        RawResponse r = get(PATH, map(
                "instId", INST_ID, "bar", "1m", "after", cursor, "limit", "10"), PUBLIC);

        assertOk(r);
        observe("MG10.2", r);
        assertThat(r.data().isArray()).isTrue();
        if (r.dataSize() > 0) {
            long firstTs = Long.parseLong(r.d0().path(0).asText());
            assertThat(firstTs).as("after — свечи строго старше курсора").isLessThan(cursorTs);
        }
    }

    @Test
    @Order(30)
    @DisplayName("MG10.3 негатив — фильтр из будущего (вне окна)")
    void mg10_3_futureAnchor() {
        RawResponse r = get(PATH, map(
                "instId", INST_ID, "bar", "1m", "after", "99999999999999"), PUBLIC);

        assertHttp200(r);
        observe("MG10.3", r);
        if (r.codeZero()) {
            assertThat(r.dataEmpty()).isTrue();
        }
    }

    @Test
    @Order(40)
    @DisplayName("MG10.4 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg10_4_missingInstId() {
        RawResponse r = get(PATH, map("bar", "1m"), PUBLIC);

        assertBusinessReject(r);
        observe("MG10.4", r);
    }
}
