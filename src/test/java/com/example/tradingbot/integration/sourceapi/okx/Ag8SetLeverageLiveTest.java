package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG8. Set leverage — {@code POST /api/v5/account/set-leverage} (Account),
 * {@code signed:true}. **WRITE, реверсивно.** Цепочка: read leverage
 * ({@code leverage-info}) → set {@code lever=3} → restore (prev) → Verify.end
 * (sweep+halt до {@code lever == prev}). Реджект set — находка (write-шаг не
 * форсируем). Настройка под общей моделью sweep+halt
 * ({@code assertRestoredOrHalt}): restore в finally — первая попытка sweep;
 * авторитет — Verify.end. Невозврат {@code lever} → жёсткий фейл + находка C3 +
 * принудительный re-set; не вычистило → halt прогона.
 */
@Order(38)
class Ag8SetLeverageLiveTest extends OkxSourceApiLiveTestBase {

    private static final String SET_LEVERAGE_PATH = "/api/v5/account/set-leverage";
    private static final String LEVERAGE_INFO_PATH = "/api/v5/account/leverage-info";
    private static final String ISOLATED = "isolated";

    @Test
    @Order(10)
    @DisplayName("AG8.1 Прямой (цепочка) — read leverage → set → restore (реверсивно)")
    void ag8_1_setAndRestore() {
        // шаг 1 — прочитать текущий leverage инструмента (isolated).
        RawResponse snapshot = get(LEVERAGE_INFO_PATH, map("instId", INST_ID, "mgnMode", ISOLATED), SIGNED);
        assertOk(snapshot);
        final String prevLever = snapshot.d0().path("lever").asText("");
        assertThat(prevLever).as("AG8.snapshot lever").isNotBlank();

        try {
            // шаг 2 — set малого значения lever=3, эхо параметров.
            RawResponse set = post(SET_LEVERAGE_PATH,
                    map("instId", INST_ID, "lever", "3", "mgnMode", ISOLATED), SIGNED);
            observe("AG8.set", set);
            if (set.businessReject() || set.firstElementReject()) {
                log.info("[AG8.set] set-leverage rejected — finding, residual-state flag (prev={})", prevLever);
            } else {
                assertOk(set);
                assertThat(set.d0().path("lever").asText()).isEqualTo("3");
                assertThat(set.d0().path("mgnMode").asText()).isEqualTo(ISOLATED);
                assertThat(set.d0().path("instId").asText()).isEqualTo(INST_ID);
            }
        } finally {
            // шаг 3 (restore) — первая попытка sweep (вернуть исходный leverage); авторитет — Verify.end.
            RawResponse restore = post(SET_LEVERAGE_PATH,
                    map("instId", INST_ID, "lever", prevLever, "mgnMode", ISOLATED), SIGNED);
            observe("AG8.restore", restore);
            if (restore.businessReject() || restore.firstElementReject()) {
                log.info("[AG8.restore] restore rejected — finding, residual-state flag (prev={})", prevLever);
            }
        }

        // шаг 4 (Verify.end) — плечо восстановлено к исходному; настройка под sweep+halt:
        // невозврат → находка C3 + принудительный re-set lever; не вычистило → halt.
        assertRestoredOrHalt("AG8.verify", "lever == " + prevLever,
                () -> {
                    RawResponse v = get(LEVERAGE_INFO_PATH, map("instId", INST_ID, "mgnMode", ISOLATED), SIGNED);
                    return v.codeZero() && prevLever.equals(v.d0().path("lever").asText());
                },
                () -> post(SET_LEVERAGE_PATH, map("instId", INST_ID, "lever", prevLever, "mgnMode", ISOLATED), SIGNED));
    }

    @Test
    @Order(20)
    @DisplayName("AG8.2 Негатив — битое значение lever (нечисловое)")
    void ag8_2_leverNonNumeric() {
        RawResponse r = post(SET_LEVERAGE_PATH,
                map("instId", INST_ID, "lever", "abc", "mgnMode", ISOLATED), SIGNED);

        assertBusinessReject(r);
        observe("AG8.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("AG8.3 Негатив — битое значение mgnMode (вне домена)")
    void ag8_3_mgnModeOutOfDomain() {
        RawResponse r = post(SET_LEVERAGE_PATH,
                map("instId", INST_ID, "lever", "3", "mgnMode", "bogus"), SIGNED);

        assertBusinessReject(r);
        observe("AG8.3", r);
    }
}
