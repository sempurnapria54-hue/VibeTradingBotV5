package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG7. Order precheck — {@code POST /api/v5/trade/order-precheck} (Trade,
 * {@code signed:true}). Dry-run пре-оценка — ордер НЕ ставится. Применим только
 * acctLv 3/4 (MCM/PM): кейс разведочно переключает {@code acctLv}→3, прогоняет
 * precheck, восстанавливает исходный {@code acctLv} (инвариант восстановления).
 *
 * <p>{@code set-account-level} — тест-фикстура (эндпоинт вне периметра).
 * {@code tdMode} под MCM per-contract: field-presence ассертится только при
 * {@code code="0"}, иначе реджект precheck = наблюдение (не fail). Set-реджект
 * (перевод в MCM) → observe + находка, не форсируем. {@code acctLv} под общей
 * моделью sweep+halt ({@code assertRestoredOrHalt}): restore в finally — первая
 * попытка sweep, Verify.end — авторитет; невозврат {@code acctLv} → жёсткий фейл
 * + находка C3 + принудительный re-set; не вычистило → halt прогона.
 */
@Order(28)
class Tg7OrderPrecheckLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/order-precheck";
    private static final String SET_ACCOUNT_LEVEL_PATH = "/api/v5/account/set-account-level";

    @Test
    @Order(10)
    @DisplayName("TG7.1 разведочный (цепочка) — snapshot → set acctLv 3 → precheck → restore → verify")
    void tg7_1_precheckChain() {
        // Snapshot.start — снимок исходного acctLv.
        RawResponse snapshot = get(ACCOUNT_CONFIG_PATH, null, SIGNED);
        assertOk(snapshot);
        String startAcctLv = snapshot.d0().path("acctLv").asText("");
        assertThat(startAcctLv).as("TG7.snapshot acctLv").isNotBlank();

        try {
            // setLv — перевод в MCM для применимости precheck (реджект → находка).
            RawResponse setLv = post(SET_ACCOUNT_LEVEL_PATH, map("acctLv", "3"), SIGNED);
            observe("TG7.setLv", setLv);
            if (!setLv.codeZero()) {
                log.info("[TG7.setLv] set acctLv=3 rejected (msg={}) — finding: precheck applicability not "
                        + "reachable, precheck result observed only", setLv.msg());
            }

            // price — реалистичная limit-цена.
            String px = tickPrice(1.0);

            // check — документируем реальный ответ precheck (field-presence только при code=0).
            RawResponse check = post(PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px), SIGNED);
            assertHttp200(check);
            observe("TG7.check", check);
            if (check.codeZero()) {
                assertThat(check.d0().path("adjEq").isMissingNode()).as("TG7.check adjEq present").isFalse();
                assertThat(check.d0().path("imr").isMissingNode()).as("TG7.check imr present").isFalse();
                assertThat(check.d0().path("mmr").isMissingNode()).as("TG7.check mmr present").isFalse();
            } else {
                log.info("[TG7.check] precheck rejected (b.code={} b.msg={}) — observation, not fail "
                        + "(tdMode under MCM per-contract; miss may be unrelated to reachability)",
                        check.code(), check.msg());
            }
        } finally {
            // restore — первая попытка sweep (восстановить исходный acctLv); авторитет — Verify.end.
            RawResponse restore = post(SET_ACCOUNT_LEVEL_PATH, map("acctLv", startAcctLv), SIGNED);
            observe("TG7.restore", restore);
            if (!restore.codeZero()) {
                log.warn("[TG7.restore] restore acctLv={} rejected (msg={}) — finding + residual acctLv flag",
                        startAcctLv, restore.msg());
            }
        }

        // Verify.end — acctLv восстановлен (== Snapshot.start); настройка под sweep+halt:
        // невозврат → находка C3 + принудительный re-set acctLv; не вычистило → halt.
        assertRestoredOrHalt("TG7.verify", "acctLv == " + startAcctLv,
                () -> {
                    RawResponse g = get(ACCOUNT_CONFIG_PATH, null, SIGNED);
                    return g.codeZero() && startAcctLv.equals(g.d0().path("acctLv").asText(""));
                },
                () -> post(SET_ACCOUNT_LEVEL_PATH, map("acctLv", startAcctLv), SIGNED));
    }

    @Test
    @Order(20)
    @DisplayName("TG7.2 негатив — пропуск обязательного instId (OKX-слой)")
    void tg7_2_missingInstId() {
        RawResponse r = post(PATH, map(
                "tdMode", "isolated", "side", "buy", "ordType", "limit", "sz", MIN_SZ), SIGNED);

        assertBusinessReject(r);
        observe("TG7.2", r);
    }
}
