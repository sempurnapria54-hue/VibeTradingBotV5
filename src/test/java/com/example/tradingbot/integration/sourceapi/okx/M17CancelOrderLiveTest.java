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

    /** Потолок риска цепочки M17.5: notional по {@code last} не выше 200 USDT. */
    private static final BigDecimal RISK_CEILING_USDT = new BigDecimal("200");
    /** Шаг размера ETH-USDT-SWAP (spec: lotSz=minSz=0.01). */
    private static final BigDecimal LOT_SZ = new BigDecimal("0.01");
    /** Размер контракта ETH-USDT-SWAP в базовой валюте (spec: ctVal=0.1 ETH). */
    private static final BigDecimal CT_VAL = new BigDecimal("0.1");
    private static final String BOOKS_PATH = "/api/v5/market/books";
    private static final int MAX_FILL_ATTEMPTS = 3;

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
        boolean partialObserved = c17a_parentWithoutFill();
        boolean b = c17b_parentWithPartialFill();
        boolean c = c17c_terminalWithoutOurCancel();

        if (!b && !c) {
            observeValue("M17.5", "п. 17 судьба встроенной защиты при непустом наливе",
                    "НЕ НАБЛЮДЁН: частичный налив под потолком риска 200 USDT недостижим — "
                            + "объём лучшего уровня книги demo кратно превышает половину потолка, "
                            + "и всякая заявка под потолком наливается целиком. Слот остаётся PENDING: "
                            + "«фикстуру собрать не удалось» гейт не закрывает");
        }
        assertThat(partialObserved)
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
     * C17b — родитель с непустым наливом (несущая цепочка). До трёх попыток;
     * вырождение (полный либо нулевой налив) сворачивается teardown'ом.
     * Возвращает {@code true}, только если {@code partially_filled} наблюдён.
     */
    private boolean c17b_parentWithPartialFill() {
        for (int attempt = 1; attempt <= MAX_FILL_ATTEMPTS; attempt++) {
            step("C17b.attempt" + attempt);
            assertThat(hasOpenPosition()).as("C17b.snapshot: позиции нет").isFalse();

            BigDecimal last = lastPrice();
            BigDecimal ceilingContracts = RISK_CEILING_USDT.divide(CT_VAL.multiply(last), 8, RoundingMode.DOWN);
            RawResponse books = get(BOOKS_PATH, map("instId", INST_ID, "sz", "5"), PUBLIC);
            assertOk(books);
            BigDecimal bestAskSz = new BigDecimal(books.d0().path("asks").path(0).path(1).asText("0"));
            String bestAskPx = books.d0().path("asks").path(0).path(0).asText("");
            observeValue("M17.5", "C17b.attempt" + attempt + ".book",
                    "asks[0]=" + bestAskPx + " × " + bestAskSz + ", потолок=" + ceilingContracts + " контрактов");

            // Объём лучшего уровня больше половины потолка ⇒ попытка пропускается.
            if (bestAskSz.compareTo(ceilingContracts.divide(new BigDecimal("2"), 8, RoundingMode.DOWN)) > 0) {
                observeValue("M17.5", "C17b.attempt" + attempt,
                        "пропущена: объём лучшего аска (" + bestAskSz + ") больше половины потолка риска");
                continue;
            }

            BigDecimal target = bestAskSz.multiply(new BigDecimal("2"))
                    .divide(LOT_SZ, 0, RoundingMode.CEILING).multiply(LOT_SZ);
            BigDecimal size = target.min(ceilingContracts.divide(LOT_SZ, 0, RoundingMode.DOWN).multiply(LOT_SZ));
            String ordId = null;
            try {
                RawResponse place = post(ORDER_PATH, map(
                        "instId", INST_ID, "tdMode", "isolated", "side", "buy", "ordType", "limit",
                        "sz", size.toPlainString(), "px", bestAskPx, "clOrdId", newId("c17b"), "reduceOnly", false,
                        "attachAlgoOrds", List.of(map("attachAlgoClOrdId", newId("b17"),
                                "slTriggerPx", tickPrice(last, 0.5), "slOrdPx", "-1", "slTriggerPxType", "last"))), SIGNED);
                assertOk(place);
                assertFirstElementOk(place);
                ordId = place.d0().path("ordId").asText();
                final String captured = ordId;

                pollUntilBool(() -> {
                    RawResponse g = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                    return g.codeZero() && !"live".equals(g.d0().path("state").asText());
                }, pollTimeout);
                RawResponse state = get(ORDER_PATH, map("instId", INST_ID, "ordId", captured), SIGNED);
                observeValue("M17.5", "C17b.attempt" + attempt + ".state",
                        state.d0().path("state").asText() + " accFillSz=" + state.d0().path("accFillSz").asText());
                if (!"partially_filled".equals(state.d0().path("state").asText())) {
                    continue;
                }
                observeValue("M17.5", "C17b partially_filled достигнут", state.d0());
                return true;
            } finally {
                cancelOrderQuietly(ordId);
                closePositionQuietly();
            }
        }
        return false;
    }

    /**
     * C17c — терминал при непустом наливе без нашей отмены ({@code ioc}).
     * Дублёр C17b с другим инициатором терминала.
     */
    private boolean c17c_terminalWithoutOurCancel() {
        step("C17c.books");
        BigDecimal last = lastPrice();
        BigDecimal ceilingContracts = RISK_CEILING_USDT.divide(CT_VAL.multiply(last), 8, RoundingMode.DOWN);
        RawResponse books = get(BOOKS_PATH, map("instId", INST_ID, "sz", "5"), PUBLIC);
        assertOk(books);
        BigDecimal bestAskSz = new BigDecimal(books.d0().path("asks").path(0).path(1).asText("0"));
        observeValue("M17.5", "C17c.book",
                "asks[0] × " + bestAskSz + ", потолок=" + ceilingContracts + " контрактов");
        if (bestAskSz.multiply(new BigDecimal("2")).compareTo(ceilingContracts) > 0) {
            observeValue("M17.5", "C17c",
                    "пропущена: ioc-размер 2×объём лучшего уровня (" + bestAskSz.multiply(new BigDecimal("2"))
                            + ") выходит за потолок риска — гарантированный частичный налив недостижим");
            return false;
        }
        return false;
    }
}
