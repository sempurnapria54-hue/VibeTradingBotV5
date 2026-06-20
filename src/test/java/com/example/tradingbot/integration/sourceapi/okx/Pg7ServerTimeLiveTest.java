package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG7. server time — {@code GET /api/v5/public/time} (Public Data),
 * {@code signed:false}. Серверное время API, Unix-мс строкой. Параметров нет.
 */
@Order(59)
class Pg7ServerTimeLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/time";

    @Test
    @Order(10)
    @DisplayName("PG7.1 Прямой — серверное время (без параметров)")
    void pg7_1_direct() {
        RawResponse r = get(PATH, null, PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("ts").isMissingNode()).isFalse();
        assertThat(Long.parseLong(r.d0().path("ts").asText())).isPositive();
    }

    @Test
    @Order(20)
    @DisplayName("PG7.2 Негатив — битый путь → non-2xx (OKX 404 нестандартное тело)")
    void pg7_2_brokenPath() {
        // RUN-факт (2026-06-19): неизвестный путь OKX отдаёт HTTP 404 с
        // нестандартным телом ({"code":404 (число), "data":{} (объект)}) →
        // /raw отдаёт non-2xx (как M1.6), а не реджект/пусто 200. Находка C3.
        RawResponse r = get("/api/v5/public/time-WRONG", null, PUBLIC);

        log.info("[PG7.2] OBSERVE broken-path http={} body={}", r.status(), r.rawBody());
        assertThat(r.status())
                .as("broken path → non-2xx (OKX 404 non-standard body)")
                .isGreaterThanOrEqualTo(400);
    }
}
