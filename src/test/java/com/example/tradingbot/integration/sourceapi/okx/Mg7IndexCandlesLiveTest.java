package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG7. index candles — {@code GET /api/v5/market/index-candles} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown. {@code data} — массив
 * массивов-строк свечи индекса {@code [ts, o, h, l, c, confirm]} (6 элементов,
 * без объёма).
 */
@Order(49)
class Mg7IndexCandlesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/index-candles";
    private static final String HISTORY_PATH = "/api/v5/market/history-index-candles";
    private static final String MARKET_CANDLES_PATH = "/api/v5/market/candles";

    @Test
    @Order(10)
    @DisplayName("MG7.1 прямой — index-candles(ETH-USDT, 1m, limit=10)")
    void mg7_1_direct() {
        RawResponse r = get(PATH, map("instId", INDEX_INST_ID, "bar", "1m", "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().isArray()).isTrue();
        assertThat(r.d0().size()).isEqualTo(6);
        assertThat(r.d0().path(0).asText("")).isNotBlank();
        assertThat(r.d0().path(5).asText("")).isIn("0", "1");
    }

    @Test
    @Order(20)
    @DisplayName("MG7.2 негатив — bar вне домена (OKX-реджект)")
    void mg7_2_barOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INDEX_INST_ID, "bar", "99z"), PUBLIC);

        assertBusinessReject(r);
        observe("MG7.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG7.3 негатив — несущ. индекс instId (OKX-реджект/пустой)")
    void mg7_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR", "bar", "1m"), PUBLIC);

        assertRejectOrEmpty("MG7.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG7.4 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg7_4_missingInstId() {
        RawResponse r = get(PATH, map("bar", "1m"), PUBLIC);

        assertBusinessReject(r);
        observe("MG7.4", r);
    }

    @Test
    @Order(50)
    @DisplayName("MG7.5 Содержательный (шаг 7) — носитель курса cross-ccy")
    void mg7_5_crossCcyRateCarrier() {
        // (1) Какие bar принимает источник и есть ли среди них секундное
        // разрешение — носитель курса «на момент операции».
        List<String> acceptedBars = new ArrayList<>();
        for (String bar : List.of("1s", "1m", "3m", "5m", "15m", "1H", "1D")) {
            RawResponse r = get(PATH, map("instId", INDEX_INST_ID, "bar", bar, "limit", "2"), PUBLIC);
            boolean accepted = r.codeZero() && r.dataSize() > 0;
            observeValue("MG7.5", "bar=" + bar + " accepted", accepted + " (code=" + r.code() + ", n=" + r.dataSize() + ")");
            if (accepted) {
                acceptedBars.add(bar);
            }
        }
        observeValue("MG7.5", "acceptedBars", acceptedBars);
        assertThat(acceptedBars)
                .as("MG7.5 → секундное разрешение есть у пары котировки: без него пересчёт cross-ccy "
                        + "не имеет носителя «на момент операции»")
                .contains("1s");

        // (3) Какая цена берётся из свечи: форма строки — [ts, o, h, l, c, confirm];
        // close интервала — индекс 4, признак закрытости — индекс 5.
        RawResponse shape = get(PATH, map("instId", INDEX_INST_ID, "bar", "1s", "limit", "3"), PUBLIC);
        assertOk(shape);
        assertThat(shape.d0().size()).as("MG7.5 → строка свечи индекса — 6 элементов").isEqualTo(6);
        observeContent("MG7.5", shape);
        observeValue("MG7.5", "closeIndex4", shape.d0().path(4).asText(""));
        observeValue("MG7.5", "confirmIndex5", shape.d0().path(5).asText(""));

        // (4) Стоимость по квоте: сколько строк отдаётся одним запросом —
        // от этого зависит, берётся ли диапазон движений разом или построчно.
        RawResponse batch = get(PATH, map("instId", INDEX_INST_ID, "bar", "1s", "limit", "300"), PUBLIC);
        assertOk(batch);
        observeValue("MG7.5", "maxRowsPerRequest(limit=300)", batch.dataSize());
        assertThat(batch.dataSize())
                .as("MG7.5 → диапазон берётся одним запросом, а не построчно")
                .isGreaterThan(1);

        // (2) Глубина каждого разрешения — правило деградации. Свежий
        // эндпоинт окно в прошлом не обслуживает; носитель глубины —
        // history-index-candles. Обе оси наблюдаются, а не предполагаются.
        long now = System.currentTimeMillis();
        for (String bar : List.of("1s", "1m")) {
            for (int days : List.of(1, 30, 120, 180, 365)) {
                long after = now - days * 86_400_000L;
                Map<String, Object> query = new LinkedHashMap<>(map(
                        "instId", INDEX_INST_ID, "bar", bar, "after", String.valueOf(after), "limit", "1"));
                RawResponse fresh = get(PATH, query, PUBLIC);
                RawResponse hist = get(HISTORY_PATH, query, PUBLIC);
                observeValue("MG7.5", "depth bar=" + bar + " -" + days + "d",
                        "index-candles n=" + fresh.dataSize() + ", history-index-candles n=" + hist.dataSize());
            }
        }

        // (5) Доступность самих пар при SWAP-only контуре: индексная свеча
        // против рыночной по той же паре.
        RawResponse marketCandles = get(MARKET_CANDLES_PATH, map("instId", INDEX_INST_ID, "bar", "1s", "limit", "2"), PUBLIC);
        observeValue("MG7.5", "market-candles(ETH-USDT, 1s)",
                "code=" + marketCandles.code() + ", n=" + marketCandles.dataSize());
    }
}
