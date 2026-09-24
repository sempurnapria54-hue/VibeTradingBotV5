package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B5} — ступени защиты: автоматика, каскад, снятие риска.
 *
 * <p><b>Предмет группы — СОСТАВ и ПОРЯДОК полной реакции ступени</b>, а не
 * то, какой детектор её запросил: что ищет проактивная детекция и чем
 * отвечает, — предмет группы {@code B7}; какие пары «класс × радиус»
 * законны у ручной поверхности — предмет {@code B6}. Здесь наблюдается,
 * что реакция делает с базой, со стабом коннектора и со сделками радиуса
 * (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Ступень поднимается двумя входами, и оба — тропы ящика:</b> тик
 * проактивной детекции на живом риске по инструменту вне контура
 * ({@link #standForeignInstrumentRisk}) и ручная поверхность
 * ({@code fullHalt}, {@code freeze}). Который из двух — называет вход
 * кейса; прямой записи в предусловиях группы нет ни одной.
 *
 * <p><b>Живой экспозиции у клеток этой части группы НЕТ, и это не
 * упрощение предусловия.</b> Тропа до налива входной ноги в дереве кода
 * обрывается: на статусе отправленного входа живую ногу не опрашивает
 * никто, и наблюдённого налива не появляется ни за какое число проходов
 * (находка {@code F-13} захода, .claude/work/backlog.md §«Налив входной
 * ноги на отправленном входе не наблюдается ничем»). Клетки, которым
 * экспозиция нужна предметно — порядок снятия риска, ограниченный цикл
 * подтверждения, неприкосновенность живой сделки под мягкой ступенью, —
 * ждут её закрытия и здесь не написаны: поставить их предусловие нечем.
 *
 * <p><b>Сделка радиуса ставится тиком отбора входа</b>
 * ({@link #openActiveDeal}): каскаду нужна активная сделка счёта, и
 * наблюдается он её статусом и причиной остановки.
 */
class DealSafetyRungBoxTest extends SharedTradingCoreBox {

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-SAFE";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Последняя цена момента. */
    private static final String LAST_PRICE = "100";

    /** Биржевой момент открытия чужого эпизода: половина его адреса. */
    private static final String POSITION_MOMENT = "2026-09-20T10:00:05Z";

    /** Жёсткая ступень: сворачивание радиуса. */
    private static final String TRADE_BLOCKED = "TRADE_BLOCKED";

    /** Мягкая ступень счёта. */
    private static final String HOLD = "HOLD";

    /** Класс события подъёма ступени. */
    private static final String HOLD_RAISED = "HOLD_RAISED";

    /**
     * Биржевое имя инструмента, которого в модели НЕТ: им ставится
     * признак жёсткой биржевой ступени, срабатывающий с первого
     * наблюдения ({@code ExchangeSideDetectors#outsideContour}).
     */
    private static final String FOREIGN_INSTRUMENT = "SOL-USDT-SWAP";

    /** Машинный код той же находки. */
    private static final String FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

    /** Машинный код ручной постановки: ни одна автоматика его не поднимает. */
    private static final String MANUAL_HALT_REQUESTED = "MANUAL_HALT_REQUESTED";

    /** Отсутствие ступени: рабочее состояние радиуса. */
    private static final String NO_RUNG = "ACTIVE";

    /** Машинный код хвостов заявок, не объяснимых живой сделкой. */
    private static final String ORPHAN_ORDERS = "INSTRUMENT_ORPHAN_ORDERS";

    /**
     * Клиентский идентификатор НАШЕЙ заявки: маркер контура впереди
     * ({@code InternalIdFactory#isOurs}).
     */
    private static final String OUR_CLIENT_ID = "vtbboxsafetyone";

    @Test
    @DisplayName("B5.5 — повторный сигнал по стоящей ступени реакции не гоняет")
    void theRepeatedSignalOnAStandingRungDoesNotRunTheReactionAgain() {
        openActiveDeal();
        standForeignInstrumentRisk();
        tick(Tick.ANOMALY_DETECTION);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        Object rungStandingSince = accountRow().get("modified_at");
        Object dealTouchedAt = dealRow().get("modified_at");
        Long raisedOnce = countEvents(HOLD_RAISED);
        Long reportedOnce = rows.count("anomaly_reports");
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.ANOMALY_DETECTION);

        // Статус не переставлялся: строка счёта не тронута вовсе.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(accountRow().get("modified_at")).isEqualTo(rungStandingSince);
        // Снятия риска нет: добыча эпизода — единственный наблюдаемый след
        // хода, и его к площадке не ушло.
        assertThat(connector.requests(positionPath(ACCOUNT))).isEmpty();
        // Каскада нет: сделка радиуса тем же проходом не тронута.
        assertThat(dealRow().get("modified_at")).isEqualTo(dealTouchedAt);
        // Второй строки факта подъёма нет — событие лежит в транзакции
        // перестановки, а её не было.
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
        // Второго отчёта по ТОМУ ЖЕ коду в окне тоже нет: дедуп держит
        // ключ состояния (вторую сторону снимает B5.6).
        assertThat(rows.count("anomaly_reports")).isEqualTo(reportedOnce);
    }

    @Test
    @DisplayName("B5.6 — поглощение реакции отчёт не гасит, а второе основание заводит свою строку")
    void theAbsorbedSignalStillJournalsItsOwnBasis() {
        openActiveDeal();
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(codesOfReports()).containsExactly(MANUAL_HALT_REQUESTED);
        Object rungStandingSince = accountRow().get("modified_at");
        Long raisedOnce = countEvents(HOLD_RAISED);
        standForeignInstrumentRisk();
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.ANOMALY_DETECTION);

        // Реакции нет: статус стои́т, второго факта подъёма нет, снятие
        // риска не гонялось.
        assertThat(accountRow().get("modified_at")).isEqualTo(rungStandingSince);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
        assertThat(connector.requests(positionPath(ACCOUNT))).isEmpty();
        // Отчёт по НОВОМУ коду заведён, и он журнальный: снимка «после» у
        // него нет по построению — между «до» и «после» ничего не
        // происходило.
        assertThat(codesOfReports()).containsExactlyInAnyOrder(MANUAL_HALT_REQUESTED,
                FOREIGN_INSTRUMENT_RISK);
        Map<String, Object> journalled = rows.row("anomaly_reports", "code", FOREIGN_INSTRUMENT_RISK);
        assertThat(journalled.get("status")).isEqualTo("COMPLETED");
        assertThat(journalled.get("internal_after")).isNull();

        // Второй отчёт по тому же коду в окне не заводится.
        tick(Tick.ANOMALY_DETECTION);

        assertThat(rows.count("anomaly_reports")).isEqualTo(2L);
    }

    @Test
    @DisplayName("B5.8 — эскалация с мягкой на жёсткую реакцию не пропускает")
    void theEscalationFromTheSoftRungDoesNotSkipTheReaction() {
        openActiveDeal();
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        assertThat(accountRung()).isEqualTo(HOLD);
        Long raisedOnSoft = countEvents(HOLD_RAISED);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        fullHalt(ACCOUNT);

        // Мягкая ступень анкером идемпотентности не является: переход
        // состоялся, и состав реакции отработал целиком.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnSoft + 1);
        // Отчёт жёсткой ступени доведён до терминала со снимком «после».
        Map<String, Object> report = rows.allOrderedBy("anomaly_reports", "id").getLast();
        assertThat(report.get("severity")).isEqualTo("CRITICAL");
        assertThat(report.get("status")).isEqualTo("COMPLETED");
        assertThat(report.get("internal_before")).isNotNull();
        assertThat(report.get("internal_after")).isNotNull();
        // Снятие риска гонялось: эпизод сделки спрошен у площадки.
        assertThat(connector.requests(positionPath(ACCOUNT))).isNotEmpty();
        // Каскад увёл активную сделку радиуса в ошибочное состояние.
        assertThat(dealRow().get("status")).isEqualTo("ERROR");
        assertThat(dealRow().get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
    }

    @Test
    @Tag("debt")
    @DisplayName("B5.9 — инструментный сигнал на счёте под биржевой ступенью ничего не делает")
    void theInstrumentSignalOnAnAccountUnderTheExchangeRungDoesNothing() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        Object rungStandingSince = accountRow().get("modified_at");
        Long raisedOnce = countEvents(HOLD_RAISED);
        standOrphanOrders();
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.ANOMALY_DETECTION);
        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        // Признак инструментного радиуса найден и ПОДТВЕРЖДЁН: строка по
        // его коду стои́т, то есть детектор сработал, а молчания гейта
        // неполноты клетка не наблюдает.
        assertThat(codesOfReports()).contains(ORPHAN_ORDERS);
        // Ступень пары не переставлена: биржевая ступень доминирует
        // инструментные реакции, и сигнал уже биржевой ступени не делает
        // ничего (docs/rules/exchange-hold.md §«Границы и эскалация»,
        // docs/rules/instrument-hold.md §Enforcement).
        //
        // КРАСНО ПО ПОСТРОЕНИЮ, метка `debt`: доминирования биржевой
        // ступени не исполняет ни один носитель тропы — ни детектор, ни
        // реакция, ни ребро подъёма, — и пара встаёт в `ENTRY_BLOCKED`.
        // Ожидание взято из дома и под текущий факт не ослаблено
        // (.claude/work/backlog.md §«Доминирование биржевой ступени над
        // инструментной реакцией не исполняет ни один носитель»).
        assertThat(pairRung(INSTRUMENT)).isEqualTo(NO_RUNG);
        // Счётная ступень стои́т и второй реакции не получила: статус не
        // переставлялся, второго факта подъёма нет.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(accountRow().get("modified_at")).isEqualTo(rungStandingSince);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
        // Снятия риска и каскада нет: ни снятой заявки, ни закрытой
        // позиции — у мягкой ступени их нет в составе вовсе.
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B5.14 — отказ журнального носителя реакцию не отменяет")
    void theFailingJournalDoesNotCancelTheReaction() {
        openActiveDeal();
        rows.refuse("anomaly_reports", "insert");
        Integer mark = AppLog.mark();

        try {
            fullHalt(ACCOUNT);
        } finally {
            rows.allow("anomaly_reports", "insert");
        }

        // Ступень поднята, снятие риска гонялось, каскад отработал — ни
        // один из трёх ходов от наблюдателя не зависит.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(connector.requests(positionPath(ACCOUNT))).isNotEmpty();
        assertThat(dealRow().get("status")).isEqualTo("ERROR");
        assertThat(dealRow().get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        // Отчёта нет, а строка об отказе записи — есть: молчаливого
        // пропуска у журнала не бывает.
        assertThat(rows.count("anomaly_reports")).isZero();
        assertThat(AppLog.since(mark)).contains("Anomaly report open failed");
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /**
     * Активная сделка счёта: её подбирает каскад жёсткой ступени, и её
     * статусом каскад наблюдается.
     *
     * <p><b>Заявок у сделки нет, и это названо, а не упущено:</b> команда
     * площадке требует ещё одного прохода, а живая экспозиция —
     * наблюдённого налива, которого тропа ящика не даёт (§шапка класса).
     * Предмет клеток этой части группы — ступень, отчёт и каскад, и все
     * три наблюдаются без экспозиции.
     */
    private void openActiveDeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(positionPath(ACCOUNT), Feed.absent());
        connector.answers(positionsPath(ACCOUNT), Feed.emptyArray());
        connector.answers(closedPositionsPath(ACCOUNT), Feed.emptyArray());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(1L);
        assertThat(dealRow().get("status")).isEqualTo("ACTIVE");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Срез счёта с живым риском по инструменту, которого в модели нет:
     * признак жёсткой биржевой ступени, срабатывающий с первого
     * наблюдения.
     *
     * <p><b>Срез обязан быть ПОЛНЫМ</b>: на неполном проходе детекторы
     * молчат ({@code AnomalyJob#observe}), и клетка наблюдала бы тишину
     * гейта вместо реакции.
     */
    private void standForeignInstrumentRisk() {
        connector.answers(positionsPath(ACCOUNT), Feed.array(Feed.livePosition("ex-foreign-1",
                FOREIGN_INSTRUMENT, "1", LAST_PRICE, POSITION_MOMENT)));
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
    }

    /**
     * Срез счёта с ХВОСТОМ заявок: позиции по инструменту нет, живая
     * заявка есть, живой сделки на паре нет — признак инструментного
     * радиуса ({@code AccountingDetectors#orphanOrders}).
     *
     * <p><b>Срез обязан быть ПОЛНЫМ</b>: на неполном проходе детекторы
     * молчат гейтом, и клетка наблюдала бы тишину вместо признака.
     */
    private void standOrphanOrders() {
        connector.answers(positionsPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingOrdersPath(ACCOUNT),
                Feed.array(Feed.pendingOrder("ex-orphan-1", OUR_CLIENT_ID, EXTERNAL_INSTRUMENT)));
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
    }

    /**
     * Отодвигает НАЗАД момент стоящих наблюдательных строк: единственная
     * прямая правка базы в предусловиях, и строк она не заводит.
     *
     * <p>Признак с гистерезисом подтверждается строкой, заведённой не
     * позже, чем разрешает минимальный возраст подтверждения; без правки
     * второй тик обязан был бы отстоять от первого на тридцать секунд
     * стенных часов. Дом довода — шапка {@link ProactiveDetectionBoxTest}.
     */
    private void ageObservations() {
        rows.put("update anomaly_reports set created_at = created_at - interval '2 minutes'");
    }

    /** Ступень пары «счёт, инструмент»; строки пары нет — рабочее состояние. */
    private String pairRung(String instrumentInternalId) {
        List<Map<String, Object>> found = rows.select("select * from account_instrument_states"
                        + " where exchange_account_id = ? and instrument_id = ?",
                accountId(ACCOUNT), instrumentId(instrumentInternalId));
        return found.isEmpty() ? NO_RUNG : String.valueOf(found.getFirst().get("safety_rung"));
    }

    /** Путь снятия обычной заявки: первый ход снятия живого риска. */
    private String cancellationPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/cancellations";
    }

    /** Рыночное закрытие позиции: второй ход снятия живого риска. */
    private String closurePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closures";
    }

    /** Строка биржевого счёта, как её видит база. */
    private Map<String, Object> accountRow() {
        return rows.row("exchange_accounts", "internal_id", ACCOUNT);
    }

    /** Ступень счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(accountRow().get("safety_rung"));
    }

    /** Единственная сделка. */
    private Map<String, Object> dealRow() {
        return rows.all("deals").getFirst();
    }

    /** Машинные коды заведённых отчётов в порядке записи. */
    private List<String> codesOfReports() {
        return rows.allOrderedBy("anomaly_reports", "id").stream()
                .map(row -> String.valueOf(row.get("code")))
                .toList();
    }

    /** Сколько строк outbox несёт названный класс. */
    private Long countEvents(String eventType) {
        return rows.all("outbox_events").stream()
                .filter(row -> eventType.equals(String.valueOf(row.get("event_type"))))
                .count();
    }

    /** Путь чтения связки фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Путь чтения снимка средств у коннектора. */
    private String balancePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/balance";
    }

    /** Живые заявки счёта целиком: первый срез проактивной детекции. */
    private String pendingOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending";
    }

    /** Живые отдельные условные заявки счёта целиком: второй срез. */
    private String pendingAlgoOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/pending";
    }

    /** Живые позиции счёта целиком: третий срез. */
    private String positionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions";
    }

    /** Живой эпизод позиции по инструменту: след хода снятия риска. */
    private String positionPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/instrument";
    }

    /** История закрытых эпизодов: нога 2 добычи позиции. */
    private String closedPositionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closed";
    }

    /** Снимок средств моментом прогона: возраст ставится В ДАННЫХ. */
    private String balanceBody() {
        String moment = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return """
                {
                  "externalUpdatedAt": "%s",
                  "externalTotalEquity": "100000",
                  "externalAdjustedEquity": "100000",
                  "externalAvailableEquity": "100000",
                  "balances": [
                    {
                      "externalCurrency": "USDT",
                      "externalUpdatedAt": "%s",
                      "externalEquity": "100000",
                      "externalCashBalance": "100000",
                      "externalAvailableBalance": "100000",
                      "externalFrozenBalance": "0"
                    }
                  ]
                }
                """.formatted(moment, moment);
    }
}
