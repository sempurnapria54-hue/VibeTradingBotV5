package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B4} — риск-гейт: когда спрашивается и что делает вердикт.
 *
 * <p><b>Предмет группы — МОМЕНТ обращения к преконтролю и РЕАКЦИЯ на его
 * вердикт, а не сами неравенства.</b> Какое число с каким сравнивается —
 * предмет уровня 2 ({@code trading-core-risk}); здесь наблюдается, на
 * каком действии преконтроль спрашивается, чем отвечает проход на
 * бессрочный, временный и отложенный вердикт и что от этого меняется в
 * базе (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Тропа до преконтроля — та же, что у группы команд</b>
 * ({@link #openGatedDeal}): проекции тиком синка, ставка комиссии своим
 * тиком, сделка с траншем тиком отбора входа, снимок средств первым тиком
 * сопровождения. Прямой записи в предусловиях группы нет ни одной.
 *
 * <p><b>Вердикт добывается ОПЕРАНДОМ, а не подменой бина.</b> Бессрочный
 * даёт дистанция встроенного стопа внутри round-trip комиссии, временный —
 * стоящая мягкая ступень пары, отложенный — возраст снимка средств. Все
 * три операнда — вход ящика, и ни один не требует заглянуть внутрь.
 *
 * <p><b>Бессрочность вердикта есть КОНЪЮНКЦИЯ по его кодам</b>
 * (docs/components/RiskBlockResolver.md), поэтому занижением потолка риска
 * бессрочный вердикт не добывается: потолки связаны множителями, и
 * заниженный поактный роняет вместе с бессрочным
 * {@code RISK_PER_ACTION_EXCEEDED} временный
 * {@code RISK_PER_DEAL_CUMULATIVE_EXCEEDED}. Дистанция стопа — единственный
 * операнд группы, дающий бессрочный вердикт ОДНИМ кодом.
 *
 * <p><b>Клетки с меткой {@code debt} красны ПО ПОСТРОЕНИЮ</b>: их ожидание
 * взято из дома, который дерево кода на этой тропе не исполняет, и
 * ослаблять его под текущий факт значило бы закрепить дефект
 * (.claude/tests/cases/trading-core.md §«Ожидание берётся из дома, даже
 * когда сегодня оно не исполнено»).
 */
class DealRiskGateBoxTest extends SharedTradingCoreBox {

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-RISK";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /**
     * Последняя цена момента группы.
     *
     * <p><b>Тысяча, а не сотня соседней группы, и это операнд.</b> Пол
     * дистанции стопа равен round-trip комиссии — доле якоря, — а шаг цены
     * инструмента абсолютен (0.1): чтобы дистанция МЕНЬШЕ пола вообще
     * выражалась в шагах цены, якорь обязан быть выше отношения шага к
     * этой доле. При сотне ближайшая ненулевая дистанция равна полу, и
     * бессрочный вердикт не достигается ни одним объявлением стратегии.
     */
    private static final String LAST_PRICE = "1000";

    /**
     * Дистанция встроенного стопа ВНУТРИ round-trip комиссии: 0.5 против
     * пола около 1.0 при якоре в тысячу.
     */
    private static final String STOP_INSIDE_FEE_FLOOR = "0.05";

    /** Фаза рынка, которую требует условие входа определений группы. */
    private static final MarketPhase.Type PHASE = MarketPhase.Type.BULL_TREND;

    /** Возраст снимка средств, заведомо больший толерантности прохода (2m). */
    private static final Duration STALE_BALANCE_AGE = Duration.ofMinutes(10);

    /** Отсутствие ступени пары: рабочее состояние. */
    private static final String NO_PAIR_RUNG = "ACTIVE";

    /** Мягкая ступень пары «счёт, инструмент». */
    private static final String ENTRY_BLOCKED = "ENTRY_BLOCKED";

    /** Отсутствие ступени счёта: рабочее состояние. */
    private static final String NO_ACCOUNT_RUNG = "ACTIVE";

    /**
     * Свинг-минимум ВЫШЕ якоря входа: им защитный уровень уезжает на
     * прибыльную сторону.
     *
     * <p>Буфер структурного стопа — процент базы (1%), поэтому уровень
     * равен 1100 − 11 = 1089 и лежит выше якоря в тысячу: worst-case
     * выхода у длинной позиции не существует, и сайзинг отказывает
     * бессрочным {@code STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING}.
     */
    private static final String SWING_LOW_ABOVE_ANCHOR = "1100";

    /**
     * Свинг-минимум НИЖЕ якоря: тот же расчёт даёт 950 − 9.5 = 940.5, то
     * есть уровень на убыточной стороне, и то же действие исполняется.
     */
    private static final String SWING_LOW_BELOW_ANCHOR = "950";

    @Test
    @DisplayName("B4.1 — преконтроль спрашивается у создающего риск действия")
    void thePrecheckIsAskedAtTheRiskCreatingActionAndReadsItsThreeOperands() {
        assignRiskAppetite();
        openGatedDeal(workingDefinition());

        tick(Tick.DEAL_ORCHESTRATOR);

        // Разрешающий вердикт плана не порождает, и действие доходит до
        // команды заведения ноги (docs/components/ActionRiskGate.md).
        assertThat(rows.count("orders")).isEqualTo(1L);
        assertThat(strategyActionStates()).hasSize(1);
    }

    @Test
    @DisplayName("B4.1 (контроль) — без ставки комиссии то же действие не исполняется")
    void theSameRiskCreatingActionDoesNotRunWithoutTheFeeRateOperand() {
        // Три операнда преконтроля снимаются по одному, и каждый снимает
        // СВОЯ клетка: снимок средств — B4.3, числа тенанта — B4.9, ставка
        // комиссии — эта. Три клетки вместе и закрывают вторую половину
        // ожидания B4.1 («без ставки, снимка средств и чисел действие не
        // исполняется»); свести их в одну значило бы трижды опустошать
        // базу внутри клетки, то есть прогонять три клетки под одним
        // именем.
        assignRiskAppetite();
        openGatedDeal(workingDefinition(), NO_FEE_RATE, FRESH_BALANCE);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.count("orders")).isZero();
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B4.14 — без назначенного плеча то же действие не исполняется")
    void theSameRiskCreatingActionDoesNotRunWithoutTheAssignedLeverage() {
        // Четвёртый операнд снимается своей клеткой, как три прежних у
        // B4.1 (контроль): плечо назначено предусловием и снято назначением
        // пустого тела — той же поверхностью, которой его ставит держатель.
        assignRiskAppetite();
        openGatedDeal(workingDefinition());
        assertThat(put(PAIR_SETTINGS + "/" + ACCOUNT + "/" + INSTRUMENT, "{}").status()).isEqualTo(200);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.count("orders")).isZero();
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B4.3 — несвежий снимок средств заказывает добычу, а не отказ")
    void theStaleBalanceSnapshotOrdersAFetchInsteadOfRefusingTheAction() {
        assignRiskAppetite();
        openGatedDeal(workingDefinition(), WITH_FEE_RATE, STALE_BALANCE);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Преконтроль на этой итерации не вызывался: строка исполнения
        // объявленного действия заводится ДО него, и её нет ни одной.
        assertThat(strategyActionStates()).isEmpty();
        assertThat(rows.count("orders")).isEqualTo(0L);
        // Ушла ДОБЫЧА: снимок средств заказан звеном СИСТЕМНОГО действия,
        // и лежит оно своей таблицей — отсюда и различение «преконтроль не
        // звался» от «прохода не было».
        assertThat(connector.requests(balancePath(ACCOUNT))).isNotEmpty();
        assertThat(rows.rowsWhere("deal_system_action_states", "system_action_type",
                "REFRESH_DEAL_CONTEXT_ACTION")).isNotEmpty();
        // Отказа нет: транш остаётся в предвходовой проверке и ждёт снимка.
        assertThat(trancheStatus()).isEqualTo("PRECHECK");

        connector.answers(balancePath(ACCOUNT), balanceBody(OffsetDateTime.now(ZoneOffset.UTC)));
        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.DEAL_ORCHESTRATOR);

        // На свежем снимке действие исполняется. Тиков два, потому что
        // добычу и работу проход не совмещает: первый приносит снимок,
        // второй его читает.
        assertThat(rows.count("orders")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B4.4 — бессрочный вердикт на `PRECHECK` закрывает транш, а не сделку в аварию")
    void thePermanentVerdictBeforeLiveRiskClosesTheTrancheAndNotTheDealIntoError() {
        assignRiskAppetite();
        openGatedDeal(tightStopDefinition());

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(trancheStatus()).isEqualTo("CLOSED");
        assertThat(trancheRow().get("close_reason")).isEqualTo("RISK_CONTROL");
        assertThat(dealStatus()).isNotEqualTo("ERROR");
        assertThat(rows.count("orders")).isEqualTo(0L);
        // Закрылись все транши — сделка уходит в терминал, и проверка
        // «операций не было» даёт ноль, а не пустоту
        // (docs/rules/trading-constraints.md §«Закрытие без входа: ноль
        // как результат проверки»).
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(dealRow().get("result_profit")).isNotNull();
        assertThat(new BigDecimal(String.valueOf(dealRow().get("result_profit"))).signum()).isZero();
    }

    @Test
    @Tag("debt")
    @DisplayName("B4.5 — временный вердикт действие откладывает, а транш оставляет ждать")
    void theTemporaryVerdictDefersTheActionAndLeavesTheTrancheWaiting() {
        assignRiskAppetite();
        openGatedDeal(workingDefinition());
        // Мягкая ступень ПАРЫ ставится после отбора входа: под стоящей
        // ступенью сканер пару пропускает вовсе (клетка B1.6).
        assertThat(post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);
        assertThat(pairRung()).isEqualTo(ENTRY_BLOCKED);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.count("orders")).isEqualTo(0L);
        // Красно по построению: временный вердикт доносится до обработчика
        // ПУСТЫМ исходом прохода (`SKIP_ACTION` → `TrancheTransition.stay`),
        // а пустой исход предвходовая проверка читает как ложное условие
        // входа и закрывает транш `ENTRY_CONDITION_EXPIRED`. Дом говорит
        // обратное — «действие не исполняется, транш ждёт следующего
        // прохода» (docs/processes/risk-evaluation.md §«Реакция на
        // результат»), и ожидание под факт не ослабляется (находка F1
        // захода).
        assertThat(trancheStatus()).isEqualTo("PRECHECK");
        assertThat(trancheRow().get("close_reason")).isNull();
        assertThat(dealStatus()).isEqualTo("ACTIVE");

        // Причина снята — то же действие исполняется следующим проходом:
        // бюджет, занятый временно, возвращается, и уровень сетки не
        // потерян навсегда.
        assertThat(post(HALT_CLEARANCES, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);
        assertThat(pairRung()).isEqualTo(NO_PAIR_RUNG);
        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.count("orders")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B4.9 — незаданные числа риск-аппетита отказывают на действии, а не на старте")
    void theUnassignedRiskAppetiteRefusesAtTheActionAndNotAtStartup() {
        // Контекст поднялся при пустых числах — стартового обхода их нет
        // вовсе, и поверхность отвечает.
        assertThat(get(HEALTH).status()).isEqualTo(200);
        openGatedDeal(workingDefinition());
        assertThat(rows.row("tenant_risk_appetites", "tenant_internal_id", TENANT)
                .get("global_consecutive_loss_limit")).isNull();
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Отказ приходит НА ДЕЙСТВИИ и называет, какое именно число пусто.
        // Код — `LOSS_LIMIT_NOT_CONFIGURED`, а не названный кейсом
        // `RISK_APPETITE_NOT_CONFIGURED`: пустая строка чисел нарушает
        // ПЕРВУЮ из двух охран порядка проверок валидатора, и имя кода
        // поправлено по коду (оба кода — временные и оба в карв-ауте, то
        // есть род реакции у них один).
        String written = AppLog.since(mark);
        assertThat(written).contains("Risk precheck blocked action");
        assertThat(written).contains("LOSS_LIMIT_NOT_CONFIGURED");
        assertThat(written).contains("globalConsecutiveLossLimit is not assigned for tenant " + TENANT);
        assertThat(rows.count("orders")).isEqualTo(0L);
        // Сделка в аварию не уходит: код в карв-ауте исчерпанного бюджета.
        assertThat(dealStatus()).isNotEqualTo("ERROR");

        // ВТОРАЯ охрана того же семейства (`RISK_APPETITE_NOT_CONFIGURED`
        // на незаданном проценте) этой клеткой не наблюдается, и это не
        // пропуск: дойти до неё можно только назначив первое число, а к
        // тому моменту транш уже закрыт временным вердиктом первой охраны
        // — находка F1 захода. Возврат — по её закрытию.
    }

    @Test
    @Tag("debt")
    @DisplayName("B4.13 — отказ расчёта по стороне уровня — отказ шага, не авария")
    void theRefusalByTheSideOfTheStopLevelFailsTheStepAndNotTheDeal() {
        assignRiskAppetite();
        openGatedDeal(structureStopDefinition(), WITH_FEE_RATE, FRESH_BALANCE,
                Feed.featuresWithStructure(PHASE.name(), LAST_PRICE, SWING_LOW_ABOVE_ANCHOR));
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Шаг НЕ исполнен: команды к площадке не ушло, ноги не заведено.
        assertThat(rows.count("orders")).isZero();
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
        // Строка исполнения остаётся ЗАПЛАНИРОВАННОЙ: отказ по стороне
        // уровня в учёт не попадает вовсе, и повтор поэтому придёт
        // следующим проходом сам (docs/processes/risk-evaluation.md
        // §«Отказ расчёта по стороне уровня — отказ шага, не авария»).
        // Отличать «отказ учтён» от «отказ пропущен» здесь может только
        // строка: статус `FAILED`, непустой счёт попыток и записанная
        // ошибка означали бы общий разбор постоянной ошибки, то есть
        // отсутствие карв-аута. Счёт попыток у нетронутой строки ПУСТ, а
        // не равен нулю: первый его writer — сам учёт, и ноль означал бы,
        // что учёт строку открывал.
        assertThat(strategyActionStates()).hasSize(1);
        assertThat(strategyActionStates().getFirst().get("status")).isEqualTo("PLANNED");
        assertThat(strategyActionStates().getFirst().get("attempt_count")).isNull();
        assertThat(strategyActionStates().getFirst().get("last_error")).isNull();
        // Отказ виден: ветвь без строки и без поверхности несома журналом.
        assertThat(AppLog.since(mark)).contains("Calculation refused by control, step not executed");
        // Ступеней не поднято НИ ОДНОГО радиуса: контроль сработал, а не
        // защита.
        assertThat(pairRung()).isEqualTo(NO_PAIR_RUNG);
        assertThat(accountRung()).isEqualTo(NO_ACCOUNT_RUNG);
        // Красно по построению: транш встаёт `CLOSED` с причиной
        // `ENTRY_CONDITION_EXPIRED`, а сделка следующим проходом уходит в
        // терминал. Механизм тот же, что у клетки B4.5, но ПРОИЗВОДИТЕЛЬ
        // пустого исхода другой: там временный вердикт преконтроля, здесь
        // контролируемый отказ расчёта, не дошедший до преконтроля вовсе.
        // Дом говорит обратное — «шаг не исполняется, транш и сделка
        // остаются в своих статусах» (docs/processes/risk-evaluation.md
        // §«Отказ расчёта по стороне уровня — отказ шага, не авария»), и
        // ожидание под факт не ослабляется.
        assertThat(trancheStatus()).isEqualTo("PRECHECK");
        assertThat(trancheRow().get("close_reason")).isNull();
        assertThat(dealStatus()).isEqualTo("ACTIVE");

        // Повтор приходит следующим проходом, когда структура даёт
        // уровень на убыточной стороне: определение не правилось, сделка
        // не переоткрывалась — изменилась одна цена раскладки.
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithStructure(PHASE.name(), LAST_PRICE, SWING_LOW_BELOW_ANCHOR));
        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.count("orders")).isEqualTo(1L);
        assertThat(trancheStatus()).isEqualTo("ENTRY_SUBMITTED");
    }

    @Test
    @DisplayName("B4.11 — заблокированное действие шага останавливает остальные действия того же шага")
    void theBlockedActionHaltsTheRestOfItsStepPackage() {
        assignRiskAppetite();
        openGatedDeal(Definitions.withTwoActionEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT, PHASE));
        assertThat(post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);

        ticks(Tick.DEAL_ORCHESTRATOR, 3);

        // Пакет встал на ПЕРВОМ действии: строка заводится у всякого
        // начатого действия ДО преконтроля, поэтому две строки означали бы
        // «пакет пошёл дальше», а одна — «пакет остановился». Обгонять
        // начатое значило бы менять объявленный порядок пакета
        // (docs/components/StrategyActionOrchestrator.md).
        assertThat(strategyActionStates()).hasSize(1);
        assertThat(strategyActionStates().getFirst().get("strategy_action_id"))
                .isEqualTo(actionId("entry-order"));
        // Шаг остаётся НЕИСПОЛНЕННЫМ: ни одна его строка не дошла до цели,
        // и команд к площадке не ушло ни одной.
        assertThat(strategyActionStates().getFirst().get("target_entity_id")).isNull();
        assertThat(rows.count("orders")).isEqualTo(0L);
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /** Ставка комиссии синкается своим тиком. */
    private static final Boolean WITH_FEE_RATE = Boolean.TRUE;

    /** Ставка комиссии не синкается: её в навесе нет. */
    private static final Boolean NO_FEE_RATE = Boolean.FALSE;

    /** Снимок средств моментом прогона. */
    private static final Boolean FRESH_BALANCE = Boolean.TRUE;

    /** Снимок средств старше толерантности прохода. */
    private static final Boolean STALE_BALANCE = Boolean.FALSE;

    /** Определение, чей вход доходит до преконтроля и им разрешается. */
    private Strategy workingDefinition() {
        return Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT, PHASE);
    }

    /** Определение, чей стоп стои́т ближе round-trip комиссии. */
    private Strategy tightStopDefinition() {
        return Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT, PHASE,
                STOP_INSIDE_FEE_FLOOR);
    }

    /** Определение, чей стоп считается от рыночной структуры. */
    private Strategy structureStopDefinition() {
        return Definitions.withStructureStopEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT, PHASE);
    }

    /**
     * Сделка с траншем в предвходовой проверке, чей вход доходит до
     * преконтроля: штатное положение всех его операндов.
     *
     * @param definition определение, которым сделка заводится
     */
    private void openGatedDeal(Strategy definition) {
        openGatedDeal(definition, WITH_FEE_RATE, FRESH_BALANCE);
    }

    /**
     * То же предусловие с НАЗВАННЫМ положением двух операндов преконтроля:
     * ими клетка-контроль снимает по одному и смотрит, исполняется ли
     * действие.
     *
     * @param definition  определение, которым сделка заводится
     * @param withFeeRate синкать ли ставку комиссии своим тиком
     * @param freshBalance свеж ли снимок средств, который отдаёт коннектор
     */
    private void openGatedDeal(Strategy definition, Boolean withFeeRate, Boolean freshBalance) {
        openGatedDeal(definition, withFeeRate, freshBalance,
                Feed.featuresWithPrice(PHASE.name(), LAST_PRICE));
    }

    /**
     * То же предусловие с НАЗВАННОЙ связкой фич: ею подаются операнды,
     * которых штатная связка не несёт (раскладка структур).
     *
     * @param definition   определение, которым сделка заводится
     * @param withFeeRate  синкать ли ставку комиссии своим тиком
     * @param freshBalance свеж ли снимок средств, который отдаёт коннектор
     * @param features     тело связки фич момента у владельца данных
     */
    private void openGatedDeal(Strategy definition, Boolean withFeeRate, Boolean freshBalance,
                               String features) {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        assignLeverage(ACCOUNT, INSTRUMENT);
        if (Boolean.TRUE.equals(withFeeRate)) {
            syncFeeRate();
        }
        marketData.answers(featuresPath(INSTRUMENT), features);
        connector.answers(balancePath(ACCOUNT), balanceBody(balanceMoment(freshBalance)));
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(definition);
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(1L);
        // Первый тик сопровождения снимает снимок средств и работы не
        // делает: предвходовая проверка обеспечивает его ДО работы.
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.count("orders")).isEqualTo(0L);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Момент снимка средств: возраст ставится В ДАННЫХ, часы не двигаются. */
    private OffsetDateTime balanceMoment(Boolean fresh) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return Boolean.TRUE.equals(fresh) ? now : now.minus(STALE_BALANCE_AGE);
    }

    /** Числа риск-аппетита тенанта: операнды преконтроля, своей поверхностью. */
    private void assignRiskAppetite() {
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("5", "10", "4")).status())
                .isEqualTo(200);
    }

    /** Ставка комиссии комиссионного уровня счёта: тиком её синка. */
    private void syncFeeRate() {
        connector.answers(feeRatePath(ACCOUNT), Feed.array(Feed.tradeFeeRate()));
        tick(Tick.TRADE_FEE_RATES);
        assertThat(rows.count("trade_fee_rates")).isEqualTo(1L);
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

    /** Путь размещения обычной заявки у коннектора. */
    private String placementPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders";
    }

    /** Строки исполнения ОБЪЯВЛЕННЫХ действий; системные лежат своей таблицей. */
    private List<Map<String, Object>> strategyActionStates() {
        return rows.all("deal_strategy_action_states");
    }

    /** Числовой ключ объявленного действия по его авторскому ключу. */
    private Object actionId(String actionKey) {
        return rows.row("strategy_actions", "key", actionKey).get("id");
    }

    /** Единственный транш сделки. */
    private Map<String, Object> trancheRow() {
        return rows.all("deal_tranches").getFirst();
    }

    /** Статус единственного транша. */
    private String trancheStatus() {
        return String.valueOf(trancheRow().get("status"));
    }

    /** Единственная сделка. */
    private Map<String, Object> dealRow() {
        return rows.all("deals").getFirst();
    }

    /** Статус единственной сделки. */
    private String dealStatus() {
        return String.valueOf(dealRow().get("status"));
    }

    /**
     * Ступень пары «счёт, инструмент», как её видит база.
     *
     * <p><b>Строки пары нет — ступени нет.</b> Рабочее состояние
     * выражается отсутствием строки, а не значением в ней, и клетка,
     * читающая колонку отсутствующей строки, получила бы пустоту вместо
     * ответа «ступень рабочая».
     */
    private String pairRung() {
        Map<String, Object> found = rows.row("account_instrument_states", "instrument_id",
                instrumentId(INSTRUMENT));
        return found.isEmpty() ? NO_PAIR_RUNG : String.valueOf(found.get("safety_rung"));
    }

    /** Ступень биржевого счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("safety_rung"));
    }

    /**
     * Снимок средств названным моментом.
     *
     * @param moment момент снимка у площадки
     */
    private String balanceBody(OffsetDateTime moment) {
        String at = moment.toString();
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
                """.formatted(at, at);
    }
}
