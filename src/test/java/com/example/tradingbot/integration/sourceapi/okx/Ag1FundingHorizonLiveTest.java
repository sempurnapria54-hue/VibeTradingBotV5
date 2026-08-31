package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Предусловие п. 7 реестра (`.claude/tests/source-api/okx/code-preconditions.md`)
 * — горизонт слагаемого {@code fundingFee} в записи закрытия
 * {@code positions-history}. Слагаемое наблюдаемо только у позиции,
 * <b>пережившей расчёт фандинга</b>; фикстура {@link Ag1DealFixtureLiveTest}
 * открывает и закрывает эпизод за секунды и границу интервала не пересекает,
 * поэтому наблюдение вынесено в отдельный кейс со своей фикстурой.
 *
 * <p><b>Почему свой инструмент.</b> Интервал расчёта фандинга у источника
 * <b>не универсален</b>: на demo из 142 живых USDT-свопов 89 считают фандинг
 * каждые <b>четыре</b> часа (00/04/08/12/16/20 UTC) и 53 — каждые восемь
 * (00/08/16 UTC). {@code INST_ID} общей базы ({@code ETH-USDT-SWAP}) —
 * восьмичасовой, и ближайшая его граница отстоит до восьми часов. Инструмент
 * этого кейса выбран четырёхчасовым, чтобы окно наблюдения было достижимо
 * внутри прогона. Величина интервала не постулируется — она читается у
 * источника ({@code fundingTime} и {@code nextFundingTime} эндпоинта
 * {@code /public/funding-rate}) и логируется наблюдением.
 *
 * <p><b>Цепочка.</b> Ожидание до {@code fundingTime − OPEN_LEAD} → открытие
 * позиции целевым нотиналом → удержание через границу (позиция проверяется
 * живой на каждом шаге удержания) → частичное закрытие → полное закрытие →
 * flat. Затем: запись закрытия эпизода из {@code positions-history} против
 * движений счёта типа 8 ({@code Funding fee}) в том же окне.
 *
 * <p><b>Кейс сам себя не запускает вне окна.</b> Если до ближайшей границы
 * дальше {@link #MAX_LEAD} либо ближе {@link #MIN_LEAD}, кейс
 * <b>пропускается</b> (assumption), а не падает: наблюдение требует
 * попадания в окно, и его отсутствие — не дефект источника.
 */
@Order(61)
class Ag1FundingHorizonLiveTest extends OkxSourceApiLiveTestBase {

    private static final String FUNDING_RATE_PATH = "/api/v5/public/funding-rate";
    private static final String INSTRUMENTS_PATH = "/api/v5/public/instruments";
    private static final String POSITIONS_HISTORY_PATH = "/api/v5/account/positions-history";
    private static final String BILLS_PATH = "/api/v5/account/bills";
    /** Тип bill'а funding-расчёта (справочник AG6.1: 8 — Funding fee). */
    private static final String BILL_TYPE_FUNDING = "8";

    /**
     * Инструмент фикстуры: четырёхчасовой интервал расчёта фандинга. Живёт
     * только в {@code src/test} и продуктовыми путями не читается —
     * фикстурная величина, как {@code M17CancelOrderLiveTest.FIXTURE_RISK_CEILING_USDT}.
     */
    private static final String FUNDING_INST_ID = "ACT-USDT-SWAP";
    /**
     * Целевой нотинал позиции, USDT. Ставка фандинга — доли процента, поэтому
     * на минимальном лоте расчёт округлился бы до нуля и наблюдение было бы
     * неотличимо от «фандинга не было». Нотинал взят таким, чтобы при
     * наблюдаемых ставках расчёт был счётно ненулевым.
     */
    private static final BigDecimal TARGET_NOTIONAL_USDT = new BigDecimal("500");
    /** За сколько до границы открывается позиция. */
    private static final Duration OPEN_LEAD = Duration.ofMinutes(5);
    /** Сколько позиция держится после границы, прежде чем закрываться. */
    private static final Duration HOLD_AFTER = Duration.ofMinutes(2);
    /** Ближе этого к границе прогон стартовать не успевает — кейс пропускается. */
    private static final Duration MIN_LEAD = Duration.ofMinutes(6);
    /** Дальше этого ждать внутри прогона бессмысленно — кейс пропускается. */
    private static final Duration MAX_LEAD = Duration.ofMinutes(50);
    /** Шаг проверки живости позиции во время удержания. */
    private static final Duration HOLD_PROBE_STEP = Duration.ofSeconds(20);

    /** Нижняя граница окна наблюдения (мс). */
    private static long windowStart;
    /** Верхняя граница окна наблюдения (мс). */
    private static long windowEnd;
    /** {@code posId} эпизода, пережившего расчёт. */
    private static String episodePosId;

    @Test
    @Order(10)
    @DisplayName("п. 7 — горизонт fundingFee у позиции, пережившей расчёт фандинга")
    void ag1_7_fundingHorizon() {
        step("AG1.7-FUND");
        pollTimeout = Duration.ofSeconds(90);
        // Ветвей исхода три, и все три обязаны оставить персистентный след:
        // окно подошло и сверка сошлась; окно не подошло (ниже, до assumeTrue);
        // окно подошло, а сверка разошлась — её пишет этот флаг в finally.
        boolean[] settled = {false};

        // (1) Границу интервала читаем у источника, а не постулируем.
        JsonNode rate = fundingRate();
        long fundingTime = Long.parseLong(rate.path("fundingTime").asText());
        long nextFundingTime = Long.parseLong(rate.path("nextFundingTime").asText("0"));
        long intervalHours = nextFundingTime > 0 ? (nextFundingTime - fundingTime) / 3_600_000L : 0L;
        long lead = fundingTime - System.currentTimeMillis();
        observeValue("AG1.7", "fundingInstId", FUNDING_INST_ID);
        observeValue("AG1.7", "fundingTime", Instant.ofEpochMilli(fundingTime) + " (через " + lead / 60_000L + " мин)");
        observeValue("AG1.7", "fundingIntervalHours", intervalHours);
        observeValue("AG1.7", "fundingRate", rate.path("fundingRate").asText(""));

        boolean withinWindow = lead >= MIN_LEAD.toMillis() && lead <= MAX_LEAD.toMillis();
        if (!withinWindow) {
            // Исход ПРОПУСКА тоже персистентен. «Кейс не гонялся» и «гонялся и
            // окно не подошло» обязаны различаться в данных: без этой записи
            // след прогона остаётся только в консоли — ровно тот дефект, из-за
            // которого два соседних слота вернулись в PENDING.
            persistObservation("AG1.7", "горизонт fundingFee: прогон был, окно не подошло", List.of(
                    "исход: OBSERVED_ABSENT — событие в окне прогона не наступило",
                    "инструмент фикстуры: " + FUNDING_INST_ID,
                    "интервал расчёта, ч: " + intervalHours,
                    "ближайшая граница: " + Instant.ofEpochMilli(fundingTime),
                    "до границы, мин: " + lead / 60_000L,
                    "окно прогона, мин: " + MIN_LEAD.toMinutes() + ".." + MAX_LEAD.toMinutes(),
                    "слот остаётся PENDING: исход «событие не наступило» закрывающей силы не имеет"));
        }
        Assumptions.assumeTrue(withinWindow,
                "до границы funding-интервала " + lead / 60_000L + " мин — вне окна прогона ["
                        + MIN_LEAD.toMinutes() + ".." + MAX_LEAD.toMinutes() + "]; кейс не исполняется");

        try {

        // (2) Snapshot.start — по инструменту фикстуры позиции нет.
        assertThat(hasFundingPosition()).as("AG1.7 → Snapshot.start: позиции по инструменту фикстуры нет").isFalse();

        // (3) Размер под целевой нотинал: считается по спецификации инструмента
        // и живому курсу, а не зашивается числом.
        String size = sizeForTargetNotional();
        observeValue("AG1.7", "plannedSize", size + " контрактов под нотинал " + TARGET_NOTIONAL_USDT + " USDT");

        // (4) Открытие за OPEN_LEAD до границы.
        sleepUntil(fundingTime - OPEN_LEAD.toMillis(), "открытие позиции");
        windowStart = System.currentTimeMillis();
        placeFundingMarket("buy", size, false);
        waitUntil("позиция открыта", this::hasFundingPosition);
        episodePosId = liveFundingPosition().path("posId").asText("");
        assertThat(episodePosId).as("AG1.7 → posId живой позиции").isNotBlank();
        observeValue("AG1.7", "episode.posId", episodePosId);
        observeValue("AG1.7", "episode.openTime", Instant.ofEpochMilli(windowStart).toString());
        observeValue("AG1.7", "episode.notionalUsd", liveFundingPosition().path("notionalUsd").asText(""));

        // (5) Удержание через границу. Живость проверяется на каждом шаге:
        // именно она — предмет наблюдения (позиция ДОЛЖНА пережить расчёт).
        holdAlive(fundingTime + HOLD_AFTER.toMillis());

        // (6) Выход: частичное закрытие, затем полное — чтобы запись эпизода
        // собиралась из нескольких закрывающих исполнений, как у общей фикстуры.
        String halfSize = halfOf(size);
        if (halfSize != null) {
            placeFundingMarket("sell", halfSize, true);
            waitUntil("позиция частично закрыта", () -> !halfSize.equals(liveFundingPosition().path("pos").asText("")));
            observeValue("AG1.7", "sizeAfterPartialClose", liveFundingPosition().path("pos").asText(""));
        }
        closeFundingPosition();
        waitUntil("позиция закрыта полностью", () -> !hasFundingPosition());
        windowEnd = System.currentTimeMillis();
        observeValue("AG1.7", "window", windowStart + ".." + windowEnd);

        // (7) ПРЕДУСЛОВИЕ п. 7 — слагаемое fundingFee в записи закрытия эпизода.
        JsonNode record = episodeRecord();
        assertThat(record).as("AG1.7 → запись закрытия эпизода найдена в positions-history").isNotNull();
        for (String field : List.of("fundingFee", "pnl", "fee", "realizedPnl", "liqPenalty")) {
            observeValue("AG1.7", "record." + field, "'" + record.path(field).asText("") + "'");
        }
        BigDecimal recordFunding = decimal(record, "fundingFee");
        observeValue("AG1.7", "record.fundingFee.signum", recordFunding.signum());
        assertThat(recordFunding.signum())
                .as("AG1.7 → п. 7: запись закрытия эпизода, пережившего расчёт, несёт НЕНУЛЕВОЙ fundingFee")
                .isNotEqualTo(0);

        // (8) Сверка с движениями счёта: Σ balChg funding-bills == Σ fundingFee записей.
        List<JsonNode> fundingBills = billsOfType(BILL_TYPE_FUNDING);
        observeValue("AG1.7", "fundingBillsInWindow", fundingBills.size());
        for (JsonNode bill : fundingBills) {
            observeValue("AG1.7", "fundingBill",
                    "ts=" + bill.path("ts").asText() + " subType=" + bill.path("subType").asText()
                            + " ccy=" + bill.path("ccy").asText() + " balChg=" + bill.path("balChg").asText()
                            + " instId=" + bill.path("instId").asText());
        }
        assertThat(fundingBills)
                .as("AG1.7 → расчёт фандинга оставил движение счёта типа 8 в окне эпизода").isNotEmpty();
        BigDecimal billsFunding = fundingBills.stream()
                .map(bill -> decimal(bill, "balChg")).reduce(BigDecimal.ZERO, BigDecimal::add);
        observeValue("AG1.7", "Σ funding bills balChg", billsFunding);
        observeValue("AG1.7", "Σ record fundingFee", recordFunding);
        assertThat(billsFunding.compareTo(recordFunding))
                .as("AG1.7 → Σ balChg funding-bills сходится с fundingFee записи закрытия (%s vs %s)",
                        billsFunding, recordFunding)
                .isEqualTo(0);

        // (8a) Исход слота — в ПЕРСИСТЕНТНЫЙ носитель, а не только в лог:
        // слот предусловия, закрытый фактом из лога, закрыт не был
        // (docs/concept.md — сослаться на лог при разборе нельзя).
        persistObservation("AG1.7", "горизонт fundingFee: запись эпизода, пережившего расчёт", List.of(
                "инструмент фикстуры: " + FUNDING_INST_ID,
                "интервал расчёта, ч: " + intervalHours,
                "граница расчёта: " + Instant.ofEpochMilli(fundingTime),
                "окно эпизода: " + windowStart + ".." + windowEnd,
                "posId эпизода: " + episodePosId,
                "record.fundingFee: '" + record.path("fundingFee").asText("") + "'",
                "record.pnl: '" + record.path("pnl").asText("") + "'",
                "record.fee: '" + record.path("fee").asText("") + "'",
                "record.realizedPnl: '" + record.path("realizedPnl").asText("") + "'",
                "record.liqPenalty: '" + record.path("liqPenalty").asText("") + "'",
                "знак fundingFee: " + recordFunding.signum(),
                "funding-bills в окне: " + fundingBills.size(),
                "Σ balChg funding-bills: " + billsFunding,
                "Σ fundingFee записи: " + recordFunding,
                "сходимость Σ balChg == Σ fundingFee: да"));

            settled[0] = true;
        } finally {
            if (!settled[0]) {
                // Третья ветвь: окно подошло, а сверка разошлась. Без этой
                // записи на диске оставался бы исход ПРОШЛОГО прогона, и
                // «сошлось» было бы неотличимо от «разошлось».
                persistObservation("AG1.7", "горизонт fundingFee: прогон был, сверка не сошлась", List.of(
                        "исход: прогон дошёл до окна, но ожидание не подтвердилось",
                        "инструмент фикстуры: " + FUNDING_INST_ID,
                        "интервал расчёта, ч: " + intervalHours,
                        "граница расчёта: " + Instant.ofEpochMilli(fundingTime),
                        "окно эпизода: " + windowStart + ".." + windowEnd,
                        "posId эпизода: " + episodePosId,
                        "слот остаётся PENDING: факт не установлен"));
            }
        }

        // (9) Verify.end — состояние вернулось к старту.
        assertRestoredOrHalt("AG1.7", "позиция по инструменту фикстуры",
                this::isFlat, this::closeFundingPositionQuietly);
    }

    // ------------------------------------------------------------------
    // Хелперы кейса
    // ------------------------------------------------------------------

    private JsonNode fundingRate() {
        RawResponse r = get(FUNDING_RATE_PATH, map("instId", FUNDING_INST_ID), PUBLIC);
        assertOk(r);
        return r.d0();
    }

    /** Размер в контрактах под {@link #TARGET_NOTIONAL_USDT}, округлённый вниз до лота. */
    private String sizeForTargetNotional() {
        RawResponse spec = get(INSTRUMENTS_PATH, map("instType", INST_TYPE, "instId", FUNDING_INST_ID), PUBLIC);
        assertOk(spec);
        BigDecimal contractValue = new BigDecimal(spec.d0().path("ctVal").asText());
        BigDecimal lot = new BigDecimal(spec.d0().path("lotSz").asText());
        BigDecimal minSize = new BigDecimal(spec.d0().path("minSz").asText());

        RawResponse ticker = get(TICKER_PATH, map("instId", FUNDING_INST_ID), PUBLIC);
        assertOk(ticker);
        BigDecimal price = new BigDecimal(ticker.d0().path("last").asText());

        BigDecimal perContract = contractValue.multiply(price);
        BigDecimal raw = TARGET_NOTIONAL_USDT.divide(perContract, 8, RoundingMode.DOWN);
        BigDecimal lots = raw.divide(lot, 0, RoundingMode.DOWN).multiply(lot);
        BigDecimal size = lots.max(minSize).stripTrailingZeros();
        return size.scale() <= 0 ? size.toBigInteger().toString() : size.toPlainString();
    }

    /** Половина размера, округлённая вниз до лота; {@code null}, если половины не существует. */
    private String halfOf(String size) {
        BigDecimal half = new BigDecimal(size).divide(new BigDecimal("2"), 0, RoundingMode.DOWN);
        return half.signum() > 0 ? half.toBigInteger().toString() : null;
    }

    /** Ждёт до момента {@code deadline}, не трогая биржу. */
    private void sleepUntil(long deadline, String what) {
        long wait = deadline - System.currentTimeMillis();
        if (wait <= 0L) {
            return;
        }
        observeValue("AG1.7", "wait", "до момента «" + what + "» " + wait / 1000L + " с");
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ожидание границы прервано", e);
        }
    }

    /** Держит позицию до {@code deadline}, проверяя её живость на каждом шаге. */
    private void holdAlive(long deadline) {
        while (System.currentTimeMillis() < deadline) {
            assertThat(hasFundingPosition())
                    .as("AG1.7 → позиция жива на всём удержании (иначе расчёт фандинга её не застаёт)").isTrue();
            long remaining = deadline - System.currentTimeMillis();
            sleepUntil(System.currentTimeMillis() + Math.min(remaining, HOLD_PROBE_STEP.toMillis()), "шаг удержания");
        }
        observeValue("AG1.7", "heldThroughBoundaryUntil", Instant.ofEpochMilli(System.currentTimeMillis()).toString());
    }

    private void placeFundingMarket(String side, String size, boolean reduceOnly) {
        RawResponse r = post(ORDER_PATH, map(
                "instId", FUNDING_INST_ID, "tdMode", "isolated", "side", side, "ordType", "market",
                "sz", size, "clOrdId", newId("fh"), "reduceOnly", reduceOnly), SIGNED);
        assertOk(r);
        assertFirstElementOk(r);
    }

    private void closeFundingPosition() {
        RawResponse r = post(CLOSE_POSITION_PATH, map("instId", FUNDING_INST_ID, "mgnMode", "isolated",
                "posSide", "net", "autoCxl", true, "ccy", "USDT"), SIGNED);
        assertOk(r);
    }

    private void closeFundingPositionQuietly() {
        try {
            closeFundingPosition();
        } catch (RuntimeException | AssertionError e) {
            log.warn("[AG1.7] quiet close error: {}", e.getMessage());
        }
    }

    private JsonNode liveFundingPosition() {
        return get(POSITIONS_PATH, map("instType", INST_TYPE, "instId", FUNDING_INST_ID), SIGNED).d0();
    }

    private boolean hasFundingPosition() {
        String size = liveFundingPosition().path("pos").asText("");
        return !size.isBlank() && new BigDecimal(size).signum() != 0;
    }

    private boolean isFlat() {
        return !hasFundingPosition();
    }

    /** Запись закрытия эпизода из окна наблюдения; ждёт её появления в истории. */
    private JsonNode episodeRecord() {
        List<JsonNode> found = new ArrayList<>();
        waitUntil("запись закрытия эпизода появилась в positions-history", () -> {
            found.clear();
            RawResponse r = get(POSITIONS_HISTORY_PATH, map("instType", INST_TYPE, "instId", FUNDING_INST_ID,
                    "before", String.valueOf(windowStart - 1_000L),
                    "after", String.valueOf(windowEnd + 60_000L),
                    "limit", "50"), SIGNED);
            assertOk(r);
            r.data().forEach(record -> {
                if (episodePosId.equals(record.path("posId").asText(""))) {
                    found.add(record);
                }
            });
            return !found.isEmpty();
        });
        observeValue("AG1.7", "episodeRecords", found.size());
        return found.get(0);
    }

    private List<JsonNode> billsOfType(String type) {
        RawResponse r = get(BILLS_PATH, map("instType", INST_TYPE,
                "begin", String.valueOf(windowStart - 1_000L),
                "end", String.valueOf(windowEnd + 60_000L),
                "limit", "100"), SIGNED);
        assertOk(r);
        List<JsonNode> selected = new ArrayList<>();
        r.data().forEach(bill -> {
            if (type.equals(bill.path("type").asText(""))
                    && FUNDING_INST_ID.equals(bill.path("instId").asText(""))) {
                selected.add(bill);
            }
        });
        return selected;
    }

    /** Числовое поле сырого JSON: пустая строка и отсутствие ключа читаются нулём. */
    private BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }
}
