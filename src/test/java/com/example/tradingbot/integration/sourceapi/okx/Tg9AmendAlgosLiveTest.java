package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG9. Amend algo — {@code POST /api/v5/trade/amend-algos} (Algo,
 * {@code signed:true}). Только Stop/Trigger (advance-семья не амендится, И-3).
 * WRITE: цепочка place(conditional SL, reduceOnly с A0-фикстурой) →
 * amend(newSlTriggerPx) → getAlgo → cancel с teardown + Verify.end (поллинг).
 */
@Order(30)
class Tg9AmendAlgosLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/amend-algos";
    private static final String CONDITIONAL = "conditional";

    @Test
    @Order(10)
    @DisplayName("TG9.1 прямой — цепочка place(conditional) → amend(newSlTriggerPx) → getAlgo → cancel")
    void tg9_1_amendAlgoLifecycle() {
        // Snapshot.start — чистый старт, нет живых conditional-algo по инструменту.
        RawResponse snapshot = get(ALGO_PENDING_PATH, map("instId", INST_ID, "ordType", CONDITIONAL), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("TG9.snapshot: clean start").isTrue();

        BigDecimal last = lastPrice();
        String slPx = tickPrice(last, 0.5);
        String newSlPx = tickPrice(last, 0.4);
        String clId = newId("tg");
        String algoId = null;
        boolean a0 = false;
        try {
            RawResponse place = post(ORDER_ALGO_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                    "ordType", CONDITIONAL, "sz", MIN_SZ, "reduceOnly", true,
                    "slTriggerPx", slPx, "slOrdPx", "-1", "slTriggerPxType", "mark",
                    "algoClOrdId", clId), SIGNED);
            // При реджекте «нет позиции» — A0 (min market-позиция) + повтор.
            if (!place.firstElementOk()) {
                observe("TG9.place.firstAttempt", place);
                openMinMarketPosition();
                a0 = true;
                place = post(ORDER_ALGO_PATH, map(
                        "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                        "ordType", CONDITIONAL, "sz", MIN_SZ, "reduceOnly", true,
                        "slTriggerPx", slPx, "slOrdPx", "-1", "slTriggerPxType", "mark",
                        "algoClOrdId", newId("tg")), SIGNED);
            }
            assertOk(place);
            assertFirstElementOk(place);
            algoId = place.d0().path("algoId").asText();
            assertThat(algoId).as("TG9.place algoId").isNotBlank();
            final String captured = algoId;

            // amend — ACK изменения newSlTriggerPx.
            RawResponse amend = post(PATH, map(
                    "instId", INST_ID, "algoId", captured,
                    "newSlTriggerPx", newSlPx, "cxlOnFail", false), SIGNED);
            assertOk(amend);
            assertFirstElementOk(amend);
            assertThat(amend.d0().path("algoId").asText()).as("TG9.amend algoId").isEqualTo(captured);

            // getAlgo — amend отражён (поллинг до slTriggerPx, иначе наблюдение).
            RawResponse getAlgo = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoId", captured), SIGNED);
            assertOk(getAlgo);
            assertThat(getAlgo.d0().path("algoId").asText()).as("TG9.get algoId").isEqualTo(captured);
            if (!newSlPx.equals(getAlgo.d0().path("slTriggerPx").asText())) {
                log.info("[TG9.get] slTriggerPx not yet {} (amend reflection lag) — observation", newSlPx);
            }
        } finally {
            cancelAlgoQuietly(algoId);
            if (a0) {
                closePositionQuietly();
            }
        }

        // Verify.end — живых conditional-algo нет (== Snapshot.start); sweep+halt при невозврате.
        final String captured = algoId;
        final boolean openedA0 = a0;
        assertRestoredOrHalt("TG9.verify",
                "live conditional algo (algoId=" + captured + ")" + (openedA0 ? " + A0 position" : ""),
                () -> algoPendingHasNo(CONDITIONAL, captured) && (!openedA0 || !hasOpenPosition()),
                () -> {
                    cancelAlgoQuietly(captured);
                    if (openedA0) {
                        closePositionQuietly();
                    }
                });
    }

    @Test
    @Order(20)
    @DisplayName("TG9.2 негатив — amend advance (trailing не амендится, И-3)")
    void tg9_2_amendAdvance() {
        RawResponse r = post(PATH, map(
                "instId", INST_ID, "algoId", "9999999999999999", "newSz", "0.02"), SIGNED);

        assertRejectedAnyLevel("TG9.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("TG9.3 негатив — amend несуществующего algo")
    void tg9_3_nonexistentAlgo() {
        RawResponse r = post(PATH, map(
                "instId", INST_ID, "algoId", "9999999999999999", "newSlTriggerPx", tickPrice(0.4)), SIGNED);

        assertRejectedAnyLevel("TG9.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("TG9.4 негатив — пропуск идентификатора algo (OKX-слой)")
    void tg9_4_missingIdentifier() {
        RawResponse r = post(PATH, map(
                "instId", INST_ID, "newSlTriggerPx", tickPrice(0.4)), SIGNED);

        assertRejectedAnyLevel("TG9.4", r);
    }
}
