package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG7. Set position mode — {@code POST /api/v5/account/set-position-mode}
 * (Account), {@code signed:true}. **WRITE (настройка {@code posMode}),
 * реверсивно.** Цепочка: Snapshot.start ({@code config.posMode}) → switch
 * (другой режим) → restore → Verify.end (sweep+halt до {@code posMode == старт}).
 * Реджект switch — находка (write-шаг не форсируем). Настройка под общей моделью
 * sweep+halt ({@code assertRestoredOrHalt}): restore в finally — первая попытка
 * sweep; авторитет «вернулось ли» — Verify.end. Невозврат {@code posMode} →
 * жёсткий фейл + находка C3 + принудительный re-set; не вычистило → halt прогона.
 */
@Order(37)
class Ag7SetPositionModeLiveTest extends OkxSourceApiLiveTestBase {

    private static final String SET_POSITION_MODE_PATH = "/api/v5/account/set-position-mode";
    private static final String NET_MODE = "net_mode";
    private static final String LONG_SHORT_MODE = "long_short_mode";

    @Test
    @Order(10)
    @DisplayName("AG7.1 Прямой (цепочка) — Snapshot.start → switch → restore → Verify.end")
    void ag7_1_switchAndRestore() {
        // Snapshot.start — снимок исходного posMode.
        RawResponse snapshot = get(ACCOUNT_CONFIG_PATH, null, SIGNED);
        assertOk(snapshot);
        final String start = snapshot.d0().path("posMode").asText("");
        assertThat(start).as("AG7.snapshot posMode").isIn(NET_MODE, LONG_SHORT_MODE);

        final String other = NET_MODE.equals(start) ? LONG_SHORT_MODE : NET_MODE;
        try {
            // switch — смена на другой режим; реджект → находка, не форсируем.
            RawResponse switched = post(SET_POSITION_MODE_PATH, map("posMode", other), SIGNED);
            observe("AG7.switch", switched);
            if (switched.businessReject() || switched.firstElementReject()) {
                log.info("[AG7.switch] posMode switch rejected — finding, residual-state flag (start={})", start);
            } else {
                assertOk(switched);
                assertThat(switched.d0().path("posMode").asText()).isEqualTo(other);
            }
        } finally {
            // restore — первая попытка sweep (вернуть исходный posMode); авторитет — Verify.end.
            RawResponse restore = post(SET_POSITION_MODE_PATH, map("posMode", start), SIGNED);
            observe("AG7.restore", restore);
            if (restore.businessReject() || restore.firstElementReject()) {
                log.info("[AG7.restore] posMode restore rejected — finding, residual-state flag (start={})", start);
            }
        }

        // Verify.end — posMode восстановлен к старту; настройка под sweep+halt:
        // невозврат → находка C3 + принудительный re-set posMode; не вычистило → halt.
        assertRestoredOrHalt("AG7.verify", "posMode == " + start,
                () -> {
                    RawResponse v = get(ACCOUNT_CONFIG_PATH, null, SIGNED);
                    return v.codeZero() && start.equals(v.d0().path("posMode").asText());
                },
                () -> post(SET_POSITION_MODE_PATH, map("posMode", start), SIGNED));
    }

    @Test
    @Order(20)
    @DisplayName("AG7.2 Негатив — битое значение posMode (вне домена)")
    void ag7_2_posModeOutOfDomain() {
        RawResponse r = post(SET_POSITION_MODE_PATH, map("posMode", "bogus_mode"), SIGNED);

        assertBusinessReject(r);
        observe("AG7.2", r);
    }
}
