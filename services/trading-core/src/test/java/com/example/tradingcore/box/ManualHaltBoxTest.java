package com.example.tradingcore.box;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B6} — ручная поверхность остановки.
 *
 * <p><b>Предмет группы — сама ПОВЕРХНОСТЬ: какие пары «класс × радиус»
 * она принимает, какой формой отвечает и что проверяет перед
 * применением.</b> Состав и порядок полной реакции — предмет группы
 * {@code B5}; что ищет проактивная детекция — предмет {@code B7}. Здесь
 * наблюдается, что вернул вызов, что стало со ступенью радиуса и какая
 * строка легла в журнал (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Прямой записи в предусловиях группы нет ни одной:</b> счета и
 * инструменты заводятся тиком синка проекций, ступени поднимаются той же
 * поверхностью, которую клетки и наблюдают.
 *
 * <p><b>Число ответа здесь пиньнуто законно.</b> {@code 202}, {@code 204}
 * и {@code 400} контейнера фиксирует дом
 * (docs/rules/error-handling-policy.md §«Внешняя поверхность»,
 * §«Отказ, произведённый контейнером, — тот же контракт»); у отказов,
 * которые пишет наш обработчик, ожидание берёт КЛАСС, а число идёт рядом
 * как факт прогона (.claude/tests/cases/trading-core.md §«Число ответа и
 * класс отказа — разные ожидания»).
 *
 * <p><b>Клетка с меткой {@code debt} красна по построению:</b> её
 * ожидание взято из дома, который дерево кода на этой тропе не исполняет
 * (.claude/tests/cases/trading-core.md §«Ожидание берётся из дома, даже
 * когда сегодня оно не исполнено»).
 */
class ManualHaltBoxTest extends SharedTradingCoreBox {

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-HALT";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Последняя цена момента: от неё считаются и уровень, и размер. */
    private static final String LAST_PRICE = "100";

    /** Биржевой момент открытия чужого эпизода: половина его адреса. */
    private static final String POSITION_MOMENT = "2026-09-20T10:00:05Z";

    /** Отсутствие ступени: рабочее состояние радиуса. */
    private static final String NO_RUNG = "ACTIVE";

    /** Мягкая ступень биржевого счёта. */
    private static final String HOLD = "HOLD";

    /** Жёсткая ступень обоих радиусов: сворачивание. */
    private static final String TRADE_BLOCKED = "TRADE_BLOCKED";

    /** Мягкая ступень инструментного радиуса. */
    private static final String ENTRY_BLOCKED = "ENTRY_BLOCKED";

    /** Машинный код ручной ПОСТАНОВКИ. */
    private static final String MANUAL_HALT_REQUESTED = "MANUAL_HALT_REQUESTED";

    /** Машинный код ручного СНЯТИЯ: направление операции различает он. */
    private static final String MANUAL_HALT_CLEARED = "MANUAL_HALT_CLEARED";

    /** Класс события подъёма ступени: его пишет ребро самого перехода. */
    private static final String HOLD_RAISED = "HOLD_RAISED";

    /**
     * Биржевое имя инструмента, которого в модели НЕТ: им ставится
     * признак жёсткой биржевой ступени, срабатывающий с первого
     * наблюдения.
     */
    private static final String FOREIGN_INSTRUMENT = "SOL-USDT-SWAP";

    /** Машинный код той же находки: его поднимает АВТОМАТИКА. */
    private static final String FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

    /**
     * Класс события снятия ступени — по дому обязан быть, писателя не
     * имеет ({@code B6.13}).
     */
    private static final String HOLD_RELEASED = "HOLD_RELEASED";

    @Test
    @DisplayName("B6.1 — полная постановка асинхронна, мягкая синхронна")
    void theFullRaiseIsAsynchronousWhileTheSoftOneIsNot() {
        provisionAccounts(ACCOUNT);

        Answer soft = freeze(ACCOUNT);
        Answer full = fullHaltAnswer(ACCOUNT, null);

        // Формы разведены СЧЁТНО, а не стилем: мягкая постановка может
        // отказать (B6.5), и синхронный ответ её отказ показывает; полная
        // гоняет снятие риска с повторами, и её ответ говорит только о
        // запуске.
        assertThat(soft.status()).isEqualTo(204);
        assertThat(full.status()).isEqualTo(202);
        // Оба хода применились: мягкая ступень поднялась ответом, жёсткая —
        // к моменту, когда фасад дописал свой конец в журнал.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
    }

    @Test
    @DisplayName("B6.2 — четыре допустимые пары «класс × радиус», прочие — отказ при запуске")
    void theSurfaceTakesTheFourLegalPairsAndRefusesTheRestAtTheEntry() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT,
                SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT));

        // Негодные пары — первыми: их след обязан быть пустым, а после
        // законных четырёх пустоту уже не отличить.
        Answer softWithoutInstrument = post(HALTS, Bodies.halt("SOFT", ACCOUNT));
        Answer freezeWithInstrument = post(HALTS, Bodies.halt("FREEZE", ACCOUNT, INSTRUMENT));

        List<Answer> refused = List.of(softWithoutInstrument, freezeWithInstrument);
        assertThat(refused).allMatch(answer -> Boolean.TRUE.equals(answer.carriesErrorDto()));
        assertThat(refused).extracting(Answer::errorCode)
                .containsExactly(INVALID_REQUEST, INVALID_REQUEST);
        assertThat(refused).extracting(Answer::status).containsExactly(400, 400);
        // Ступени не тронуты и отчёта нет: перечень пар задан лестницами, и
        // отказ приходит раньше, чем объект радиуса вообще разрешается —
        // строки состояния пары не материализовалось ни одной.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("account_instrument_states")).isZero();
        assertThat(rows.count("anomaly_reports")).isZero();

        // Законные четыре: мягкая счёта, мягкая пары, полная пары, полная
        // счёта. Порядок выбран так, чтобы каждая применилась на своём
        // радиусе, а не поглотилась соседней.
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        assertThat(post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);
        assertThat(fullHaltAnswer(ACCOUNT, SECOND_INSTRUMENT).status()).isEqualTo(202);
        assertThat(fullHaltAnswer(ACCOUNT, null).status()).isEqualTo(202);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(pairRung(INSTRUMENT)).isEqualTo(ENTRY_BLOCKED);
        assertThat(pairRung(SECOND_INSTRUMENT)).isEqualTo(TRADE_BLOCKED);
    }

    @Test
    @DisplayName("B6.3 — неизвестный класс вмешательства — негодный вход вызова")
    void theUnknownInterventionClassIsAnInvalidCallInput() {
        provisionAccounts(ACCOUNT);

        Answer answer = post(HALTS, Bodies.halt("PANIC", ACCOUNT));

        // Перевод api → domain делает контроллер, и неизвестное значение
        // уходит тем же единым error-DTO, что прочие негодные входы: своего
        // контракта отказа поверхность не заводит.
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(INVALID_REQUEST);
        assertThat(answer.status()).isEqualTo(400);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("anomaly_reports")).isZero();
    }

    @Test
    @DisplayName("B6.5 — мягкая постановка отказывает по статусу, полная — нет")
    void theSoftRaiseRefusesByStatusWhileTheFullOneDoesNot() {
        provisionClosedAccount();

        Answer soft = freeze(ACCOUNT);

        // Множество входа мягкой ступени — только рабочее состояние: счёт
        // отключён владельцем и своей ступени не несёт, и поднимать мягкую
        // не на чем.
        assertThat(soft.carriesErrorDto()).isTrue();
        assertThat(soft.errorCode()).isEqualTo(INVALID_REQUEST);
        assertThat(soft.status()).isEqualTo(400);
        assertThat(accountRung()).isEqualTo(NO_RUNG);

        Answer full = fullHaltAnswer(ACCOUNT, null);

        // Полная постановка того же отказа не знает: множество её входа —
        // любое состояние, авария застаёт объект в любом.
        assertThat(full.status()).isEqualTo(202);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);

        // Вторая сторона того же правила: под СТОЯЩЕЙ ступенью своего
        // радиуса мягкая постановка не отказывает, а ПОГЛОЩАЕТСЯ — статуса
        // не меняет и реакции не запускает. Отказ и поглощение разводит
        // ровно наличие стоящей ступени, а не имя статуса.
        Answer absorbed = freeze(ACCOUNT);

        assertThat(absorbed.status()).isEqualTo(204);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
    }

    @Test
    @DisplayName("B6.6 — снятие называет ступень явно, и повтор попадает в холостой ход")
    void theClearanceNamesItsRungAndTheRepeatFallsIntoANoOp() {
        provisionAccounts(ACCOUNT);
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        assertThat(accountRung()).isEqualTo(HOLD);

        Answer first = clear("FREEZE", ACCOUNT);

        assertThat(first.status()).isEqualTo(204);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        // Снятие — происшествие, и свою строку оно заводит: без кода
        // направления журнал не отличил бы «поднял» от «снял».
        assertThat(codesOfReports()).containsExactly(MANUAL_HALT_REQUESTED, MANUAL_HALT_CLEARED);

        Answer second = clear("FREEZE", ACCOUNT);

        // Холостой ход: названная ступень не стои́т — строки журнала нет, и
        // по лестнице вызов дальше не шагнул. Без явно названной ступени
        // второе нажатие вернуло бы торговлю.
        assertThat(second.status()).isEqualTo(204);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(codesOfReports()).containsExactly(MANUAL_HALT_REQUESTED, MANUAL_HALT_CLEARED);
    }

    @Test
    @DisplayName("B6.7 — прыжка через ступень нет")
    void theClearanceDoesNotJumpOverAStandingRung() {
        provisionAccounts(ACCOUNT);
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        Long journalled = rows.count("anomaly_reports");

        Answer answer = clear("FREEZE", ACCOUNT);

        // Снимать мягкую нечего, пока над ней стои́т жёсткая: холостым ходом
        // такой вызов не считается, он отвергается.
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(INVALID_REQUEST);
        assertThat(answer.status()).isEqualTo(400);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(rows.count("anomaly_reports")).isEqualTo(journalled);
    }

    @Test
    @DisplayName("B6.8 — снятие сворачивания ведёт в мягкую ступень, а не в рабочее состояние")
    void theTeardownRungIsClearedIntoTheSoftOneAndNotIntoTheWorkingState() {
        provisionAccounts(ACCOUNT);
        // Живого риска на радиусе нет: сделок у счёта нет вовсе, и выборка
        // предусловия пуста.
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);

        Answer answer = clear("FULL", ACCOUNT);

        assertThat(answer.status()).isEqualTo(204);
        // Две ступени — два хода: поверхность читает счёт как стоящий в
        // мягкой ступени, а не как рабочий.
        assertThat(accountRung()).isEqualTo(HOLD);
        Answer state = get(SAFETY_STATES + "/" + ACCOUNT);
        assertThat(state.status()).isEqualTo(200);
        assertThat(state.asObject().get("accountSafetyRung")).isEqualTo(HOLD);
    }

    @Test
    @DisplayName("B6.4 — ручная постановка идёт тем же механизмом и тем же составом реакции")
    void theManualRaiseGoesByTheSameMechanismWithTheSameReactionComposition() {
        openDealWithLiveLeg();

        fullHalt(ACCOUNT);

        // Состав — тот же, что у автоматики: жёсткий статус, факт подъёма,
        // отчёт со своим кодом, снятие живого риска, каскад сделок радиуса.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(eventTypes()).contains(HOLD_RAISED);
        assertThat(codesOfReports()).containsExactly(MANUAL_HALT_REQUESTED);
        // Снятие риска дошло до живой ноги: её снятие ушло к площадке, и
        // ушёл же вопрос об эпизоде — подтверждение приносят факты, а не
        // ответы.
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isNotEmpty();
        assertThat(connector.requests(positionPath(ACCOUNT))).isNotEmpty();
        assertThat(dealRow().get("status")).isEqualTo("ERROR");
        assertThat(dealRow().get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        // Второго механизма остановки не наблюдается: код причины у отчёта
        // один, и ни одна автоматическая тропа его не поднимает.
        assertThat(codesOfReports()).doesNotContain(FOREIGN_INSTRUMENT_RISK);
    }

    @Test
    @DisplayName("B6.14 — полная постановка на паре снимает риск сделки пары, хотя триггерной сделки у вызова нет")
    void theFullPairRaiseTearsThePairDealDownWithoutATriggerDeal() {
        openDealWithLiveLeg();
        Integer mark = AppLog.mark();

        assertThat(fullHaltAnswer(ACCOUNT, INSTRUMENT).status()).isEqualTo(202);

        // Сделку пары снятие риска берёт популяцией радиуса, а не из
        // контекста вызова: обход дошёл до живой ноги, и её снятие ушло к
        // площадке — разыменования пустой сделки нет.
        assertThat(pairRung(INSTRUMENT)).isEqualTo(TRADE_BLOCKED);
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isNotEmpty();
        assertThat(AppLog.since(mark)).doesNotContain("NullPointerException");
        // Стаб снятия ноги не подтверждает, и ход доходит до развилки
        // эскалации: неподтверждённое снятие пары поднимает ступень счёта.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(dealRow().get("status")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("B6.15 — полная постановка на счёте закрывает позицию без сделки и подтверждает снятие срезом позиций")
    void theFullAccountRaiseClosesAPositionWithoutADeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        connector.answersInTurn(positionsPath(ACCOUNT), Feed.array(Feed.livePosition("ex-dealless-1",
                EXTERNAL_INSTRUMENT, "1", LAST_PRICE, POSITION_MOMENT)), Feed.emptyArray());
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));

        fullHalt(ACCOUNT);

        // Сделки у счёта нет, а живая позиция есть: снятие риска закрывает её
        // само, адресуя инструментом строки и валютой расчёта проекции.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(rows.count("deals")).isZero();
        List<LoggedRequest> closures = connector.requests(closurePath(ACCOUNT));
        assertThat(closures).hasSize(1);
        assertThat(closures.getFirst().queryParameter("externalInstrumentId").firstValue())
                .isEqualTo(EXTERNAL_INSTRUMENT);
        assertThat(closures.getFirst().queryParameter("settleCurrency").firstValue()).isEqualTo("USDT");
        // Подтверждение пришло срезом позиций: следующее чтение пусто, и
        // отчёт доведён до терминала.
        assertThat(rows.row("anomaly_reports", "code", MANUAL_HALT_REQUESTED).get("status"))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("B6.9 — непогашенный живой риск снятие сворачивания отвергает")
    void theUnclearedLiveRiskRefusesTheTeardownClearance() {
        standUnconfirmedTeardown();

        Answer answer = clear("FULL", ACCOUNT);

        // Предусловие машинное, а не заявляемое: нога сделки жива, и
        // снятие поверх неё вернуло бы вход в торговлю над живым риском.
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(INVALID_REQUEST);
        assertThat(answer.status()).isEqualTo(400);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(liveOrderStatus()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("B6.11 — повторный полный вызов гоняет снятие риска заново")
    void theRepeatedFullCallRunsTheRiskTeardownAgain() {
        standUnconfirmedTeardown();
        Long raisedOnce = countEvents(HOLD_RAISED);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        Answer answer = fullHaltAnswer(ACCOUNT, null);

        // Анкер стоящей ступени явный вызов держателя НЕ поглощает: это
        // единственный выход из сворачивания с неподтверждённым снятием
        // риска.
        assertThat(answer.status()).isEqualTo(202);
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isNotEmpty();
        // Строки факта подъёма второй нет: событие лежит в транзакции
        // перестановки, а перестановки не было — объявлять фактом ход,
        // которого не случилось, нельзя.
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
    }

    @Test
    @DisplayName("B6.12 — автоматический сигнал доведения не получает")
    void theAutomaticSignalGetsNoRightToFinishTheUnfinished() {
        standUnconfirmedTeardown();
        Long raisedOnce = countEvents(HOLD_RAISED);
        standForeignInstrumentRisk();
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.ANOMALY_DETECTION);

        // Право на доведение есть только у явного вызова держателя: иначе
        // периодический детектор непогашенного риска слал бы аварийное
        // закрытие каждым тиком, и бюджет попыток перестал бы что-либо
        // ограничивать.
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(positionPath(ACCOUNT))).isEmpty();
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
    }

    @Test
    @Tag("debt")
    @DisplayName("B6.13 — ручное снятие на биржу не ходит и события не производит")
    void theManualClearanceNeitherReachesTheExchangeNorProducesItsFact() {
        provisionAccounts(ACCOUNT);
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        Answer answer = clear("FREEZE", ACCOUNT);

        assertThat(answer.status()).isEqualTo(204);
        // На биржу снятие не ходит: живой риск оно не возобновляет и снятых
        // защит не восстанавливает — трогается только гейт.
        assertThat(connector.requests()).isEmpty();
        assertThat(rows.row("anomaly_reports", "code", MANUAL_HALT_CLEARED)).isNotEmpty();
        // Долг: по дому факт снятия ступени обязан уезжать классом
        // `HoldReleased`, а писателя у класса нет — его нет и в перечне
        // классов события (.claude/work/backlog.md §«Класс события
        // `HoldReleased` и его ручная тропа»).
        assertThat(eventTypes()).contains(HOLD_RELEASED);
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /**
     * Сделка с ЖИВОЙ ногой входа: единственная форма живого риска, до
     * которой тропа ящика доходит.
     *
     * <p><b>Живой экспозиции у неё нет, и это названо, а не упущено.</b>
     * Тропа до налива входной ноги в дереве кода обрывается: на статусе
     * отправленного входа живую ногу не опрашивает никто (находка
     * {@code F-13}, .claude/work/backlog.md §«Налив входной ноги на
     * отправленном входе не наблюдается ничем»). Живой риск здесь
     * предъявлен живой НОГОЙ — она и есть один из пяти его признаков
     * (docs/spec/deal-lifecycle.json §riskProvenAbsent), и снятие риска
     * снимает её первой (docs/rules/exit-teardown-order.md). Клетка
     * {@code B6.10}, которой нужен живой хвост на ТЕРМИНАЛЬНОЙ сделке,
     * этой тропой не ставится и здесь не написана.
     *
     * <p><b>Все входы расчёта поставлены тропами ящика:</b> проекции —
     * тиком синка, ставка комиссии — тиком её синка, числа риск-аппетита —
     * своей поверхностью, сделка — тиком отбора входа, снимок средств —
     * первым тиком сопровождения. Нога создаётся вторым тиком и на
     * площадку ещё не уходила: решение о заявке принимается раньше её
     * отправки.
     */
    private void openDealWithLiveLeg() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("5", "10", "4")).status())
                .isEqualTo(200);
        assignLeverage(ACCOUNT, INSTRUMENT);
        connector.answers(feeRatePath(ACCOUNT), Feed.array(Feed.tradeFeeRate()));
        tick(Tick.TRADE_FEE_RATES);
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(liveOrderStatus()).isEqualTo("CREATED");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Счёт под сворачиванием, чьё снятие риска НЕ подтвердилось: нога
     * сделки осталась живой.
     *
     * <p><b>Неподтверждённость ставится молчанием площадки, а не
     * подменой:</b> стаб коннектора снятия ноги и перечитки факта не
     * отвечает, ограниченный цикл исчерпывает бюджет попыток, и нога
     * остаётся в базе живой — ровно то состояние, ради выхода из которого
     * доведение недоделанного и заведено
     * (docs/rules/manual-halt.md §«Снятие: что во что переходит и при
     * каком предусловии»).
     */
    private void standUnconfirmedTeardown() {
        openDealWithLiveLeg();
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(liveOrderStatus()).isEqualTo("CREATED");
    }

    /**
     * Срез счёта с живым риском по инструменту, которого в модели нет:
     * признак жёсткой биржевой ступени, срабатывающий с первого
     * наблюдения.
     *
     * <p><b>Срез обязан быть ПОЛНЫМ</b>: на неполном проходе детекторы
     * молчат гейтом неполноты, и клетка наблюдала бы тишину гейта вместо
     * поглощения.
     */
    private void standForeignInstrumentRisk() {
        connector.answers(positionsPath(ACCOUNT), Feed.array(Feed.livePosition("ex-foreign-1",
                FOREIGN_INSTRUMENT, "1", LAST_PRICE, POSITION_MOMENT)));
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
    }

    /**
     * Счёт ВНЕ рабочего состояния и без стоящей ступени своего радиуса:
     * единственное множество, на котором мягкая постановка отказывает.
     *
     * <p><b>Предусловие кейса {@code B6.5} названо иначе — «счёт стои́т в
     * `TRADE_BLOCKED`», — и такого отказа оно не даёт</b> (находка захода).
     * Дом разводит два случая: мягкий запрос на объекте не в рабочем
     * состоянии <b>и без стоящей ступени своего радиуса</b> — отказ при
     * запуске; мягкий запрос на объекте под жёсткой ступенью —
     * <b>поглощается</b> (docs/rules/manual-halt.md §«Идемпотентность
     * наследуется, а не обходится»; docs/rules/exchange-hold.md §«Границы и
     * эскалация»). Клетка проверяет обе стороны дома, а не ту редакцию
     * предусловия, при которой отказа не бывает.
     *
     * <p>Статус счёта приезжает из реестра его владельца, и отключённый
     * счёт заводится тем же тиком синка, что и рабочий.
     */
    private void provisionClosedAccount() {
        auth.answers(PEER_ACCOUNTS, Feed.array(Feed.account(ACCOUNT, TENANT, "DEMO", "CLOSED")));
        marketData.answers(PEER_INSTRUMENTS, Feed.emptyArray());
        tick(Tick.REGISTRY_PROJECTIONS);
        assertThat(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("status"))
                .isEqualTo("CLOSED");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Полная постановка с ОТВЕТОМ поверхности: клетки группы утверждают о
     * числе ответа, а общий помощник ящика ждёт только конца асинхронного
     * доведения.
     *
     * @param accountInternalId    идентичность счёта
     * @param instrumentInternalId инструмент радиуса; пусто — радиус счёта
     * @return ответ поверхности на запуск
     */
    private Answer fullHaltAnswer(String accountInternalId, String instrumentInternalId) {
        Integer mark = AppLog.mark();
        Answer answer = isNull(instrumentInternalId)
                ? post(HALTS, Bodies.halt("FULL", accountInternalId))
                : post(HALTS, Bodies.halt("FULL", accountInternalId, instrumentInternalId));
        awaitLog(mark, "Holder full halt finished accountInternalId=" + accountInternalId);
        return answer;
    }

    /** Снятие названной ступени счётного радиуса. */
    private Answer clear(String haltClass, String accountInternalId) {
        return post(HALT_CLEARANCES, Bodies.halt(haltClass, accountInternalId));
    }

    /** Статус единственной ноги входа, как его видит база. */
    private String liveOrderStatus() {
        return String.valueOf(rows.all("orders").getFirst().get("status"));
    }

    /** Единственная сделка. */
    private Map<String, Object> dealRow() {
        return rows.all("deals").getFirst();
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

    /** Путь приватного чтения ставок комиссии у коннектора. */
    private String feeRatePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/trade-fee-rates";
    }

    /** Путь снятия обычной заявки: первый ход снятия живого риска. */
    private String cancellationPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/cancellations";
    }

    private String closurePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closures";
    }

    /** Живой эпизод позиции по инструменту: подтверждение закрытия экспозиции. */
    private String positionPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/instrument";
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

    /** Ступень счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("safety_rung"));
    }

    /** Ступень пары «счёт, инструмент», как её видит база. */
    private String pairRung(String instrumentInternalId) {
        return String.valueOf(rows.select("select * from account_instrument_states"
                        + " where exchange_account_id = ? and instrument_id = ?",
                accountId(ACCOUNT), instrumentId(instrumentInternalId)).getFirst().get("safety_rung"));
    }

    /** Машинные коды заведённых отчётов в порядке записи. */
    private List<String> codesOfReports() {
        return rows.allOrderedBy("anomaly_reports", "id").stream()
                .map(row -> String.valueOf(row.get("code")))
                .toList();
    }

    /** Классы событий строк outbox в порядке записи. */
    private List<String> eventTypes() {
        return rows.all("outbox_events").stream()
                .map(row -> String.valueOf(row.get("event_type")))
                .toList();
    }
}
