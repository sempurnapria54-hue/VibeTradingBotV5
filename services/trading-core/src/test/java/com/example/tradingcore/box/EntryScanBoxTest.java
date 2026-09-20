package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B1} — отбор входа: тик сканера.
 *
 * <p><b>Предмет группы — ТИК, и выходов у него три:</b> строка сделки с
 * её траншами (читается поверхностью), исходящие вызовы соседям (читаются
 * стабами) и строка outbox (читается базой). Ход подаётся ручным фасадом,
 * и ждётся он записью фасада о конце запуска, а не паузой
 * ({@link TradingCoreBox#tick}).
 *
 * <p><b>Этой группой у ящика впервые появляется СВОЯ тропа к сделке.</b>
 * До неё писателя сделки ящик не имел вовсе, и группа {@code B11} ставила
 * её прямой записью ({@link Rows#put}). Здесь прямая запись остаётся
 * ровно у тех предусловий, которых тик поставить не может по построению:
 * сделка в ошибочном состоянии ({@code B1.4}) — ребра в {@code ERROR} у
 * сканера нет, его двигает проход сопровождения (группа {@code B2}).
 *
 * <p><b>Условие входа — ФАЗОВОЕ, и это выбор, а не удобство.</b> Фаза
 * приезжает той же связкой фич, что и раскладки операндов, и покрыта
 * ровно тогда, когда владелец её отдал: условие на ней отделяет «условие
 * ложно» ({@code B1.9}) от «операнд недоступен» ({@code B1.8}). Условие
 * на индикаторе смешало бы оба исхода в один, и обе клетки стали бы
 * зелены при любом поведении гейта свежести.
 *
 * <p><b>Порядок обхода инструментов — ПРЕДУСЛОВИЕ половины клеток</b>, и
 * задан он идентификатором строки каталога
 * ({@code InstrumentRepository#findTradable}, {@code order by i.id asc}).
 * Поэтому проекция ставится НАЗВАННЫМ порядком ({@link #project}), а
 * клетки, которые на него опираются, проверяют его отдельным ассертом:
 * порядок, сложившийся сам собой, сделал бы «второй инструмент не
 * тронут» утверждением о везении.
 */
class EntryScanBoxTest extends SharedTradingCoreBox {

    /** Идентичность определения, которым ходит большинство клеток. */
    private static final String DEFINITION = "S1";

    /** Второе определение: им наблюдается поинструментный обход. */
    private static final String SECOND_DEFINITION = "S2";

    /** Биржевой момент, который отдаёт коннектор: им пинится поле сделки. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Класс события заведения сделки в outbox. */
    private static final String DEAL_OPENED = "DEAL_OPENED";

    /** Класс события решения о заявке: сканер его не производит. */
    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    /** Причина заведения сделки входной тропой. */
    private static final String STRATEGY_ENTRY = "STRATEGY";

    /** След отказа отбора по одному счёту в журнале приложения. */
    private static final String SCAN_FAILED = "Entry scan failed exchangeAccountId=";

    @Test
    @DisplayName("B1.1 — активное определение на свободной паре заводит сделку и её транши")
    void anActiveDefinitionOnAFreePairOpensADealWithItsDeclaredTranches() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));

        tick(Tick.ENTRY_SCANNER);

        Map<String, Object> deal = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT).single();
        assertThat(deal.get("status")).isEqualTo("ACTIVE");
        assertThat(deal.get("instrumentInternalId")).isEqualTo(INSTRUMENT);
        // Причину ставит сама тропа, а не операнд вызова: на ней стои́т
        // предикат «позиция по сделке наблюдалась»
        // (docs/components/DealOpeningService.md).
        assertThat(deal.get("entryReason")).isEqualTo(STRATEGY_ENTRY);
        // Направление прочитано с действия шага, а не назначено тиком.
        assertThat(deal.get("direction")).isEqualTo("LONG");
        // Момент создания — БИРЖЕВОЙ: он приехал от коннектора и служит
        // нижней границей окна линковки движений
        // (docs/models/domain/aggregate/Deal.md).
        assertThat(String.valueOf(deal.get("externalCreatedAt"))).startsWith("2026-09-20T10:00");

        Answer one = get(DEALS + "/" + deal.get("internalId"));
        // Транш по единственному объявлению детали, и он в PRECHECK:
        // материализация эагерна — «уровень объявлен и ждёт» видно в
        // данных.
        assertThat(one.nestedList("tranches")).hasSize(1);
        assertThat(one.nestedList("tranches"))
                .allMatch(tranche -> "PRECHECK".equals(tranche.get("status")));

        // К коннектору ушёл РОВНО один вызов — чтение биржевого момента:
        // создание сделки на биржу не ходит по своему контракту.
        assertThat(connector.paths()).containsExactly(PEER_SERVER_TIME);
        assertThat(rows.count("orders")).isZero();
        // В outbox ровно одна строка, и класс у неё один — заведение
        // сделки (docs/architecture/contracts.md §«У каждого класса
        // события назван писатель, и он же писатель решения»).
        assertThat(rows.count("outbox_events")).isEqualTo(1L);
        assertThat(rows.all("outbox_events").getFirst().get("event_type")).isEqualTo(DEAL_OPENED);
    }

    @Test
    @DisplayName("B1.2 — заведя сделку, обход счёта прекращается")
    void openingADealStopsTheWalkOfThatAccount() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT, SECOND_INSTRUMENT));
        assertThat(instrumentId(INSTRUMENT)).isLessThan(instrumentId(SECOND_INSTRUMENT));
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        featuresOf(SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));
        activate(entryDefinition(SECOND_DEFINITION, SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND));

        tick(Tick.ENTRY_SCANNER);

        // Заведена РОВНО одна сделка, и на первом инструменте обхода:
        // контурная половина гейта закрывает счёт до следующего тика.
        Map<String, Object> deal = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT).single();
        assertThat(deal.get("instrumentInternalId")).isEqualTo(INSTRUMENT);
        assertThat(rows.count("deals")).isEqualTo(1L);
        // Второй инструмент не тронут: фич по нему не запрашивалось вовсе.
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isZero();
    }

    @Test
    @DisplayName("B1.3 — активная сделка на счёте закрывает вход по всему контуру счёта")
    void anActiveDealClosesEntryOverTheWholeAccountContour() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT, SECOND_INSTRUMENT));
        featuresOf(SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(SECOND_DEFINITION, SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND));
        insertDeal("D1", INSTRUMENT, "ACTIVE");

        tick(Tick.ENTRY_SCANNER);

        // Новой сделки нет: радиус гейта — СЧЁТ, а не пара.
        assertThat(rows.count("deals")).isEqualTo(1L);
        // Счёт выпал из обхода целиком: фич не запрашивалось ни по одному
        // инструменту, включая свободный.
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isZero();
        assertThat(marketData.count(featuresPath(INSTRUMENT))).isZero();
    }

    @Test
    @DisplayName("B1.4 — ошибочное состояние слот держит")
    void anErroneousDealStillHoldsTheAccountSlot() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT, SECOND_INSTRUMENT));
        featuresOf(SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(SECOND_DEFINITION, SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND));
        // Ребра в ERROR у сканера нет — его двигает проход сопровождения,
        // поэтому предусловие ставится прямой записью (§шапка класса).
        insertDeal("D1", INSTRUMENT, "ERROR");

        tick(Tick.ENTRY_SCANNER);

        // Новой сделки нет: ошибочное состояние АКТИВНО, и слот пары оно
        // держит (docs/lifecycles/Deal.md §Группы).
        assertThat(rows.count("deals")).isEqualTo(1L);
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isZero();
        // Ответ поверхности показывает его тем же статусом.
        assertThat(get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT).single().get("status"))
                .isEqualTo("ERROR");
    }

    @Test
    @DisplayName("B1.5 — счёт под мягкой ступенью из выборки выпадает")
    void anAccountUnderTheSoftRungFallsOutOfThePopulation() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);

        tick(Tick.ENTRY_SCANNER);

        // Ступень энфорсится ВЫБОРКОЙ: счёт в популяцию отбора не попал.
        assertThat(rows.count("deals")).isZero();
        assertThat(marketData.count()).isZero();
        // Живых сделок мягкая ступень не трогает — гасится только новый
        // вход (docs/rules/exchange-hold.md §«Ступень 1 — мягкий холд»).
        assertThat(get(SAFETY_STATES + "/" + ACCOUNT).asObject().get("accountSafetyRung"))
                .isEqualTo("HOLD");
    }

    @Test
    @DisplayName("B1.6 — пара под стоящей ступенью инструмента пропускается, соседняя нет")
    void aPairUnderAStandingRungIsSkippedWhileItsNeighbourIsNot() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT, SECOND_INSTRUMENT));
        assertThat(instrumentId(INSTRUMENT)).isLessThan(instrumentId(SECOND_INSTRUMENT));
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        featuresOf(SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));
        activate(entryDefinition(SECOND_DEFINITION, SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND));
        assertThat(post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);

        tick(Tick.ENTRY_SCANNER);

        // Сделка на соседней паре, а не на первой по обходу: ступень пары
        // энфорсится выборкой (docs/rules/instrument-hold.md §Enforcement).
        Map<String, Object> deal = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT).single();
        assertThat(deal.get("instrumentInternalId")).isEqualTo(SECOND_INSTRUMENT);
        // Пара под ступенью не читалась вовсе — она выпала до чтения фич.
        assertThat(marketData.count(featuresPath(INSTRUMENT))).isZero();
        assertThat(get(SAFETY_STATES + "/" + ACCOUNT).asObject()
                .get("instrumentInternalIdsWithStandingRung")).isEqualTo(List.of(INSTRUMENT));
    }

    @Test
    @DisplayName("B1.7 — нерезолвенная фаза рынка вход не открывает")
    void anUnresolvedMarketPhaseOpensNothingAndFailsNothing() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        marketData.answers(featuresPath(INSTRUMENT), Feed.featuresWithoutPhase());
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));
        Integer mark = AppLog.mark();

        tick(Tick.ENTRY_SCANNER);

        // Деталь выбирать не по чему, и пустота здесь КОНСЕРВАТИВНА:
        // отбор молчит, а не берёт деталь наугад.
        assertThat(rows.count("deals")).isZero();
        assertThat(rows.count("outbox_events")).isZero();
        // Отказа нет: связка фич снята, просто фазы в ней не оказалось.
        assertThat(AppLog.since(mark)).doesNotContain(SCAN_FAILED);
        assertThat(marketData.count(featuresPath(INSTRUMENT))).isEqualTo(1);
        // Биржевой момент не спрашивался: до создания сделки не дошло.
        assertThat(connector.count()).isZero();
    }

    @Test
    @DisplayName("B1.8 — шаг, чьи операнды не покрыты свежей раскладкой, до оценки условия не доходит")
    void aStepWhoseOperandsAreNotCoveredYieldsToTheNextCoveredStep() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        // Раскладка несёт фазу и НЕ несёт индикаторного операнда первого
        // шага: отсутствующее и устаревшее места в раскладке не занимают.
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(Definitions.withUncoveredThenPhaseEntry(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));

        tick(Tick.ENTRY_SCANNER);

        // Сделку завёл ВТОРОЙ шаг, и видно это по направлению его
        // действия: непокрытый шаг объявляет SHORT, покрытый — LONG.
        // То есть непокрытый шаг входа не открыл, а обход шагов
        // объявления на нём не оборвался.
        Map<String, Object> deal = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT).single();
        assertThat(deal.get("direction")).isEqualTo("LONG");
        // ПОЛОВИНА «до оценки условия не доходит» ящиком не наблюдаема:
        // снаружи процесса «шаг пропущен гейтом свежести» и «условие шага
        // вычислено и ложно» дают один и тот же исход — входа нет.
        // Носитель у половины ЕСТЬ, и он назван: модульная проба
        // {@code EntryScanPassTest#aStepWhoseOperandsAreNotCoveredDoesNotOpenADeal}
        // того же дерева спрашивает интерпретатор напрямую, потому и не
        // снимается ревизией набора.
    }

    @Test
    @DisplayName("B1.9 — невыполненное условие входа сделки не заводит")
    void anUnsatisfiedEntryConditionOpensNothing() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        // Фаза резолвена и деталь под неё есть — ложно именно условие
        // шага: оно требует другой фазы.
        featuresOf(INSTRUMENT, MarketPhase.Type.RANGE);
        exchangeMoment();
        activate(Definitions.withEntryOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.RANGE, MarketPhase.Type.BULL_TREND));

        tick(Tick.ENTRY_SCANNER);

        assertThat(rows.count("deals")).isZero();
        assertThat(rows.count("deal_tranches")).isZero();
        assertThat(rows.count("outbox_events")).isZero();
        // К коннектору обращений нет: биржевой момент добывается ТОЛЬКО
        // под создание сделки.
        assertThat(connector.count()).isZero();
    }

    @Test
    @DisplayName("B1.10 — определение не в `ACTIVE` входом не пользуется")
    void aDefinitionOutsideActiveIsNotUsedForEntry() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        Strategy definition = entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND);
        activate(definition);
        moveDefinition(STRATEGY_DEACTIVATED, definition, Strategy.Status.INACTIVE.name());

        tick(Tick.ENTRY_SCANNER);

        assertThat(rows.count("deals")).isZero();
        // Фич по паре не запрашивалось: отбор статуса идёт ВЫБОРКОЙ копии,
        // то есть до всякого чтения наружу.
        assertThat(marketData.count(featuresPath(INSTRUMENT))).isZero();
    }

    @Test
    @DisplayName("B1.11 — отказ отбора по одному счёту отбор по остальным не отменяет")
    void aFailureOnOneAccountDoesNotCancelTheScanOfTheOthers() {
        project(List.of(ACCOUNT, SECOND_ACCOUNT), List.of(INSTRUMENT, SECOND_INSTRUMENT));
        // Отказ стои́т на инструменте ПЕРВОГО счёта; второй счёт держит
        // определение на соседнем инструменте и его читает исправно.
        marketData.answers(featuresPath(INSTRUMENT), 503,
                Feed.peerFailure(PEER_SERVICE_UNAVAILABLE));
        featuresOf(SECOND_INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));
        activate(secondAccountDefinition());
        Integer mark = AppLog.mark();

        tick(Tick.ENTRY_SCANNER);

        // По второму счёту сделка заведена: счета независимы, и общая
        // ветка — не выход из тика.
        Map<String, Object> deal =
                get(DEALS + "?exchangeAccountInternalId=" + SECOND_ACCOUNT).single();
        assertThat(deal.get("instrumentInternalId")).isEqualTo(SECOND_INSTRUMENT);
        assertThat(get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT).asList()).isEmpty();
        // Отказ назван в журнале: строки у этой ветки нет, и несёт её он.
        assertThat(AppLog.since(mark)).contains(SCAN_FAILED);

        // Повторный тик пробует первый счёт снова — ничего не «запомнено».
        tick(Tick.ENTRY_SCANNER);

        assertThat(marketData.count(featuresPath(INSTRUMENT))).isEqualTo(2);
        // Второй счёт вторым тиком не тронут: его закрыла своя же сделка.
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isEqualTo(1);
        assertThat(rows.count("deals")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B1.14 — сканер заявок не создаёт и риска не проверяет")
    void theScannerCreatesTheDealAndNothingElse() {
        project(List.of(ACCOUNT), List.of(INSTRUMENT));
        featuresOf(INSTRUMENT, MarketPhase.Type.BULL_TREND);
        exchangeMoment();
        activate(entryDefinition(DEFINITION, INSTRUMENT, MarketPhase.Type.BULL_TREND));

        tick(Tick.ENTRY_SCANNER);

        assertThat(rows.count("deals")).isEqualTo(1L);
        // Команд размещения к площадке не уходило: единственный вызов
        // коннектора — чтение биржевого момента.
        assertThat(connector.paths()).containsExactly(PEER_SERVER_TIME);
        assertThat(rows.count("orders")).isZero();
        assertThat(rows.count("algo_orders")).isZero();
        // Строк исполнения нет ни на одном из двух уровней: строку заводит
        // проход сопровождения, а не отбор
        // (docs/components/EntryScannerJob.md §Границы).
        assertThat(rows.count("deal_strategy_action_states")).isZero();
        assertThat(rows.count("deal_system_action_states")).isZero();
        // Событий решения о заявке в outbox нет — сканер производит
        // ТОЛЬКО заведение сделки.
        assertThat(eventTypes()).containsExactly(DEAL_OPENED);
        assertThat(eventTypes()).doesNotContain(ORDER_DECIDED);
    }

    // ------------------------------------------------------------------
    // Предусловия и наблюдатели группы
    // ------------------------------------------------------------------

    /**
     * Проекции чужих реестров тропой ящика, в НАЗВАННОМ порядке.
     *
     * <p>Порядок каталога есть порядок обхода
     * ({@code order by i.id asc}), и клетки о поинструментном обходе
     * опираются на него; раскладка с неопределённым порядком обхода
     * сделала бы их зелёными через раз.
     *
     * @param accounts    идентичности счетов
     * @param instruments идентичности инструментов в порядке заведения
     */
    private void project(List<String> accounts, List<String> instruments) {
        Map<String, String> catalogue = new LinkedHashMap<>();
        instruments.forEach(internalId -> catalogue.put(internalId, externalOf(internalId)));
        provision(accounts, catalogue);
    }

    /** Имя инструмента у площадки по его идентичности. */
    private String externalOf(String instrumentInternalId) {
        return INSTRUMENT.equals(instrumentInternalId) ? EXTERNAL_INSTRUMENT : SECOND_EXTERNAL_INSTRUMENT;
    }

    /** Связка фич момента по инструменту: раскладки пусты, фаза названа. */
    private void featuresOf(String instrumentInternalId, MarketPhase.Type phaseType) {
        marketData.answers(featuresPath(instrumentInternalId), Feed.features(phaseType.name()));
    }

    /** Путь чтения фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /** Биржевой момент у коннектора: им пинится поле создания сделки. */
    private void exchangeMoment() {
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
    }

    /** Определение счёта {@code A1} на названной паре и фазе. */
    private Strategy entryDefinition(String internalId, String instrumentInternalId,
                                     MarketPhase.Type phase) {
        return Definitions.withEntryOnPhase(internalId, ACCOUNT, instrumentInternalId, phase, phase);
    }

    /** Определение ВТОРОГО счёта — на соседнем инструменте. */
    private Strategy secondAccountDefinition() {
        return Definitions.withEntryOnPhase(SECOND_DEFINITION, SECOND_ACCOUNT, SECOND_INSTRUMENT,
                MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND);
    }

    /** Живая либо ошибочная сделка счёта прямой записью (§шапка класса). */
    private void insertDeal(String internalId, String instrumentInternalId, String status) {
        rows.put("""
                insert into deals (internal_id, exchange_account_id, instrument_id, status, direction,
                                   entry_reason)
                values (?, ?, ?, ?, 'LONG', 'RECOVERY')
                """, internalId, accountId(ACCOUNT), instrumentId(instrumentInternalId), status);
    }

    /** Классы событий строк outbox в порядке записи. */
    private List<String> eventTypes() {
        return rows.all("outbox_events").stream().map(row -> String.valueOf(row.get("event_type"))).toList();
    }
}
