package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M19. placeAlgoOrder — {@code POST /api/v5/trade/order-algo} (Place algo
 * order), {@code signed:true}. Тело строится руками по контракту. Варианты
 * ordType: conditional (SL/TP), oco (обе ноги), move_order_stop (trailing:
 * callbackRatio / callbackSpread). WRITE: каждая цепочка с teardown +
 * Verify.end (поллинг). Покрывает попутно M13/M14/M15 (богатые состояния) и
 * M20/M21 прямой (ветви cancel ordinary/advance).
 *
 * <p>A0-фикстура: protective algo ({@code reduceOnly=true}) может требовать
 * позиции; при реджекте place открывается min market-позиция (A0) и place
 * повторяется, позиция закрывается в teardown.
 *
 * <p>Ядро И-2 (trailing): {@code cancel-advance-algos} выведен из офдока
 * (changelog 2025-04-24), но метод клиента существует — ветвь cancel идёт по
 * семье advance. Шаг cancel логирует находку C3, если реджектит (возможный
 * делистинг). Verify.end trailing идёт через общую модель sweep+halt
 * ({@code assertRestoredOrHalt}): невозврат advance-algo → находка C3
 * (логируется) + принудительный cancel-advance; не вычистило → halt прогона
 * (невычищаемое грязное состояние demo не замалчивается).
 */
@Order(19)
class M19PlaceAlgoOrderLiveTest extends OkxSourceApiLiveTestBase {

    @Test
    @Order(10)
    @DisplayName("M19.cond-sl — conditional STOP_LOSS lifecycle")
    void m19CondSl_chain() {
        assertAlgoPendingClean("conditional", "M19cond.snapshot");

        String clId = newId("cond");
        String slPx = tickPrice(0.5);
        Map<String, Object> body = map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "conditional", "sz", MIN_SZ, "reduceOnly", true,
                "algoClOrdId", clId, "slTriggerPx", slPx, "slTriggerPxType", "mark", "slOrdPx", "-1");
        AlgoPlacement placement = null;
        try {
            placement = placeReduceOnlyAlgo("M19cond.place", body);
            final String algoId = placement.algoId();

            RawResponse live = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoId", algoId), SIGNED);
            assertOk(live);
            assertThat(live.d0().path("algoId").asText()).isEqualTo(algoId);
            assertThat(live.d0().path("state").asText()).isNotEqualTo("canceled");

            RawResponse byClId = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoClOrdId", clId), SIGNED);
            assertOk(byClId);
            assertThat(byClId.d0().path("algoId").asText()).isEqualTo(algoId);

            RawResponse pending = get(ALGO_PENDING_PATH, map("instId", INST_ID, "ordType", "conditional"), SIGNED);
            assertThat(containsField(pending, "algoId", algoId)).as("M19cond.pending live algo").isTrue();

            RawResponse cancel = post(CANCEL_ALGOS_PATH, List.of(map("instId", INST_ID, "algoId", algoId)), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);

            waitUntil("M19cond.canceled", () -> algoCanceledOrGone(algoId));

            // orders-algo-history требует state|algoId (RUN-факт: иначе 50015).
            // Алго отменён выше → ищем в state=canceled.
            RawResponse history = get(ALGO_HISTORY_PATH,
                    map("instId", INST_ID, "ordType", "conditional", "state", "canceled"), SIGNED);
            assertOk(history);
            observeMissingInHistory("M19cond.history", history, algoId);
        } finally {
            teardownAlgo(placement);
        }

        assertAlgoFlat("conditional", placement, "M19cond.verify");
    }

    @Test
    @Order(20)
    @DisplayName("M19.cond-tp — conditional TAKE_PROFIT lifecycle")
    void m19CondTp_chain() {
        assertAlgoPendingClean("conditional", "M19tp.snapshot");

        String clId = newId("tp");
        String tpPx = tickPrice(2.0);
        Map<String, Object> body = map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "conditional", "sz", MIN_SZ, "reduceOnly", true,
                "algoClOrdId", clId, "tpTriggerPx", tpPx, "tpTriggerPxType", "mark", "tpOrdPx", "-1");
        AlgoPlacement placement = null;
        try {
            placement = placeReduceOnlyAlgo("M19tp.place", body);
            final String algoId = placement.algoId();

            RawResponse live = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoId", algoId), SIGNED);
            assertOk(live);
            assertThat(live.d0().path("state").asText()).isNotEqualTo("canceled");

            RawResponse cancel = post(CANCEL_ALGOS_PATH, List.of(map("instId", INST_ID, "algoId", algoId)), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);
            waitUntil("M19tp.canceled", () -> algoCanceledOrGone(algoId));
        } finally {
            teardownAlgo(placement);
        }

        assertAlgoFlat("conditional", placement, "M19tp.verify");
    }

    @Test
    @Order(30)
    @DisplayName("M19.oco — oco (обе ноги) lifecycle")
    void m19Oco_chain() {
        assertAlgoPendingClean("oco", "M19oco.snapshot");

        String clId = newId("oco");
        String slPx = tickPrice(0.5);
        String tpPx = tickPrice(2.0);
        Map<String, Object> body = map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "oco", "sz", MIN_SZ, "reduceOnly", true, "algoClOrdId", clId,
                "slTriggerPx", slPx, "slTriggerPxType", "mark", "slOrdPx", "-1",
                "tpTriggerPx", tpPx, "tpTriggerPxType", "mark", "tpOrdPx", "-1");
        AlgoPlacement placement = null;
        try {
            placement = placeReduceOnlyAlgo("M19oco.place", body);
            final String algoId = placement.algoId();

            RawResponse live = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoId", algoId), SIGNED);
            assertOk(live);
            assertThat(live.d0().path("state").asText()).isNotEqualTo("canceled");

            RawResponse cancel = post(CANCEL_ALGOS_PATH, List.of(map("instId", INST_ID, "algoId", algoId)), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);
            waitUntil("M19oco.canceled", () -> algoCanceledOrGone(algoId));
        } finally {
            teardownAlgo(placement);
        }

        assertAlgoFlat("oco", placement, "M19oco.verify");
    }

    @Test
    @Order(40)
    @DisplayName("M19.trailing — move_order_stop callbackRatio (ядро И-2)")
    void m19Trailing_chain() {
        assertAlgoPendingClean("move_order_stop", "M19tr.snapshot");

        String clId = newId("tr");
        Map<String, Object> body = map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "move_order_stop", "sz", MIN_SZ, "reduceOnly", true,
                "algoClOrdId", clId, "callbackRatio", "0.05");

        runTrailingChain("M19tr", body);
    }

    @Test
    @Order(50)
    @DisplayName("M19.trailing-spread — move_order_stop callbackSpread (абсолютный)")
    void m19TrailingSpread_chain() {
        assertAlgoPendingClean("move_order_stop", "M19trs.snapshot");

        String clId = newId("trs");
        String spread = tickPrice(0.01);
        Map<String, Object> body = map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "move_order_stop", "sz", MIN_SZ, "reduceOnly", true,
                "algoClOrdId", clId, "callbackSpread", spread);

        runTrailingChain("M19trs", body);
    }

    @Test
    @Order(60)
    @DisplayName("M19.neg.ordType — битый сырой ordType (вне домена)")
    void m19Neg_ordType() {
        RawResponse r = post(ORDER_ALGO_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "BOGUS", "sz", MIN_SZ, "reduceOnly", true, "algoClOrdId", newId("ng"),
                "slTriggerPx", tickPrice(0.5), "slTriggerPxType", "mark", "slOrdPx", "-1"), SIGNED);

        assertRejectedAnyLevel("M19.neg.ordType", r);
    }

    @Test
    @Order(70)
    @DisplayName("M19.neg.size — отрицательный размер (вне домена)")
    void m19Neg_size() {
        RawResponse r = post(ORDER_ALGO_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "conditional", "sz", "-1", "reduceOnly", true, "algoClOrdId", newId("ng"),
                "slTriggerPx", tickPrice(0.5), "slTriggerPxType", "mark", "slOrdPx", "-1"), SIGNED);

        assertRejectedAnyLevel("M19.neg.size", r);
    }

    @Test
    @Order(80)
    @DisplayName("M19.neg.reqParam — пропуск обязательного sz (OKX-слой)")
    void m19Neg_missingSize() {
        RawResponse r = post(ORDER_ALGO_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "conditional", "reduceOnly", true, "algoClOrdId", newId("ng"),
                "slTriggerPx", tickPrice(0.5), "slTriggerPxType", "mark", "slOrdPx", "-1"), SIGNED);

        assertRejectedAnyLevel("M19.neg.reqParam", r);
    }

    @Test
    @Order(90)
    @DisplayName("M19.neg.dupClId — дубль algoClOrdId (stateful, с teardown)")
    void m19NegDupClId_chain() {
        assertAlgoPendingClean("conditional", "M19dup.snapshot");

        String clId = newId("adup");
        String slPx = tickPrice(0.5);
        Map<String, Object> body = map(
                "instId", INST_ID, "tdMode", "isolated", "posSide", "net", "side", "sell",
                "ordType", "conditional", "sz", MIN_SZ, "reduceOnly", true,
                "algoClOrdId", clId, "slTriggerPx", slPx, "slTriggerPxType", "mark", "slOrdPx", "-1");
        AlgoPlacement placement = null;
        String algoId2 = null;
        try {
            placement = placeReduceOnlyAlgo("M19dup.place1", body);

            RawResponse place2 = post(ORDER_ALGO_PATH, body, SIGNED);
            observe("M19dup.place2", place2);
            assertThat(place2.businessReject() || place2.firstElementReject())
                    .as("duplicate algoClOrdId expected reject (observation)").isTrue();
            if (place2.firstElementOk()) {
                algoId2 = place2.d0().path("algoId").asText(); // неожиданный успех — захватить
            }
        } finally {
            teardownAlgo(placement);
            cancelAlgoQuietly(algoId2);
        }

        assertAlgoFlat("conditional", placement, "M19dup.verify");
    }

    // ---------------------------------------------------------------------
    // Внутренние помощники цепочек
    // ---------------------------------------------------------------------

    /** Trailing-цепочка (И-2): place → get → pending → cancel-advance (мягко) → teardown → verify (мягко). */
    private void runTrailingChain(String tag, Map<String, Object> body) {
        AlgoPlacement placement = null;
        try {
            placement = placeReduceOnlyAlgo(tag + ".place", body);
            final String algoId = placement.algoId();

            RawResponse live = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoId", algoId), SIGNED);
            assertOk(live);
            assertThat(live.d0().path("state").asText()).isNotEqualTo("canceled");

            RawResponse pending = get(ALGO_PENDING_PATH, map("instId", INST_ID, "ordType", "move_order_stop"), SIGNED);
            assertThat(containsField(pending, "algoId", algoId)).as(tag + ".pending advance algo").isTrue();

            // cancel-advance (И-2 гипотеза) — мягко: фейл = находка C3, не валит кейс.
            RawResponse cancel = post(CANCEL_ADVANCE_ALGOS_PATH,
                    List.of(map("instId", INST_ID, "algoId", algoId)), SIGNED);
            observe(tag + ".cancel(advance, И-2)", cancel);
            if (!(cancel.codeZero() && cancel.firstElementOk())) {
                log.warn("[{}] И-2 finding (C3): cancel-advance-algos rejected — possible delisting on demo "
                        + "(code={}, msg={}, sCode={})", tag, cancel.code(), cancel.msg(), cancel.sCode());
            }
        } finally {
            if (placement != null) {
                cancelAdvanceAlgoQuietly(placement.algoId());
                if (placement.a0()) {
                    closePositionQuietly();
                }
            }
        }

        // Verify.end — sweep+halt (И-2): невозврат advance-algo = находка C3 (логируется
        // в assertRestoredOrHalt) + принудительный cancel-advance; если не вычистило
        // (возможный делистинг) → halt прогона, чтобы demo не остался грязным.
        final String captured = placement == null ? null : placement.algoId();
        final boolean a0 = placement != null && placement.a0();
        assertRestoredOrHalt(tag + ".verify",
                "advance algo (И-2, algoId=" + captured + ")" + (a0 ? " + A0 position" : ""),
                () -> algoPendingHasNo("move_order_stop", captured) && (!a0 || !hasOpenPosition()),
                () -> {
                    cancelAdvanceAlgoQuietly(captured);
                    if (a0) {
                        closePositionQuietly();
                    }
                });
    }

    /** Размещение reduce-only algo с A0-фолбэком (открыть min-позицию при реджекте «нет позиции»). */
    private AlgoPlacement placeReduceOnlyAlgo(String caseId, Map<String, Object> body) {
        RawResponse place = post(ORDER_ALGO_PATH, body, SIGNED);
        boolean a0 = false;
        if (!(place.codeZero() && place.firstElementOk())) {
            observe(caseId + " (retry with A0 position)", place);
            openMinMarketPosition();
            a0 = true;
            place = post(ORDER_ALGO_PATH, body, SIGNED);
        }
        assertOk(place);
        assertFirstElementOk(place);
        String algoId = place.d0().path("algoId").asText();
        assertThat(algoId).as(caseId + " algoId").isNotBlank();
        return new AlgoPlacement(algoId, a0);
    }

    private void assertAlgoPendingClean(String ordType, String caseId) {
        RawResponse snapshot = get(ALGO_PENDING_PATH, map("instId", INST_ID, "ordType", ordType), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("%s: clean start", caseId).isTrue();
    }

    private boolean algoCanceledOrGone(String algoId) {
        RawResponse g = get(ORDER_ALGO_PATH, map("instId", INST_ID, "algoId", algoId), SIGNED);
        return g.codeZero() && ("canceled".equals(g.d0().path("state").asText()) || g.dataEmpty());
    }

    private void observeMissingInHistory(String caseId, RawResponse history, String algoId) {
        if (!containsField(history, "algoId", algoId)) {
            log.info("[{}] algoId {} not yet in 3m algo-history — observation", caseId, algoId);
        }
    }

    private void teardownAlgo(AlgoPlacement placement) {
        if (placement == null) {
            return;
        }
        cancelAlgoQuietly(placement.algoId());
        if (placement.a0()) {
            closePositionQuietly();
        }
    }

    private void assertAlgoFlat(String ordType, AlgoPlacement placement, String caseId) {
        final String captured = placement == null ? null : placement.algoId();
        final boolean a0 = placement != null && placement.a0();
        assertRestoredOrHalt(caseId,
                "live " + ordType + " algo (algoId=" + captured + ")" + (a0 ? " + A0 position" : ""),
                () -> algoPendingHasNo(ordType, captured) && (!a0 || !hasOpenPosition()),
                () -> {
                    cancelAlgoQuietly(captured);
                    if (a0) {
                        closePositionQuietly();
                    }
                });
    }

    /** Результат размещения algo: id + признак, что под него открывалась A0-позиция. */
    private record AlgoPlacement(String algoId, boolean a0) {
    }
}
