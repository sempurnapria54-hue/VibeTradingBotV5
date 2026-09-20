package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B10} — проекции чужих реестров, ставки комиссии,
 * объявление потребности.
 *
 * <p><b>Предмет группы — ТИК, а выход его лежит в двух местах сразу:</b>
 * строками проекций в базе и записями стабов соседей. Отрицания группы
 * («обращений нет», «метка не сдвинулась», «второй тик не ходит»)
 * наблюдаются только парой этих наблюдателей: строка молчит о том,
 * спрашивали ли владельца, а запись стаба молчит о том, что записали.
 *
 * <p><b>Момент снимка читается со смещением</b> ({@link Rows}): он и есть
 * операнд гейта свежести, и клетки {@code B10.3} и {@code B10.4}
 * утверждают именно о нём — о том, что он НЕ сдвинулся.
 *
 * <p><b>Копия определения ставится сообщением</b>
 * ({@link TradingCoreBox#activate}), а не вставкой дерева: писатель копии
 * у ядра один — слушатель темы владельца определений.
 */
class ProjectionsBoxTest extends SharedTradingCoreBox {

    /** Третий инструмент: им наблюдается «отказ одной строки — одна строка». */
    private static final String THIRD_INSTRUMENT = "I3";

    /** Имя третьего инструмента у площадки. */
    private static final String THIRD_EXTERNAL_INSTRUMENT = "SOL-USDT-SWAP";

    /** Путь правил инструмента у владельца каталога. */
    private static final String RULES = "/rules";

    /** Приватное чтение ставок комиссии у коннектора. */
    private static final String FEE_RATES = "/trade-fee-rates";

    /** Требование ряда свечей у владельца данных. */
    private static final String REQUIRE_CANDLES = "/api/v1/market-data/requirements/candles";

    /** Требование идентичности индикатора. */
    private static final String REQUIRE_INDICATORS = "/api/v1/market-data/requirements/indicators";

    /** Требование идентичности структуры рынка. */
    private static final String REQUIRE_STRUCTURES = "/api/v1/market-data/requirements/market-structures";

    @Test
    @DisplayName("B10.1 — синк проекций сводит реестр счетов и каталог одним моментом снимка")
    void oneTickProjectsBothRegistriesUnderOneSnapshotMoment() {
        auth.answers(PEER_ACCOUNTS, Feed.array(
                Feed.account(ACCOUNT, TENANT, "DEMO", "ACTIVE"),
                Feed.account(SECOND_ACCOUNT, TENANT, "DEMO", "ACTIVE")));
        marketData.answers(PEER_INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, EXTERNAL_INSTRUMENT),
                Feed.instrument(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT)));
        answerRules(INSTRUMENT, EXTERNAL_INSTRUMENT);
        answerRules(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);

        tick(Tick.REGISTRY_PROJECTIONS);

        assertThat(rows.count("exchange_accounts")).isEqualTo(2L);
        assertThat(rows.count("instruments")).isEqualTo(2L);
        // Момент снимка отвечает на вопрос «когда мы в последний раз
        // спрашивали владельца», и он ОДИН на тик: дробить его по строкам
        // значило бы мерить длительность собственного прохода.
        assertThat(projectedMoments()).hasSize(1);
        // Правила прочитаны в том же проходе, что и спецификация: иначе
        // одна метка описывала бы свежую спецификацию при старом навесе.
        assertThat(marketData.count(PEER_INSTRUMENTS + "/" + INSTRUMENT + RULES)).isEqualTo(1);
        assertThat(marketData.count(PEER_INSTRUMENTS + "/" + SECOND_INSTRUMENT + RULES)).isEqualTo(1);
        assertThat(rows.row("instruments", "internal_id", INSTRUMENT).get("external_rules")).isNotNull();
        // Под тенанта появившихся счетов заведено место под числа
        // риск-аппетита — заводит его тот же проход.
        assertThat(rows.countWhere("tenant_risk_appetites", "tenant_internal_id", TENANT)).isEqualTo(1L);
    }

    @Test
    @DisplayName("B10.2 — половины тика независимы")
    void theTwoHalvesOfTheTickDoNotDependOnEachOther() {
        auth.refusesAnything(503, Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));
        marketData.answers(PEER_INSTRUMENTS, Feed.array(Feed.instrument(INSTRUMENT, EXTERNAL_INSTRUMENT)));
        answerRules(INSTRUMENT, EXTERNAL_INSTRUMENT);
        Integer mark = AppLog.mark();

        tick(Tick.REGISTRY_PROJECTIONS);

        // Каталог сведён, хотя владелец реестра счетов отвечал отказом:
        // перезапуск соседа не делается причиной устаревания чужой
        // проекции.
        assertThat(rows.count("instruments")).isEqualTo(1L);
        assertThat(rows.count("exchange_accounts")).isZero();
        assertThat(AppLog.since(mark)).contains("Exchange account projection sync failed");
    }

    @Test
    @DisplayName("B10.3 — исчезнувшая у владельца строка из проекции не удаляется")
    void aRowThatDisappearedAtTheOwnerIsKeptWithItsOldSnapshotMoment() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Long instrument = instrumentId(INSTRUMENT);
        OffsetDateTime before = projectedAt(INSTRUMENT);
        Long deal = insertRecoveryDeal(accountId(ACCOUNT), instrument);
        marketData.answers(PEER_INSTRUMENTS, Feed.emptyArray());

        tick(Tick.REGISTRY_PROJECTIONS);

        // Строка на месте и со СТАРЫМ моментом снимка: она сама
        // показывает себя гейту свежести, который вход по ней и отвергнет
        // (docs/rules/market-data-freshness.md).
        assertThat(rows.count("instruments")).isEqualTo(1L);
        assertThat(projectedAt(INSTRUMENT)).isEqualTo(before);
        // Ссылки торговых строк целы — удаление оборвало бы их.
        assertThat(rows.row("deals", "id", deal).get("instrument_id")).isEqualTo(instrument);
    }

    @Test
    @DisplayName("B10.4 — отказ по одному инструменту метку не двигает и проход не роняет")
    void aRefusalOnOneInstrumentCostsExactlyThatInstrument() {
        provision(List.of(ACCOUNT), Map.of(THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT));
        OffsetDateTime before = projectedAt(THIRD_INSTRUMENT);
        marketData.answers(PEER_INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, EXTERNAL_INSTRUMENT),
                Feed.instrument(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT),
                Feed.instrument(THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT)));
        answerRules(INSTRUMENT, EXTERNAL_INSTRUMENT);
        answerRules(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);
        // Осознанный отказ соседа (4xx) — наш дефект, а не его
        // недоступность: проход его терпит построчно
        // (docs/rules/runtime-error-classification.md).
        marketData.answers(PEER_INSTRUMENTS + "/" + THIRD_INSTRUMENT + RULES, 422,
                Feed.peerFailure("INVALID_REQUEST"));

        tick(Tick.REGISTRY_PROJECTIONS);

        assertThat(projectedAt(INSTRUMENT)).isNotNull().isNotEqualTo(before);
        assertThat(projectedAt(SECOND_INSTRUMENT)).isNotNull();
        // Метка третьего не сдвинулась — то есть он сам себя показывает
        // гейту свежести, а тик при этом дошёл до конца.
        assertThat(projectedAt(THIRD_INSTRUMENT)).isEqualTo(before);

        // Повтор пробует третий снова: невзятая строка не помечается
        // негодной, она просто остаётся старой.
        Integer attemptsBefore = marketData.count(PEER_INSTRUMENTS + "/" + THIRD_INSTRUMENT + RULES);
        tick(Tick.REGISTRY_PROJECTIONS);
        assertThat(marketData.count(PEER_INSTRUMENTS + "/" + THIRD_INSTRUMENT + RULES))
                .isGreaterThan(attemptsBefore);
    }

    @Test
    @DisplayName("B10.5 — недоступность владельца каталога прекращает проход целиком")
    void anUnavailableCatalogueOwnerStopsTheWholePass() {
        provision(List.of(ACCOUNT), Map.of());
        auth.answers(PEER_ACCOUNTS, Feed.array(Feed.account(ACCOUNT, TENANT, "DEMO", "ACTIVE")));
        marketData.answers(PEER_INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, EXTERNAL_INSTRUMENT),
                Feed.instrument(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT),
                Feed.instrument(THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT)));
        answerRules(INSTRUMENT, EXTERNAL_INSTRUMENT);
        marketData.answers(PEER_INSTRUMENTS + "/" + SECOND_INSTRUMENT + RULES, 503,
                Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));
        marketData.answers(PEER_INSTRUMENTS + "/" + THIRD_INSTRUMENT + RULES, 503,
                Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));

        tick(Tick.REGISTRY_PROJECTIONS);

        // Первый сведён, обход прекращён: следующие четыреста вызовов
        // дали бы тот же отказ.
        assertThat(rows.count("instruments")).isEqualTo(1L);
        assertThat(projectedAt(INSTRUMENT)).isNotNull();
        assertThat(marketData.count(PEER_INSTRUMENTS + "/" + THIRD_INSTRUMENT + RULES)).isZero();
        // Сделок в ошибку не уводится: недоступность соседа по ярусу —
        // свой класс отказа.
        assertThat(rows.countWhere("deals", "status", "ERROR")).isZero();
    }

    @Test
    @DisplayName("B10.6 — ставка комиссии читается одним вызовом на пару «счёт, тип инструмента»")
    void feeRatesAreReadOncePerAccountAndInstrumentType() {
        provision(List.of(ACCOUNT, SECOND_ACCOUNT), Map.of(
                INSTRUMENT, EXTERNAL_INSTRUMENT,
                SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT,
                THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT));
        connector.answers(feeRatePath(ACCOUNT), Feed.array(feeRate("A")));
        connector.answers(feeRatePath(SECOND_ACCOUNT), Feed.array(feeRate("B")));

        tick(Tick.TRADE_FEE_RATES);

        // Ставка есть атрибут комиссионной группы СЧЁТА, а не
        // инструмента: три инструмента одного типа дают один вызов на
        // счёт, а не три.
        assertThat(connector.count(feeRatePath(ACCOUNT))).isEqualTo(1);
        assertThat(connector.count(feeRatePath(SECOND_ACCOUNT))).isEqualTo(1);
        // Перечень типов взят ИЗ ДАННЫХ проекции, а не из константы: тип
        // в запросе тот, который лежит в каталоге.
        assertThat(connector.single(feeRatePath(ACCOUNT)).getUrl()).contains("externalInstrumentType=SWAP");
        // Счёт-владельца проставило ядро: числовые ключи баз границу
        // сервиса не пересекают, и коннектор его заполнить не может.
        assertThat(rows.countWhere("trade_fee_rates", "exchange_account_id", accountId(ACCOUNT)))
                .isEqualTo(1L);
        assertThat(rows.countWhere("trade_fee_rates", "exchange_account_id", accountId(SECOND_ACCOUNT)))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B10.7 — отказ одной пары стоит одну пару")
    void aRefusalOnOnePairCostsExactlyThatPair() {
        provision(List.of(ACCOUNT, SECOND_ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        connector.answers(feeRatePath(ACCOUNT), 503, Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));
        connector.answers(feeRatePath(SECOND_ACCOUNT), Feed.array(feeRate("B")));
        Integer mark = AppLog.mark();

        tick(Tick.TRADE_FEE_RATES);

        assertThat(rows.countWhere("trade_fee_rates", "exchange_account_id", accountId(SECOND_ACCOUNT)))
                .isEqualTo(1L);
        assertThat(rows.countWhere("trade_fee_rates", "exchange_account_id", accountId(ACCOUNT))).isZero();
        assertThat(AppLog.since(mark)).contains("Trade fee rate sync failed for account=" + ACCOUNT);
    }

    @Test
    @DisplayName("B10.8 — пустая проекция каталога синк ставок пропускает")
    void anEmptyCatalogueProjectionSkipsTheFeeRateSync() {
        provisionAccounts(ACCOUNT);
        connector.answersAnything(Feed.emptyArray());

        tick(Tick.TRADE_FEE_RATES);

        // Перечень типов пуст — спрашивать не о чем: обращений нет,
        // отказа нет, записей не появилось.
        assertThat(connector.count()).isZero();
        assertThat(rows.count("trade_fee_rates")).isZero();
    }

    @Test
    @DisplayName("B10.9 — потребность объявляется по копиям с непривязанным объявлением")
    void demandIsDeclaredForCopiesThatCarryAnUnboundDeclaration() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withComputations("S1", ACCOUNT, INSTRUMENT);
        activate(definition);
        answerComputations();

        tick(Tick.STRATEGY_DEMAND);

        // Порядок несущий: ряды — потом идентичности индикаторов — потом
        // структуры. Структура адресует свои входы идентичностями, и её
        // требование составимо только после индикаторов.
        assertThat(requirementOrder()).containsExactly(
                REQUIRE_CANDLES, REQUIRE_INDICATORS, REQUIRE_INDICATORS, REQUIRE_STRUCTURES);
        assertThat(boundIndicatorIdentities()).containsOnly("cfg-indicator");
        assertThat(boundStructureIdentities()).containsOnly("cfg-structure");

        // Второй тик по той же копии обращений не делает: работы у тика
        // ровно столько, сколько непривязанных объявлений.
        PeerStub.all().forEach(PeerStub::forgetRequests);
        tick(Tick.STRATEGY_DEMAND);
        assertThat(requirementOrder()).isEmpty();
    }

    @Test
    @DisplayName("B10.10 — недоступность владельца данных приём определения не роняет")
    void anUnavailableDataOwnerDoesNotBreakTheDefinitionReception() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.refusesAnything(503, Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));
        Strategy definition = Definitions.withComputations("S2", ACCOUNT, INSTRUMENT);

        activate(definition);
        tick(Tick.STRATEGY_DEMAND);

        // Копия заведена и активна — приём определения от соседа не
        // зависит.
        assertThat(rows.row("strategies", "internal_id", "S2").get("status")).isEqualTo("ACTIVE");
        // Потребность не объявлена, идентичности не привязаны: торговать
        // копия не начинает — её условия не адресуются.
        assertThat(boundIndicatorIdentities()).containsOnlyNulls();
        assertThat(boundStructureIdentities()).containsOnlyNulls();

        // Следующий тик на поднятом соседе объявление доводит.
        answerComputations();
        tick(Tick.STRATEGY_DEMAND);
        assertThat(boundIndicatorIdentities()).containsOnly("cfg-indicator");
        assertThat(boundStructureIdentities()).containsOnly("cfg-structure");
    }

    @Test
    @DisplayName("B10.11 — объявленная глубина ряда едет как есть")
    void theDeclaredSeriesDepthTravelsUnchanged() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        activate(Definitions.withSeriesDepths("S3", ACCOUNT, INSTRUMENT));
        answerComputations();

        tick(Tick.STRATEGY_DEMAND);

        List<Map<String, Object>> required = requiredSeries();
        // Два объявления одного таймфрейма дают ОДНО требование, и
        // глубина у него бо́льшая из двух: своего числа прогрева ядро не
        // подставляет — вывод прогрева принадлежит владельцу данных.
        assertThat(required).hasSize(2);
        assertThat(depthOf(required, "ONE_HOUR")).isEqualTo(300);
        // Пустая объявленная глубина означает всю доступную историю, а не
        // подставленное число.
        assertThat(depthOf(required, "FOUR_HOURS")).isNull();
    }

    /** Заготовка ответа владельца каталога на правила названного инструмента. */
    private void answerRules(String internalId, String externalId) {
        marketData.answers(PEER_INSTRUMENTS + "/" + internalId + RULES, Feed.instrumentRules(externalId));
    }

    /** Заготовки ответов владельца данных на все три требования. */
    private void answerComputations() {
        marketData.answers(REQUIRE_CANDLES, "{}");
        marketData.answers(REQUIRE_INDICATORS, """
                {"internalId": "cfg-indicator"}
                """);
        marketData.answers(REQUIRE_STRUCTURES, """
                {"internalId": "cfg-structure"}
                """);
    }

    /** Путь приватного чтения ставок комиссии счёта у коннектора. */
    private String feeRatePath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId + FEE_RATES;
    }

    /** Тело одного наблюдения комиссионной группы. */
    private String feeRate(String group) {
        return """
                {
                  "externalInstrumentType": "SWAP",
                  "instrumentType": "SWAP",
                  "externalFeeGroupId": "%s",
                  "externalTakerFeeRate": "-0.0005",
                  "externalMakerFeeRate": "-0.0002",
                  "externalFeeLevel": "Lv1"
                }
                """.formatted(group);
    }

    /** Различные моменты снимка среди строк обеих проекций. */
    private List<Object> projectedMoments() {
        return rows.select("""
                select distinct projected_at from (
                    select projected_at from exchange_accounts
                    union all
                    select projected_at from instruments) moments
                """).stream().map(row -> row.get("projected_at")).toList();
    }

    /** Момент снимка строки каталога. */
    private OffsetDateTime projectedAt(String internalId) {
        return (OffsetDateTime) rows.row("instruments", "internal_id", internalId).get("projected_at");
    }

    /** Пути требований к владельцу данных в порядке отправки. */
    private List<String> requirementOrder() {
        return marketData.requestsUnder("/api/v1/market-data/requirements").stream()
                .map(request -> request.getUrl().split("\\?")[0])
                .toList();
    }

    /** Привязанные идентичности индикаторных объявлений копии. */
    private List<Object> boundIndicatorIdentities() {
        return rows.all("strategy_indicator_settings").stream()
                .map(row -> row.get("computation_config_internal_id"))
                .toList();
    }

    /** Привязанные идентичности структурных объявлений копии. */
    private List<Object> boundStructureIdentities() {
        return rows.all("strategy_market_structure_settings").stream()
                .map(row -> row.get("computation_config_internal_id"))
                .toList();
    }

    /** Тела требований рядов, разобранные телом запроса. */
    private List<Map<String, Object>> requiredSeries() {
        return marketData.requests(REQUIRE_CANDLES).stream()
                .map(request -> new Answer(200, request.getBodyAsString()).asObject())
                .toList();
    }

    /** Объявленная глубина требования по имени таймфрейма. */
    private Object depthOf(List<Map<String, Object>> required, String timeframe) {
        return required.stream()
                .filter(body -> timeframe.equals(body.get("timeframe")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Требования ряда " + timeframe + " нет: " + required))
                .get("depthBars");
    }

    /**
     * Сделка без объявления — то есть восстановительная: ею наблюдается
     * целостность ссылки торговой строки на проекцию.
     */
    private Long insertRecoveryDeal(Long account, Long instrument) {
        return rows.insert("""
                insert into deals (internal_id, exchange_account_id, instrument_id, status, direction,
                                   entry_reason)
                values ('D1', ?, ?, 'ACTIVE', 'LONG', 'RECOVERY') returning id
                """, account, instrument);
    }
}
