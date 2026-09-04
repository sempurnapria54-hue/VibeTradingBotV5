package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M17. cancelOrder — {@code POST /api/v5/trade/cancel-order} (Cancel order),
 * {@code signed:true}. Прямой by {@code ordId} покрыт цепочкой Climit
 * (M16, не дублируется). Здесь: no-state негативы (M17.1/M17.2),
 * состояние-конфликт «отмена отменённого» (M17.3) и вариант by {@code clOrdId}
 * самостоятельной мини-цепочкой (M17.4) — обе с teardown + Verify.end.
 *
 * <p>Отклонение от плана: M17.3 в плане ссылается на состояние Climit
 * (M16). Код-тесты самодостаточны (каждая цепочка несёт свой
 * snapshot/teardown), поэтому M17.3 реализован как самостоятельная
 * последовательность place → cancel → re-cancel.
 */
@Order(17)
class M17CancelOrderLiveTest extends OkxSourceApiLiveTestBase {

    /**
     * Потолок риска цепочки M17.5 — <b>фикстурная конфигурация</b>, поднятая до
     * величины, сопоставимой с объёмом лучшего уровня книги demo (решение
     * держателя 2026-08-30). Прежние 200 USDT давали 0.81 контракта — меньше
     * любого наблюдённого верхнего уровня, и всякая заявка под потолком
     * наливалась целиком; частичный налив был недостижим по счёту, а не по
     * гонке.
     *
     * <p><b>Это не продуктовая политика риска.</b> Продуктовые потолки живут в
     * {@code docs/rules/risk-policy.md} и считаются от базы риска; константа
     * ниже — размер фикстуры контура тестов источника, существует только в
     * {@code src/test} и ни одним продовым путём не читается.
     *
     * <p><b>Чем ограничена величина.</b> 2500 USDT по {@code last} ≈ 10
     * контрактов ETH-USDT-SWAP — вдвое ниже {@code maxBuy} demo-счёта при
     * плече 3 и около шестой части свободного баланса, то есть заявка
     * исполнима даже при полном наливе, который цепочка считает вырождением.
     */
    private static final BigDecimal FIXTURE_RISK_CEILING_USDT = new BigDecimal("2500");
    /** Шаг размера ETH-USDT-SWAP (spec: lotSz=minSz=0.01). */
    private static final BigDecimal LOT_SZ = new BigDecimal("0.01");
    /** Размер контракта ETH-USDT-SWAP в базовой валюте (spec: ctVal=0.1 ETH). */
    private static final BigDecimal CT_VAL = new BigDecimal("0.1");
    private static final String BOOKS_PATH = "/api/v5/market/books";
    private static final int MAX_FILL_ATTEMPTS = 5;
    private static final BigDecimal TWO = new BigDecimal("2");

    @Test
    @Order(10)
    @DisplayName("M17.1 негатив (no-state) — cancel несуществующего ordId")
    void m17_1_cancelNonexistent() {
        RawResponse r = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", "9999999999999999"), SIGNED);

        assertRejectedAnyLevel("M17.1", r);
    }

    @Test
    @Order(20)
    @DisplayName("M17.2 негатив — пропуск обязательного instId (OKX-слой)")
    void m17_2_missingInstId() {
        RawResponse r = post(CANCEL_ORDER_PATH, map("ordId", "9999999999999999"), SIGNED);

        assertRejectedAnyLevel("M17.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("M17.3 негатив — отмена отменённого (состояние-конфликт)")
    void m17_3_cancelOfCanceled() {
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("M17.3.snapshot: clean start").isTrue();

        String clOrdId = newId("m17c");
        String px = tickPrice(0.5);
        String ordId = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            final String captured = ordId;

            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);
            waitUntil("M17.3 order canceled", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });

            // Повторная отмена уже отменённого — реджект (already canceled / not exist).
            RawResponse recancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertRejectedAnyLevel("M17.3", recancel);
        } finally {
            cancelOrderQuietly(ordId);
        }

        final String captured = ordId;
        assertRestoredOrHalt("M17.3.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }

    @Test
    @Order(40)
    @DisplayName("M17.4 прямой + вариант clOrdId — cancel by clOrdId (мини-цепочка)")
    void m17_4_cancelByClOrdId() {
        RawResponse snapshot = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        assertOk(snapshot);
        assertThat(snapshot.dataEmpty()).as("M17.4.snapshot: clean start").isTrue();

        String clOrdId = newId("m17");
        String px = tickPrice(0.5);
        String ordId = null;
        try {
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                    "ordType", "limit", "sz", MIN_SZ, "px", px,
                    "clOrdId", clOrdId, "reduceOnly", false), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            assertThat(ordId).as("M17.4.place ordId").isNotBlank();

            // cancel by clOrdId (без ordId).
            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "clOrdId", clOrdId), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);

            // canceled — финал через поллинг по clOrdId.
            waitUntil("M17.4 canceled by clOrdId", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "clOrdId", clOrdId), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });
        } finally {
            cancelOrderQuietly(ordId);
        }

        final String captured = ordId;
        assertRestoredOrHalt("M17.4.verify", "live orders (ordId=" + captured + ")",
                () -> pendingHasNo(captured), () -> cancelOrderQuietly(captured));
    }

    @Test
    @Order(50)
    @DisplayName("M17.5 Содержательный (шаг 7) — судьба встроенной защиты при отмене родителя")
    void m17_5_attachedProtectionOnParentCancel() {
        boolean baseObserved = c17a_parentWithoutFill();
        boolean b = c17b_parentWithPartialFill();
        boolean c = c17c_terminalWithoutOurCancel();

        if (!b && !c) {
            observeValue("M17.5", "п. 17 судьба встроенной защиты при непустом наливе",
                    "НЕ НАБЛЮДЁН: частичный налив не собран за " + MAX_FILL_ATTEMPTS
                            + " попыток даже под поднятым фикстурным потолком "
                            + FIXTURE_RISK_CEILING_USDT + " USDT. Слот остаётся PENDING: "
                            + "«фикстуру собрать не удалось» гейт не закрывает");
        }
        assertThat(baseObserved)
                .as("M17.5 → C17a: представление встроенной защиты у родителя наблюдено")
                .isTrue();
    }

    /**
     * C17a — родитель без налива (детерминированная база). Отвечает на (1)
     * представление встроенной защиты у живого родителя и даёт базу сравнения
     * для C17b. Возвращает {@code true}, если наблюдение состоялось.
     */
    private boolean c17a_parentWithoutFill() {
        step("C17a.snapshot");
        assertThat(noPendingOrders()).as("C17a.snapshot: чистый старт").isTrue();

        BigDecimal last = lastPrice();
        String parentPx = tickPrice(last, 0.5);
        String slTriggerPx = tickPrice(last, 0.4);
        String attachClOrdId = newId("a17");
        String ordId = null;
        try {
            step("C17a.place");
            RawResponse place = post(ORDER_PATH, map(
                    "instId", INST_ID, "tdMode", "isolated", "side", "buy", "ordType", "limit",
                    "sz", MIN_SZ, "px", parentPx, "clOrdId", newId("c17a"), "reduceOnly", false,
                    "attachAlgoOrds", List.of(map(
                            "attachAlgoClOrdId", attachClOrdId, "slTriggerPx", slTriggerPx,
                            "slOrdPx", "-1", "slTriggerPxType", "last"))), SIGNED);
            assertOk(place);
            assertFirstElementOk(place);
            ordId = place.d0().path("ordId").asText();
            assertThat(ordId).as("C17a.place ordId").isNotBlank();
            final String captured = ordId;

            // (1) Представление встроенной защиты у ЖИВОГО родителя — факт, не ожидание.
            step("C17a.get");
            RawResponse live = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertOk(live);
            assertThat(live.d0().path("state").asText()).isEqualTo("live");
            assertThat(live.d0().path("accFillSz").asText()).isEqualTo("0");
            JsonNode attachedLive = live.d0().path("attachAlgoOrds").path(0);
            observeValue("M17.5", "C17a.attachAlgoOrds[0] у живого родителя", attachedLive);
            assertThat(attachedLive.isMissingNode())
                    .as("C17a → встроенная защита представлена в details родителя").isFalse();
            assertThat(attachedLive.path("attachAlgoClOrdId").asText()).isEqualTo(attachClOrdId);
            observeValue("M17.5", "C17a.attachAlgoId", attachedLive.path("attachAlgoId").asText());

            // Виден ли attached до филла самостоятельной algo-записью.
            step("C17a.algoPending");
            RawResponse algoPending = get(ALGO_PENDING_PATH,
                    map("instType", INST_TYPE, "instId", INST_ID, "ordType", "conditional"), SIGNED);
            assertOk(algoPending);
            observeValue("M17.5", "C17a.algoPending до отмены",
                    "n=" + algoPending.dataSize() + " содержит attachAlgoClOrdId="
                            + containsField(algoPending, "algoClOrdId", attachClOrdId));

            step("C17a.cancel");
            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);
            waitUntil("C17a родитель отменён", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });

            step("C17a.canceled");
            RawResponse canceled = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
            observeValue("M17.5", "C17a.attachAlgoOrds[0] у отменённого родителя",
                    canceled.d0().path("attachAlgoOrds").path(0));
            observeValue("M17.5", "C17a.cancelSource",
                    canceled.d0().path("cancelSource").asText() + "/" + canceled.d0().path("cancelSourceReason").asText());

            step("C17a.algoPendingAfter");
            RawResponse after = get(ALGO_PENDING_PATH,
                    map("instType", INST_TYPE, "instId", INST_ID, "ordType", "conditional"), SIGNED);
            assertOk(after);
            assertThat(containsField(after, "algoClOrdId", attachClOrdId))
                    .as("C17a → защита ненали́того родителя живой не остаётся").isFalse();
            return true;
        } finally {
            cancelOrderQuietly(ordId);
        }
    }

    /**
     * C17b — родитель с непустым наливом (несущая цепочка): отмена <b>нами</b>
     * частично налитого родителя со встроенной защитой. До
     * {@link #MAX_FILL_ATTEMPTS} попыток; вырождение (полный либо нулевой
     * налив) сворачивается teardown'ом. Возвращает {@code true}, только если
     * судьба защиты после отмены налитого родителя наблюдена.
     */
    private boolean c17b_parentWithPartialFill() {
        for (int attempt = 1; attempt <= MAX_FILL_ATTEMPTS; attempt++) {
            step("C17b.attempt" + attempt);
            assertThat(hasOpenPosition()).as("C17b.snapshot: позиции нет").isFalse();

            BigDecimal last = lastPrice();
            BigDecimal ceilingContracts = ceilingContracts(last);
            Level level = bestAsk("C17b.attempt" + attempt, ceilingContracts);
            if (level == null) {
                continue;
            }

            // Размер — весь фикстурный потолок: остаток после налива верхнего
            // уровня заведомо не меньше половины потолка, поэтому вырождение в
            // `filled` требует, чтобы книга поглотила его целиком, а не лот.
            String size = ceilingContracts.toPlainString();
            String attachClOrdId = newId("b17");
            String ordId = null;
            try {
                step("C17b.place");
                RawResponse place = post(ORDER_PATH, map(
                        "instId", INST_ID, "tdMode", "isolated", "side", "buy", "ordType", "limit",
                        "sz", size, "px", level.px(), "clOrdId", newId("c17b"), "reduceOnly", false,
                        "attachAlgoOrds", List.of(map("attachAlgoClOrdId", attachClOrdId,
                                "slTriggerPx", tickPrice(last, 0.5), "slOrdPx", "-1",
                                "slTriggerPxType", "last"))), SIGNED);
                assertOk(place);
                assertFirstElementOk(place);
                ordId = place.d0().path("ordId").asText();
                final String captured = ordId;

                step("C17b.get");
                pollUntilBool(() -> {
                    RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                    return g.codeZero() && !"live".equals(g.d0().path("state").asText());
                }, pollTimeout);
                RawResponse state = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                String parentState = state.d0().path("state").asText();
                String accFillSz = state.d0().path("accFillSz").asText();
                observeValue("M17.5", "C17b.attempt" + attempt + ".state",
                        parentState + " accFillSz=" + accFillSz + " из sz=" + size);
                if (!"partially_filled".equals(parentState)) {
                    continue;
                }
                observeValue("M17.5", "C17b.partially_filled родитель", state.d0());

                if (observeProtectionOnTerminal("C17b", captured, attachClOrdId, accFillSz, true)) {
                    return true;
                }
            } finally {
                cancelOrderQuietly(ordId);
                closePositionQuietly();
                sweepConditionalAlgos("C17b");
            }
        }
        return false;
    }

    /**
     * C17c — терминал при непустом наливе <b>без нашей отмены</b>
     * ({@code ordType=ioc}: остаток снимает биржа). Дублёр C17b с другим
     * инициатором терминала; расхождение исходов — самостоятельный факт.
     */
    private boolean c17c_terminalWithoutOurCancel() {
        for (int attempt = 1; attempt <= MAX_FILL_ATTEMPTS; attempt++) {
            step("C17c.attempt" + attempt);
            assertThat(hasOpenPosition()).as("C17c.snapshot: позиции нет").isFalse();

            BigDecimal last = lastPrice();
            BigDecimal ceilingContracts = ceilingContracts(last);
            Level level = bestAsk("C17c.attempt" + attempt, ceilingContracts);
            if (level == null) {
                continue;
            }

            String size = ceilingContracts.toPlainString();
            String attachClOrdId = newId("c17");
            String ordId = null;
            try {
                step("C17c.place");
                RawResponse place = post(ORDER_PATH, map(
                        "instId", INST_ID, "tdMode", "isolated", "side", "buy", "ordType", "ioc",
                        "sz", size, "px", level.px(), "clOrdId", newId("c17c"), "reduceOnly", false,
                        "attachAlgoOrds", List.of(map("attachAlgoClOrdId", attachClOrdId,
                                "slTriggerPx", tickPrice(last, 0.5), "slOrdPx", "-1",
                                "slTriggerPxType", "last"))), SIGNED);
                assertOk(place);
                // Реджект сочетания ioc + attachAlgoOrds — факт контракта, а не фейл.
                if (!"0".equals(place.d0().path("sCode").asText())) {
                    observeValue("M17.5", "C17c.place реджект сочетания ioc+attachAlgoOrds",
                            place.d0().path("sCode").asText() + " " + place.d0().path("sMsg").asText());
                    return false;
                }
                ordId = place.d0().path("ordId").asText();
                final String captured = ordId;

                step("C17c.get");
                pollUntilBool(() -> {
                    RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                    String st = g.d0().path("state").asText();
                    return g.codeZero() && ("canceled".equals(st) || "filled".equals(st));
                }, pollTimeout);
                RawResponse state = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                String parentState = state.d0().path("state").asText();
                String accFillSz = state.d0().path("accFillSz").asText();
                observeValue("M17.5", "C17c.attempt" + attempt + ".state",
                        parentState + " accFillSz=" + accFillSz + " из sz=" + size);
                // Несущее для C17c: терминал получен биржей, налив непустой.
                if (!"canceled".equals(parentState) || new BigDecimal(accFillSz).signum() == 0) {
                    continue;
                }
                observeValue("M17.5", "C17c.терминал биржей при непустом наливе", state.d0());

                if (observeProtectionOnTerminal("C17c", captured, attachClOrdId, accFillSz, false)) {
                    return true;
                }
            } finally {
                cancelOrderQuietly(ordId);
                closePositionQuietly();
                sweepConditionalAlgos("C17c");
            }
        }
        return false;
    }

    /**
     * Общая половина C17b/C17c — судьба встроенной защиты у терминального
     * родителя с непустым наливом. {@code ourCancel=true} — терминал наводим
     * мы ({@code cancel-order}); {@code false} — терминал уже наступил (биржа
     * сняла остаток {@code ioc}). Отвечает на несущий вопрос предусловия
     * п. 17: остаётся ли на бирже <b>живая</b> защита на налитый объём.
     */
    private boolean observeProtectionOnTerminal(String chain, String ordId, String attachClOrdId,
                                                String accFillSz, boolean ourCancel) {
        step(chain + ".position");
        RawResponse positions = get(POSITIONS_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
        assertOk(positions);
        observeValue("M17.5", chain + ".position",
                "pos=" + positions.d0().path("pos").asText() + " posId="
                        + positions.d0().path("posId").asText() + " против accFillSz=" + accFillSz);

        if (ourCancel) {
            step(chain + ".cancel");
            RawResponse cancel = post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", ordId), SIGNED);
            assertOk(cancel);
            assertFirstElementOk(cancel);
            waitUntil(chain + " частично налитый родитель отменён", () -> {
                RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", ordId), SIGNED);
                return g.codeZero() && "canceled".equals(g.d0().path("state").asText());
            });
        }

        step(chain + ".getAfter");
        RawResponse after = get(ORDER_PATH, map("instId", INST_ID, "ordId", ordId), SIGNED);
        assertOk(after);
        observeValue("M17.5", chain + ".getAfter",
                "state=" + after.d0().path("state").asText()
                        + " accFillSz=" + after.d0().path("accFillSz").asText()
                        + " cancelSource=" + after.d0().path("cancelSource").asText()
                        + "/" + after.d0().path("cancelSourceReason").asText());
        observeValue("M17.5", chain + ".getAfter.attachAlgoOrds[0]", after.d0().path("attachAlgoOrds").path(0));

        step(chain + ".algoPending");
        RawResponse algoPending = get(ALGO_PENDING_PATH,
                map("instType", INST_TYPE, "instId", INST_ID, "ordType", "conditional"), SIGNED);
        assertOk(algoPending);
        boolean aliveByClient = containsField(algoPending, "algoClOrdId", attachClOrdId)
                || containsField(algoPending, "attachAlgoClOrdId", attachClOrdId);
        observeContent("M17.5." + chain + ".algoPending", algoPending);
        observeValue("M17.5", chain + ".НЕСУЩЕЕ: живая защита после терминала родителя",
                (aliveByClient ? "ЕСТЬ" : "НЕТ") + " (algoClOrdId=" + attachClOrdId
                        + ", n=" + algoPending.dataSize() + ", накопленный налив=" + accFillSz + ")");

        step(chain + ".algoHistory");
        RawResponse algoHistory = get(ALGO_HISTORY_PATH, map("instType", INST_TYPE, "instId", INST_ID,
                "ordType", "conditional", "state", "canceled"), SIGNED);
        observeValue("M17.5", chain + ".algoHistory(canceled) содержит защиту",
                containsField(algoHistory, "algoClOrdId", attachClOrdId)
                        + " n=" + algoHistory.dataSize());
        observeContent("M17.5." + chain + ".algoHistory", algoHistory);
        return true;
    }

    /**
     * Потолок фикстуры в контрактах: {@link #FIXTURE_RISK_CEILING_USDT} по
     * {@code last}, округлённый <b>вниз</b> до {@link #LOT_SZ}.
     */
    private BigDecimal ceilingContracts(BigDecimal last) {
        return FIXTURE_RISK_CEILING_USDT.divide(CT_VAL.multiply(last), 8, RoundingMode.DOWN)
                .divide(LOT_SZ, 0, RoundingMode.DOWN).multiply(LOT_SZ);
    }

    /**
     * Верхний уровень книги, пригодный для частичного налива: объём лучшего
     * аска обязан быть не больше половины потолка — иначе заявка размером в
     * потолок налилась бы целиком, и попытка пропускается с перечитыванием
     * книги. {@code null} — уровень непригоден.
     */
    private Level bestAsk(String label, BigDecimal ceilingContracts) {
        RawResponse books = get(BOOKS_PATH, map("instId", INST_ID, "sz", "5"), PUBLIC);
        assertOk(books);
        String px = books.d0().path("asks").path(0).path(0).asText("");
        BigDecimal sz = new BigDecimal(books.d0().path("asks").path(0).path(1).asText("0"));
        observeValue("M17.5", label + ".book",
                "asks[0]=" + px + " × " + sz + ", потолок=" + ceilingContracts + " контрактов");
        if (sz.signum() == 0 || px.isBlank()) {
            return null;
        }
        if (sz.compareTo(ceilingContracts.divide(TWO, 8, RoundingMode.DOWN)) > 0) {
            observeValue("M17.5", label,
                    "пропущена: объём лучшего аска (" + sz + ") больше половины потолка фикстуры");
            return null;
        }
        return new Level(px, sz);
    }

    /** Снять живые conditional-algo по инструменту (страховка teardown цепочки). */
    private void sweepConditionalAlgos(String chain) {
        try {
            RawResponse pending = get(ALGO_PENDING_PATH,
                    map("instType", INST_TYPE, "instId", INST_ID, "ordType", "conditional"), SIGNED);
            for (JsonNode element : pending.data()) {
                cancelAlgoQuietly(element.path("algoId").asText(null));
            }
        } catch (RuntimeException | AssertionError e) {
            log.warn("{} sweepConditionalAlgos swallowed: {}", chain, e.getMessage());
        }
    }

    /** Уровень книги: цена и объём. */
    private record Level(String px, BigDecimal sz) {
    }
}
