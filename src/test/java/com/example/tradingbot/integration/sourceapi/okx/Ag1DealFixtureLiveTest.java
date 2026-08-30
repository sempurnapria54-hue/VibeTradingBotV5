package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Фикстура закрытой сделки и содержательные кейсы шага 7, которые на ней
 * сидят: AG1.5, AG1.6, AG1.7, AG1.8, AG1.9, AG3.4, AG6.2
 * (`.claude/tests/source-api/okx/plan.md` §AG1.5 «Фикстура — общая, прогон
 * один»). Все семь кейсов читают ОДНУ цепочку — отдельного прогона на
 * каждый не создаётся.
 *
 * <p><b>Цепочка.</b> Эпизод 1: открытие {@code 2×lotSz} → частичное
 * закрытие reduce-only {@code 1×lotSz} → полное закрытие → flat. Эпизод 2
 * (в том же окне): открытие {@code 1×lotSz} → полное закрытие → flat.
 * Отсюда окно истории содержит два эпизода, первый из которых закрывался
 * двумя слайсами — ровно то, что требуют предусловия пп. 1 и 15.
 *
 * <p><b>Инвариант восстановления.</b> Цепочка заканчивается flat; Verify.end
 * проверяет отсутствие позиции и живых заявок.
 */
@Order(60)
class Ag1DealFixtureLiveTest extends OkxSourceApiLiveTestBase {

    private static final String POSITIONS_HISTORY_PATH = "/api/v5/account/positions-history";
    private static final String BILLS_PATH = "/api/v5/account/bills";
    private static final String SUBTYPES_PATH = "/api/v5/account/subtypes";
    /** Размер эпизода 1: два лота, чтобы закрытие шло двумя слайсами. */
    private static final String EPISODE_1_SZ = "0.02";
    /** Тип bill'а торгового движения (справочник AG6.1: 2 — Trade). */
    private static final String BILL_TYPE_TRADE = "2";
    /** Тип bill'а funding-расчёта (справочник AG6.1: 8 — Funding rate). */
    private static final String BILL_TYPE_FUNDING = "8";
    /** Тип bill'а маржинального перевода (справочник AG6.1: 6 — Margin transfer). */
    private static final String BILL_TYPE_MARGIN_TRANSFER = "6";
    /** Типы принудительного закрытия (справочник AG6.1: 5 — Forced liquidation, 9 — ADL). */
    private static final String BILL_TYPE_FORCED_LIQUIDATION = "5";
    private static final String BILL_TYPE_ADL = "9";

    /** Нижняя граница окна сделки (мс), выставляется до первой заявки. */
    private static long windowStart;
    /** Верхняя граница окна сделки (мс), выставляется после выхода в flat. */
    private static long windowEnd;
    /** {@code posId}, наблюдённый у живой позиции эпизода 1. */
    private static String episode1PosId;
    /** {@code posId}, наблюдённый у живой позиции эпизода 2. */
    private static String episode2PosId;
    /** Собралась ли фикстура — гейт для всех кейсов ниже. */
    private static boolean fixtureBuilt;

    @Test
    @Order(10)
    @DisplayName("Фикстура — два эпизода в одном окне, первый с частичным закрытием")
    void fixture_twoEpisodesOneWindow() {
        step("FIXTURE");
        pollTimeout = Duration.ofSeconds(60);

        // Snapshot.start — ни позиции, ни живых заявок по инструменту.
        assertThat(hasOpenPosition()).as("FIXTURE → Snapshot.start: позиции нет").isFalse();
        windowStart = System.currentTimeMillis();

        // Эпизод 1: открытие двумя лотами.
        placeMarket("buy", EPISODE_1_SZ, false);
        waitUntil("эпизод 1 открыт", this::hasOpenPosition);
        episode1PosId = livePosId();
        assertThat(episode1PosId).as("FIXTURE → posId живой позиции эпизода 1").isNotBlank();
        observeValue("FIXTURE", "episode1.posId", episode1PosId);

        // Эпизод 1: частичное закрытие одним лотом (reduce-only).
        placeMarket("sell", MIN_SZ, true);
        waitUntil("эпизод 1 частично закрыт", () -> MIN_SZ.equals(livePosSize()));
        observeValue("FIXTURE", "episode1.sizeAfterPartialClose", livePosSize());

        // Эпизод 1: полное закрытие.
        closePosition();
        waitUntil("эпизод 1 закрыт полностью", () -> !hasOpenPosition());

        // Эпизод 2 — в том же окне, по тому же инструменту.
        placeMarket("buy", MIN_SZ, false);
        waitUntil("эпизод 2 открыт", this::hasOpenPosition);
        episode2PosId = livePosId();
        observeValue("FIXTURE", "episode2.posId", episode2PosId);
        closePosition();
        waitUntil("эпизод 2 закрыт", () -> !hasOpenPosition());

        windowEnd = System.currentTimeMillis();
        observeValue("FIXTURE", "window", windowStart + ".." + windowEnd);

        // Verify.end — состояние вернулось к старту.
        assertRestoredOrHalt("FIXTURE", "позиция и живые заявки по инструменту",
                () -> !hasOpenPosition() && noPendingOrders(), this::closePositionQuietly);
        fixtureBuilt = true;
    }

    @Test
    @Order(20)
    @DisplayName("AG1.5 Содержательный — семантика агрегации partial-close")
    void ag1_5_partialCloseAggregation() {
        requireFixture();
        observeContent("AG1.5", historyByWindow());
        List<JsonNode> records = windowRecords();

        JsonNode episode1 = records.stream()
                .filter(record -> EPISODE_1_SZ.equals(record.path("closeTotalPos").asText("")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "AG1.5 → в окне нет записи с closeTotalPos=" + EPISODE_1_SZ
                                + ": источник не агрегирует слайсы частичного закрытия в одну запись"));

        // (1) Одна финализированная запись на эпизод, а не по записи на слайс.
        long episode1Records = records.stream()
                .filter(record -> EPISODE_1_SZ.equals(record.path("closeTotalPos").asText("")))
                .count();
        assertThat(episode1Records).as("AG1.5 → эпизод с частичным закрытием даёт ровно одну запись").isEqualTo(1L);

        // (2) closeTotalPos — ПОЛНЫЙ закрытый объём, а не объём последнего слайса.
        assertThat(new BigDecimal(episode1.path("closeTotalPos").asText()))
                .as("AG1.5 → closeTotalPos покрывает оба слайса")
                .isEqualByComparingTo(new BigDecimal(EPISODE_1_SZ));

        // (3) realizedPnl кумулятивен по всей жизни эпизода и равен сумме слагаемых.
        BigDecimal pnl = decimal(episode1, "pnl");
        BigDecimal fee = decimal(episode1, "fee");
        BigDecimal fundingFee = decimal(episode1, "fundingFee");
        BigDecimal liqPenalty = decimal(episode1, "liqPenalty");
        BigDecimal realized = decimal(episode1, "realizedPnl");
        observeValue("AG1.5", "realizedPnl", realized);
        observeValue("AG1.5", "pnl+fee+fundingFee+liqPenalty", pnl.add(fee).add(fundingFee).add(liqPenalty));
        assertThat(realized)
                .as("AG1.5 → realizedPnl = pnl + fee + fundingFee + liqPenalty (композиция записи)")
                .isEqualByComparingTo(pnl.add(fee).add(fundingFee).add(liqPenalty));

        // (4) Сверка с движениями счёта: слагаемые записи равны суммам по bills окна.
        BigDecimal billsPnl = BigDecimal.ZERO;
        BigDecimal billsFee = BigDecimal.ZERO;
        for (JsonNode bill : windowBills()) {
            if (BILL_TYPE_TRADE.equals(bill.path("type").asText(""))) {
                billsPnl = billsPnl.add(decimal(bill, "pnl"));
                billsFee = billsFee.add(decimal(bill, "fee"));
            }
        }
        observeValue("AG1.5", "Σ bills pnl (оба эпизода)", billsPnl);
        observeValue("AG1.5", "Σ bills fee (оба эпизода)", billsFee);
        BigDecimal recordsPnl = records.stream()
                .map(record -> decimal(record, "pnl")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recordsFee = records.stream()
                .map(record -> decimal(record, "fee")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(recordsPnl).as("AG1.5 → Σ pnl записей окна = Σ pnl торговых bills окна")
                .isEqualByComparingTo(billsPnl);
        assertThat(recordsFee).as("AG1.5 → Σ fee записей окна = Σ fee торговых bills окна")
                .isEqualByComparingTo(billsFee);

        // (5) Момент финализации: запись обновлена не раньше открытия эпизода.
        observeValue("AG1.5", "episode1.cTime", episode1.path("cTime").asText());
        observeValue("AG1.5", "episode1.uTime", episode1.path("uTime").asText());
        assertThat(Long.parseLong(episode1.path("uTime").asText()))
                .as("AG1.5 → запись финализирована не раньше открытия эпизода")
                .isGreaterThan(Long.parseLong(episode1.path("cTime").asText()));
    }

    @Test
    @Order(30)
    @DisplayName("AG1.9 Содержательный — окно с несколькими записями = эпизоды сделки")
    void ag1_9_episodesInWindow() {
        requireFixture();
        List<JsonNode> records = windowRecords();

        // Записей столько, сколько эпизодов, — источник не схлопывает их в одну.
        assertThat(records).as("AG1.9 → окно двух эпизодов отдаёт две записи").hasSize(2);

        // Адресуемость: пара (posId, cTime) уникальна по записям окна.
        Set<String> pairs = new LinkedHashSet<>();
        Set<String> posIds = new LinkedHashSet<>();
        for (JsonNode record : records) {
            pairs.add(record.path("posId").asText("") + "@" + record.path("cTime").asText(""));
            posIds.add(record.path("posId").asText(""));
            observeValue("AG1.9", "record", record.path("posId").asText()
                    + " cTime=" + record.path("cTime").asText()
                    + " uTime=" + record.path("uTime").asText()
                    + " closeTotalPos=" + record.path("closeTotalPos").asText()
                    + " realizedPnl=" + record.path("realizedPnl").asText());
        }
        assertThat(pairs).as("AG1.9 → запись эпизода адресуема парой (posId, cTime)").hasSize(2);

        // НЕСУЩЕЕ наблюдение предусловия п. 15: получает ли переоткрытая
        // позиция НОВЫЙ posId в пределах окна. Ответ «переиспользован» ломает
        // дискриминатор смены эпизода и ключ uk_position_deal_external.
        observeValue("AG1.9", "posIdsInWindow", posIds);
        observeValue("AG1.9", "posIdReusedAcrossEpisodes", posIds.size() == 1);
        observeValue("AG1.9", "livePosId(эпизод 1 / эпизод 2)", episode1PosId + " / " + episode2PosId);

        // realizedPnl каждой записи кумулятивен ВНУТРИ своего эпизода.
        for (JsonNode record : records) {
            assertThat(decimal(record, "realizedPnl"))
                    .as("AG1.9 → realizedPnl записи = сумма её слагаемых")
                    .isEqualByComparingTo(decimal(record, "pnl")
                            .add(decimal(record, "fee"))
                            .add(decimal(record, "fundingFee"))
                            .add(decimal(record, "liqPenalty")));
        }
    }

    @Test
    @Order(40)
    @DisplayName("AG1.6 Содержательный — оси адресации записи без posId")
    void ag1_6_addressingAxes() {
        requireFixture();

        // (1) Какие оси запроса источник принимает помимо posId.
        RawResponse byInstTypeOnly = get(POSITIONS_HISTORY_PATH, map("instType", INST_TYPE, "limit", "5"), SIGNED);
        assertOk(byInstTypeOnly);
        observeValue("AG1.6", "axis instType", "n=" + byInstTypeOnly.dataSize());

        RawResponse byInstIdOnly = get(POSITIONS_HISTORY_PATH, map("instId", INST_ID, "limit", "5"), SIGNED);
        observeValue("AG1.6", "axis instId без instType",
                "code=" + byInstIdOnly.code() + " n=" + byInstIdOnly.dataSize());
        assertThat(byInstIdOnly.codeZero())
                .as("AG1.6 → instId принимается без instType: create-тропа адресует запись инструментом")
                .isTrue();

        RawResponse byWindow = historyByWindow();
        assertOk(byWindow);
        observeValue("AG1.6", "axis instId+before/after(uTime)", "n=" + byWindow.dataSize());
        assertThat(byWindow.dataSize())
                .as("AG1.6 → окно по uTime отбирает ровно записи окна сделки")
                .isEqualTo(2);

        // posId как фильтр — принимается, но, будучи переиспользован, уже не
        // адресует одну запись: наблюдение, а не ожидание.
        RawResponse byPosId = get(POSITIONS_HISTORY_PATH,
                map("instType", INST_TYPE, "instId", INST_ID, "posId", episode1PosId, "limit", "10"), SIGNED);
        observeValue("AG1.6", "axis posId", "code=" + byPosId.code() + " n=" + byPosId.dataSize());

        // (3) Отдаёт ли запись cTime и direction — операнды материализации
        // Position на create-тропе.
        for (JsonNode record : windowRecords()) {
            assertThat(record.path("cTime").asText(""))
                    .as("AG1.6 → запись несёт cTime (операнд create-тропы)").isNotBlank();
            assertThat(record.path("direction").asText(""))
                    .as("AG1.6 → запись несёт direction (операнд create-тропы)").isNotBlank();
            assertThat(record.path("openAvgPx").asText(""))
                    .as("AG1.6 → запись несёт openAvgPx").isNotBlank();
        }

        // (2) Порядок записей в окне — наблюдение (от него зависит разбор пагинации).
        List<String> order = new ArrayList<>();
        for (JsonNode record : windowRecords()) {
            order.add(record.path("uTime").asText());
        }
        observeValue("AG1.6", "uTimeOrderInWindow", order);
    }

    @Test
    @Order(50)
    @DisplayName("AG1.7 Содержательный — семантика и знаки числовых полей записи")
    void ag1_7_numericFieldSemantics() {
        requireFixture();
        List<JsonNode> records = windowRecords();

        // (3) ПРЕДУСЛОВИЕ п. 14 — форма пустого значения несобытийного поля.
        // Ожидание: строковый "0", не пустая строка и не отсутствие ключа.
        for (JsonNode record : records) {
            for (String field : List.of("fundingFee", "liqPenalty")) {
                JsonNode value = record.path(field);
                observeValue("AG1.7", "emptyForm." + field,
                        "missing=" + value.isMissingNode() + " text='" + value.asText("") + "'");
                assertThat(value.isMissingNode())
                        .as("AG1.7 → несобытийное поле %s присутствует в записи ключом", field).isFalse();
                assertThat(value.asText(""))
                        .as("AG1.7 → несобытийное поле %s приходит строковым нулём, не пустой строкой", field)
                        .isEqualTo("0");
            }
            // Контраст: поля, не применимые к записи, источник отдаёт ПУСТОЙ
            // строкой — форма пустого значения различается по полю.
            observeValue("AG1.7", "contrast.settledPnl", "'" + record.path("settledPnl").asText("") + "'");
            observeValue("AG1.7", "contrast.nonSettleAvgPx", "'" + record.path("nonSettleAvgPx").asText("") + "'");
            observeValue("AG1.7", "contrast.triggerPx", "'" + record.path("triggerPx").asText("") + "'");
        }

        // (2) Фактические знаки операндов — наблюдение, не утверждение.
        for (JsonNode record : records) {
            observeValue("AG1.7", "sign.fee",
                    decimal(record, "fee").signum() + " (" + record.path("fee").asText() + ")");
            observeValue("AG1.7", "sign.fundingFee",
                    decimal(record, "fundingFee").signum() + " (" + record.path("fundingFee").asText() + ")");
            observeValue("AG1.7", "sign.liqPenalty",
                    decimal(record, "liqPenalty").signum() + " (" + record.path("liqPenalty").asText() + ")");
        }
        // Комиссия — издержка: на закрытой без ребейта сделке знак отрицательный.
        assertThat(records.stream().allMatch(record -> decimal(record, "fee").signum() <= 0))
                .as("AG1.7 → fee приходит издержкой (знак минус), ребейта на фикстуре нет").isTrue();

        // (1) ПРЕДУСЛОВИЕ п. 7 — горизонт fundingFee. Наблюдаем только при
        // пересечении границы funding-интервала; иначе слот НЕ закрывается.
        List<JsonNode> fundingBills = billsOfType(BILL_TYPE_FUNDING);
        observeValue("AG1.7", "fundingBillsInWindow", fundingBills.size());
        if (fundingBills.isEmpty()) {
            observeValue("AG1.7", "п. 7 горизонт fundingFee",
                    "НЕ НАБЛЮДЁН: за окно фикстуры границу funding-интервала не пересекли; "
                            + "слот остаётся PENDING (фикстура требует удержания позиции через границу)");
            return;
        }
        BigDecimal billsFunding = fundingBills.stream()
                .map(bill -> decimal(bill, "balChg")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recordsFunding = records.stream()
                .map(record -> decimal(record, "fundingFee")).reduce(BigDecimal.ZERO, BigDecimal::add);
        observeValue("AG1.7", "Σ funding bills balChg", billsFunding);
        observeValue("AG1.7", "Σ record fundingFee", recordsFunding);
    }

    @Test
    @Order(60)
    @DisplayName("AG3.4 Содержательный — комиссии приходят в settle-ccy")
    void ag3_4_feeCurrency() {
        requireFixture();
        List<JsonNode> bills = windowBills();
        assertThat(bills).as("AG3.4 → окно фикстуры содержит движения счёта").isNotEmpty();

        List<JsonNode> feeBills = new ArrayList<>();
        for (JsonNode bill : bills) {
            if (decimal(bill, "fee").signum() != 0) {
                feeBills.add(bill);
            }
        }
        assertThat(feeBills).as("AG3.4 → в окне есть записи с ненулевой комиссией").isNotEmpty();
        for (JsonNode bill : feeBills) {
            observeValue("AG3.4", "feeBill",
                    "type=" + bill.path("type").asText() + " subType=" + bill.path("subType").asText()
                            + " ccy=" + bill.path("ccy").asText() + " fee=" + bill.path("fee").asText());
            assertThat(bill.path("ccy").asText(""))
                    .as("AG3.4 → комиссия SWAP приходит в settle-ccy (инвариант trading-constraints)")
                    .isEqualTo("USDT");
        }

        // Вторая проверка — состав realizedPnl при cross-ccy-издержке. Ветка
        // редкая: исполняется ПРИ НАЛИЧИИ движения чужой ccy в окне.
        Set<String> currencies = new LinkedHashSet<>();
        for (JsonNode bill : bills) {
            currencies.add(bill.path("ccy").asText(""));
        }
        observeValue("AG3.4", "currenciesInWindow", currencies);
        if (currencies.size() == 1) {
            observeValue("AG3.4", "cross-ccy",
                    "НЕ НАБЛЮДАЛОСЬ: движений вне settle-ccy в окне нет — посылка о составе realizedPnl "
                            + "этой фикстурой не разрешается");
        }
    }

    @Test
    @Order(70)
    @DisplayName("AG6.2 Содержательный — перечень типов вне экономики сделки")
    void ag6_2_typesOutsideDealEconomics() {
        requireFixture();

        RawResponse dictionary = get(SUBTYPES_PATH, null, SIGNED);
        assertOk(dictionary);
        Set<String> dictionaryPairs = new LinkedHashSet<>();
        for (JsonNode type : dictionary.data()) {
            for (JsonNode sub : type.path("subTypeDetails")) {
                dictionaryPairs.add(type.path("type").asText("") + "/" + sub.path("subType").asText(""));
            }
        }
        observeValue("AG6.2", "dictionaryPairCount", dictionaryPairs.size());

        // Какие пары справочника ФАКТИЧЕСКИ попали в окно сделки.
        Set<String> windowPairs = new LinkedHashSet<>();
        for (JsonNode bill : windowBills()) {
            windowPairs.add(bill.path("type").asText("") + "/" + bill.path("subType").asText(""));
        }
        observeValue("AG6.2", "pairsInDealWindow", windowPairs);
        assertThat(windowPairs).as("AG6.2 → окно сделки непусто по типам движений").isNotEmpty();
        assertThat(dictionaryPairs).as("AG6.2 → каждая наблюдённая пара есть в справочнике")
                .containsAll(windowPairs);

        // Несущее: появляются ли МАРЖИНАЛЬНЫЕ движения по позиции в окне сделки
        // при isolated-марже. Они несут instId и сопоставимы с самой позицией —
        // то есть раздували бы Σ|amount| и композиционный член epsilon.
        List<JsonNode> marginBills = billsOfType(BILL_TYPE_MARGIN_TRANSFER);
        observeValue("AG6.2", "marginTransferBillsInWindow", marginBills.size());
        for (JsonNode bill : marginBills) {
            observeValue("AG6.2", "marginBill", bill);
        }

        // Кандидаты списка исключений = пары справочника, не попавшие в окно
        // торговой экономики. Перечень — операнд конфига исключений.
        List<String> outsideWindow = new ArrayList<>();
        for (String pair : dictionaryPairs) {
            if (!windowPairs.contains(pair)) {
                outsideWindow.add(pair);
            }
        }
        observeValue("AG6.2", "pairsNotInDealWindowCount", outsideWindow.size());
        assertThat(outsideWindow)
                .as("AG6.2 → список исключений сверки непуст: справочник шире экономики сделки")
                .isNotEmpty();
    }

    @Test
    @Order(80)
    @DisplayName("AG1.8 Содержательный — след автоделевериджа в bills")
    void ag1_8_adlTrace() {
        requireFixture();

        List<JsonNode> forced = new ArrayList<>(billsOfType(BILL_TYPE_FORCED_LIQUIDATION));
        forced.addAll(billsOfType(BILL_TYPE_ADL));
        observeValue("AG1.8", "forcedBillsInWindow", forced.size());
        for (JsonNode bill : forced) {
            observeValue("AG1.8", "forcedBill", bill);
        }

        // Шире окна: весь доступный период bills — вдруг эпизод был раньше.
        RawResponse wideAdl = get(BILLS_PATH,
                map("instType", INST_TYPE, "type", BILL_TYPE_ADL, "limit", "100"), SIGNED);
        RawResponse wideLiq = get(BILLS_PATH,
                map("instType", INST_TYPE, "type", BILL_TYPE_FORCED_LIQUIDATION, "limit", "100"), SIGNED);
        observeValue("AG1.8", "adlBills(7d)", wideAdl.dataSize());
        observeValue("AG1.8", "forcedLiquidationBills(7d)", wideLiq.dataSize());

        if (forced.isEmpty() && wideAdl.dataEmpty() && wideLiq.dataEmpty()) {
            observeValue("AG1.8", "п. 10 след автоделевериджа",
                    "НЕ НАБЛЮДЁН: эпизод ADL инициирует биржа, на demo не заказывается; "
                            + "слот остаётся PENDING — исходом «не наступило» гейт не закрывается");
        }
    }

    // ------------------------------------------------------------------
    // Фикстурные хелперы
    // ------------------------------------------------------------------

    private void requireFixture() {
        Assumptions.assumeTrue(fixtureBuilt, "фикстура не собралась — содержательные кейсы не исполняются");
    }

    private void placeMarket(String side, String sz, boolean reduceOnly) {
        RawResponse r = post(ORDER_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "side", side, "ordType", "market",
                "sz", sz, "clOrdId", newId("fx"), "reduceOnly", reduceOnly), SIGNED);
        assertOk(r);
        assertFirstElementOk(r);
    }

    private void closePosition() {
        RawResponse r = post(CLOSE_POSITION_PATH, map("instId", INST_ID, "mgnMode", "isolated",
                "posSide", "net", "autoCxl", true, "ccy", "USDT"), SIGNED);
        assertOk(r);
    }

    private JsonNode livePosition() {
        RawResponse r = get(POSITIONS_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
        return r.d0();
    }

    private String livePosId() {
        return livePosition().path("posId").asText("");
    }

    private String livePosSize() {
        return livePosition().path("pos").asText("");
    }

    private RawResponse historyByWindow() {
        return get(POSITIONS_HISTORY_PATH, map("instType", INST_TYPE, "instId", INST_ID,
                "before", String.valueOf(windowStart - 1_000L),
                "after", String.valueOf(windowEnd + 60_000L),
                "limit", "50"), SIGNED);
    }

    private List<JsonNode> windowRecords() {
        RawResponse r = historyByWindow();
        assertOk(r);
        List<JsonNode> records = new ArrayList<>();
        r.data().forEach(records::add);
        return records;
    }

    private List<JsonNode> windowBills() {
        RawResponse r = get(BILLS_PATH, map("instType", INST_TYPE,
                "begin", String.valueOf(windowStart - 1_000L),
                "end", String.valueOf(windowEnd + 60_000L),
                "limit", "100"), SIGNED);
        assertOk(r);
        List<JsonNode> bills = new ArrayList<>();
        r.data().forEach(bills::add);
        return bills;
    }

    private List<JsonNode> billsOfType(String type) {
        List<JsonNode> selected = new ArrayList<>();
        for (JsonNode bill : windowBills()) {
            if (type.equals(bill.path("type").asText(""))) {
                selected.add(bill);
            }
        }
        return selected;
    }

    /** Числовое поле сырого JSON: пустая строка и отсутствие ключа читаются нулём. */
    private BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }
}
