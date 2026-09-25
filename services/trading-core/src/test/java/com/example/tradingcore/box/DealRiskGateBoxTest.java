package com.example.tradingcore.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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
class DealRiskGateBoxTest extends SharedLiveDealBox {

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

    /** Код отказа преконтроля по незаданным числам риск-аппетита. */
    private static final String APPETITE_NOT_CONFIGURED = "RISK_APPETITE_NOT_CONFIGURED";

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

    /** Ключ действия добора на сопровождении. */
    private static final String ADD_ON = "add-on";

    /** Ключ уровня фиксации прибыли на сопровождении. */
    private static final String TAKE_PROFIT = "take-profit";

    /** Ключ первой отдельной защиты: её ставит первый шаг, снимает второй. */
    private static final String FIRST_STOP = "first-stop";

    /** Ключ снятия первой защиты во втором шаге. */
    private static final String REMOVE_FIRST_STOP = "remove-first-stop";

    /** Ключ второй отдельной защиты: она сменяет первую. */
    private static final String SECOND_STOP = "second-stop";

    /** Дистанция отдельной защиты, процент якоря: дальше встроенной. */
    private static final String STOP_PERCENTS = "3";

    /** Дистанция уровня фиксации прибыли, процент якоря. */
    private static final String PROFIT_PERCENTS = "5";

    /** Та же дистанция со знаком, уводящим уровень на убыточную сторону якоря. */
    private static final String WRONG_SIDE_PROFIT_PERCENTS = "-5";

    /**
     * Множитель сделочного бюджета, при котором вход в него укладывается,
     * а вход с добором — нет.
     */
    private static final String EXHAUSTED_BUDGET = "0.3";

    /** Потолок проходов сопровождения, за который пакет из четырёх действий обязан исчерпаться. */
    private static final Integer PACKAGE_PASS_LIMIT = 30;

    /** Код блок-сета стоящей ступени. */
    private static final String SAFETY_HOLD_CODE = "INSTRUMENT_SAFETY_HOLD";

    /** След реакции преконтроля: блок-сет ступени, действие пропущено. */
    private static final String SAFETY_HOLD_SKIPPED = "type=SKIP_ACTION code=" + SAFETY_HOLD_CODE;

    /** След реакции преконтроля: исчерпанный бюджет сделки, действие пропущено. */
    private static final String CUMULATIVE_SKIPPED = "type=SKIP_ACTION code=RISK_PER_DEAL_CUMULATIVE_EXCEEDED";

    /** След реакции преконтроля: код вне карв-аута при живом риске — увод в ошибку. */
    private static final String WRONG_SIDE_ESCALATED = "type=MOVE_DEAL_TO_ERROR code=TAKE_PROFIT_INVALID_SIDE";

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
    @DisplayName("B4.2 — преконтроль не спрашивается у добычи, выхода, дочистки и safety")
    void thePrecheckIsNotAskedOnTheExitPath() {
        openLiveDeal();
        // Числа риск-аппетита сняты ПОСЛЕ входа: пустая строка чисел есть
        // отказ всякого действия, которое преконтроль спрашивает, и выход
        // проходит ровно тогда, когда его не спрашивают.
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("null", "null", "null")).status())
                .isEqualTo(200);
        standExchangeFollowingCommands("-5");
        Integer mark = AppLog.mark();

        exitByDeletion(workingDefinition());
        passesUntilDealTerminal();

        // Выход исполнен целиком: позиция закрыта, защита снята, сделка в
        // штатном терминале.
        assertThat(connector.requests(closurePath(ACCOUNT))).hasSize(1);
        assertThat(connector.requests(attachedCancellationPath(ACCOUNT))).hasSize(1);
        assertThat(dealStatus()).isEqualTo("CLOSED");
        // Отказа по незаданным числам на этой тропе нет ни в одном
        // носителе: ни в строках исполнения, ни в журнале происшествий, ни
        // в журнале приложения.
        assertThat(rows.all("deal_strategy_action_states").toString()).doesNotContain(APPETITE_NOT_CONFIGURED);
        assertThat(rows.all("deal_system_action_states").toString()).doesNotContain(APPETITE_NOT_CONFIGURED);
        assertThat(rows.count("anomaly_reports")).isZero();
        assertThat(AppLog.since(mark)).doesNotContain(APPETITE_NOT_CONFIGURED);
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
    @DisplayName("B4.8 — неполный граф — не вердикт риск-политики")
    void theIncompleteGraphIsNotARiskPolicyVerdict() {
        // Определение — то же, что у B4.4: на полном графе его вход даёт
        // бессрочный вердикт и терминал транша с причиной RISK_CONTROL.
        // Разница с B4.4 одна — граф, и потому клетка различает «риск не
        // позволил» от «контекст не загрузился» на единственной стадии, где
        // схема реакции вообще ставит эту причину.
        assignRiskAppetite();
        openGatedDeal(tightStopDefinition());
        // Граф неполон по конъюнкту ног: нижняя граница окна линковки
        // движений стоит, а ни одной ноги у сделки нет
        // (docs/spec/deal-context-load.json, graphComplete). Сочетание
        // ПРЯМОЙ записью, и довод в том, что писателя у него нет: граница
        // пишется наблюдением ноги, а ноги сервис не удаляет — неполнота
        // графа есть отказ предъявления, а не состояние, которое тропа
        // производит.
        rows.put("update deals set bills_window_begin = now()");

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(dealStatus()).isEqualTo("ERROR");
        assertThat(trancheRow().get("close_reason")).isNotEqualTo("RISK_CONTROL");
        assertThat(trancheStatus()).isNotEqualTo("CLOSED");
        assertThat(rows.count("orders")).isZero();
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
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

    @Test
    @DisplayName("B4.6 — код карв-аута на стадии с живым риском в аварию не уводит")
    void theCarveOutCodeWithLiveRiskDoesNotLeadToTheEmergency() {
        Strategy definition = Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, PHASE,
                List.of(Definitions.managingStep(StrategyStepType.GRID_ENTRY, Definitions.addOnEntry(ADD_ON))));
        // Бюджет сделки ЗАНИЖЕН так, что вход в него укладывается, а добор
        // — уже нет: риск акта входа около 210 при потолке 1000 × 0.3 = 300,
        // вход с добором — около 420. Прочие потолки остаются штатными, и
        // вердикт несёт ровно один код.
        definition.getDetails().getFirst().setCumulativeRiskPerDealMultiplier(new BigDecimal(EXHAUSTED_BUDGET));
        openLiveDeal(definition);
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);
        Integer refusalsAfterFirst = occurrences(AppLog.since(mark), CUMULATIVE_SKIPPED);
        tick(Tick.DEAL_ORCHESTRATOR);

        // Действие отвергнуто кодом исчерпанного бюджета сделки, реакция —
        // пропуск действия, а не увод в ошибку.
        assertThat(refusalsAfterFirst).isEqualTo(1);
        // Следующий тик пробует снова: отказ повторился, а не погас.
        assertThat(occurrences(AppLog.since(mark), CUMULATIVE_SKIPPED)).isEqualTo(2);
        // Действие не исполнено: второй ноги нет, к площадке не ушло ничего.
        assertThat(ordersOfDeal()).hasSize(1);
        assertThat(commandCalls()).isEmpty();
        // Сделка и транш в своих статусах, живой риск не снимался, ступеней
        // не поднято ни одного радиуса.
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(trancheStatus()).isEqualTo("MANAGING");
        assertThat(accountRung()).isEqualTo(NO_ACCOUNT_RUNG);
        assertThat(pairRung()).isEqualTo(NO_PAIR_RUNG);
    }

    @Test
    @DisplayName("B4.7 — код вне карв-аута на стадии с живым риском уводит сделку в ошибку")
    void theCodeOutsideTheCarveOutWithLiveRiskLeadsToError() {
        // Код рассогласования учёта средств (BALANCE_INVALID) на стадии с
        // живым риском недостижим: база риска заморожена на сделке при
        // входе (DealContext#riskBase). Предмет клетки — реакция на код ВНЕ
        // карв-аута, и она у всех таких кодов одна; вход — уровень фиксации
        // прибыли на убыточной стороне якоря.
        openLiveDeal(Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, PHASE,
                List.of(Definitions.managingStep(StrategyStepType.PARTIAL_EXIT,
                        Definitions.takeProfitAlgo(TAKE_PROFIT, WRONG_SIDE_PROFIT_PERCENTS)))));
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(AppLog.since(mark)).contains(WRONG_SIDE_ESCALATED);
        // Сделка — в ошибке, и причина остановки ПУСТА: ребро решением
        // обработчика её не пишет.
        assertThat(dealStatus()).isEqualTo("ERROR");
        assertThat(dealRow().get("shutdown_reason")).isNull();
        // Действие не исполнено: условной заявки у площадки не ставилось.
        assertThat(connector.requests(algoPlacementPath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B4.10 (добор) — стоящая мягкая ступень пары блокирует создание риска")
    void theStandingPairRungBlocksTheAddOn() {
        openLiveDeal(Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, PHASE,
                        List.of(Definitions.managingStep(StrategyStepType.GRID_ENTRY,
                                Definitions.addOnEntry(ADD_ON)))),
                this::standPairEntryBlock);
        Integer mark = AppLog.mark();

        ticks(Tick.DEAL_ORCHESTRATOR, 2);

        // Добор отвергнут кодом стоящей ступени пары, и реакция — пропуск
        // действия: сделка остаётся в статусе.
        assertThat(AppLog.since(mark)).contains(SAFETY_HOLD_SKIPPED);
        assertThat(ordersOfDeal()).hasSize(1);
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(trancheStatus()).isEqualTo("MANAGING");
        assertThat(pairRung()).isEqualTo(ENTRY_BLOCKED);
    }

    @Test
    @DisplayName("B4.10 (фиксация прибыли) — стоящая мягкая ступень пары фиксации прибыли не блокирует")
    void theStandingPairRungDoesNotBlockTheTakeProfit() {
        openLiveDeal(Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, PHASE,
                        List.of(Definitions.managingStep(StrategyStepType.PARTIAL_EXIT,
                                Definitions.takeProfitAlgo(TAKE_PROFIT, PROFIT_PERCENTS)))),
                this::standPairEntryBlock);
        standAlgoOrdersFollowingCommands(1);
        Integer mark = AppLog.mark();

        passesUntil(() -> connector.count(algoPlacementPath(ACCOUNT)) > 0);

        // Постановка уровня фиксации прибыли прошла: риска она не создаёт и
        // контроля не ослабляет, и блок-сет мягкой ступени её не накрывает.
        assertThat(algoOrdersOfDeal()).hasSize(1);
        assertThat(algoOrdersOfDeal().getFirst().get("condition_type")).isEqualTo("TAKE_PROFIT");
        assertThat(AppLog.since(mark)).doesNotContain(SAFETY_HOLD_CODE);
        assertThat(pairRung()).isEqualTo(ENTRY_BLOCKED);
        assertThat(dealStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B4.12 — за проход исполняется одно действие пакета шага")
    void onePackageActionRunsPerPass() {
        List<List<String>> commandsByPass = runTwoStepPackage();
        PeerStub.all().forEach(PeerStub::forgetRequests);
        ticks(Tick.DEAL_ORCHESTRATOR, 2);

        // На каждом проходе к площадке уходит не больше ОДНОЙ команды.
        assertThat(commandsByPass).allMatch(commands -> commands.size() <= 1);
        // Порядок: три постановки, снятие — последним, хотя объявлено
        // первым; устанавливающие защиту идут раньше снимающих.
        assertThat(commandsByPass.stream().flatMap(List::stream).toList()).containsExactly(
                algoPlacementPath(ACCOUNT), algoPlacementPath(ACCOUNT), algoPlacementPath(ACCOUNT),
                algoCancellationPath(ACCOUNT));
        List<Map<String, Object>> algos = algoOrdersOfDeal();
        assertThat(algos).extracting(row -> row.get("condition_type"))
                .containsExactly("STOP_LOSS", "STOP_LOSS", "TAKE_PROFIT");
        // Снимающее адресовало ПЕРВУЮ защиту, а вторая им не тронута: оно
        // ушло после устанавливающего, и окна без защиты не было.
        assertThat(algos.getFirst().get("close_reason")).isEqualTo("CANCELED_BY_STRATEGY");
        assertThat(algos.get(1).get("close_reason")).isNull();
        // После третьего действия второго шага команд нет.
        assertThat(commandCalls()).isEmpty();
    }

    @Test
    @DisplayName("B4.12 (исчерпание) — после третьего действия пакет исчерпан")
    void thePackageIsExhaustedAfterItsThirdAction() {
        runTwoStepPackage();
        ticks(Tick.DEAL_ORCHESTRATOR, 2);

        // Пакет исчерпан — каждое его действие доведено до завершения, и
        // снятая защита подтверждена снятой фактом площадки
        // (docs/rules/ack-not-runtime-truth.md).
        assertThat(packageExhausted())
                .as("B4.12: пакет не исчерпан; строки исполнения %s", strategyActionStates())
                .isTrue();
        assertThat(algoOrdersOfDeal().getFirst().get("status")).isEqualTo("CANCELED");
    }

    /**
     * Живая сделка с пакетом шага сопровождения и проходы до его снимающего
     * действия.
     *
     * <p>Первый шаг ставит защиту, которую снимает второй; второй несёт три
     * исполнимых действия, и снимающее объявлено ПЕРВЫМ — иначе порядок
     * исполнения совпадал бы с порядком объявления, и клетка не отличала бы
     * упорядочивание от его отсутствия.
     *
     * @return команды площадке, ушедшие на каждом проходе, по проходам
     */
    private List<List<String>> runTwoStepPackage() {
        openLiveDeal(Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, PHASE, List.of(
                Definitions.managingStep(StrategyStepType.MAIN_PROTECTION,
                        Definitions.stopLossAlgo(FIRST_STOP, STOP_PERCENTS)),
                Definitions.managingStep(StrategyStepType.PROTECTION_ADJUSTMENT,
                        Definitions.cancelStopLossAlgo(REMOVE_FIRST_STOP, FIRST_STOP),
                        Definitions.stopLossAlgo(SECOND_STOP, STOP_PERCENTS),
                        Definitions.takeProfitAlgo(TAKE_PROFIT, PROFIT_PERCENTS)))));
        standAlgoOrdersFollowingCommands(3);
        List<List<String>> commandsByPass = new ArrayList<>();
        for (int pass = 0; pass < PACKAGE_PASS_LIMIT && isFalse(cancellationSent(commandsByPass)); pass++) {
            PeerStub.all().forEach(PeerStub::forgetRequests);
            tick(Tick.DEAL_ORCHESTRATOR);
            commandsByPass.add(commandCalls());
        }
        return commandsByPass;
    }

    /** Ушла ли уже команда снятия условной заявки. */
    private Boolean cancellationSent(List<List<String>> commandsByPass) {
        return commandsByPass.stream().flatMap(List::stream)
                .anyMatch(path -> Objects.equals(algoCancellationPath(ACCOUNT), path));
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

    /** Ставит мягкую ступень пары ручной поверхностью. */
    private void standPairEntryBlock() {
        assertThat(post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);
    }

    /**
     * Пакет второго шага исчерпан: строки всех четырёх объявленных
     * действий сопровождения доведены до завершения.
     */
    private Boolean packageExhausted() {
        List<Map<String, Object>> managing = rows.select("select s.* from deal_strategy_action_states s"
                + " join strategy_actions a on a.id = s.strategy_action_id where a.key in (?, ?, ?, ?)",
                FIRST_STOP, REMOVE_FIRST_STOP, SECOND_STOP, TAKE_PROFIT);
        return managing.size() == 4
                && managing.stream().allMatch(row -> Objects.equals("COMPLETED", row.get("status")));
    }

    /** Сколько раз фрагмент встречается в тексте журнала. */
    private Integer occurrences(String text, String fragment) {
        return text.split(Pattern.quote(fragment), -1).length - 1;
    }

    /** Строки исполнения ОБЪЯВЛЕННЫХ действий; системные лежат своей таблицей. */
    private List<Map<String, Object>> strategyActionStates() {
        return rows.all("deal_strategy_action_states");
    }

    /** Числовой ключ объявленного действия по его авторскому ключу. */
    private Object actionId(String actionKey) {
        return rows.row("strategy_actions", "key", actionKey).get("id");
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
