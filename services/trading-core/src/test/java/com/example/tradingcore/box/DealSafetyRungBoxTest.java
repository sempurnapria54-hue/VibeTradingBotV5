package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p><b>Живая экспозиция ставится общей сборкой</b> ({@link LiveDealBox}):
 * вход налит целиком, эпизод позиции жив, встроенная защита
 * материализована. Клетки, которым экспозиция нужна предметно, — состав
 * и порядок снятия риска, ограниченный цикл его подтверждения, — стоят на
 * ней; клетки, чей предмет ступень, отчёт и каскад, обходятся активной
 * сделкой без заявок ({@link #openActiveDeal}).
 *
 * <p><b>Площадка меняет ответ ПОСЛЕ команды, и это вход клетки.</b> Снятие
 * риска подтверждается перечиткой фактов (docs/components/KillSwitchExecutor.md
 * §Подтверждение), поэтому стаб отдаёт закрытую позицию и снятую защиту
 * ровно с того чтения, которое следует за командой
 * ({@link #standConfirmedTeardown}); стаб, не меняющий ответа, ставит
 * НЕподтверждение — предмет {@code B5.4}.
 *
 * <p><b>Сделка радиуса ставится тиком отбора входа</b>
 * ({@link #openActiveDeal}): каскаду нужна активная сделка счёта, и
 * наблюдается он её статусом и причиной остановки.
 */
class DealSafetyRungBoxTest extends SharedLiveDealBox {

    /** Причина снятия, которой ступень метит снимаемые сущности. */
    private static final String KILL_SWITCH = "KILL_SWITCH";

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-SAFE";

    /** Биржевой момент открытия чужого эпизода: половина его адреса. */
    private static final String POSITION_MOMENT = "2026-09-20T10:00:05Z";

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

    /** Код отчёта расхождения сверки результата сделки. */
    private static final String RECONCILIATION_MISMATCH = "PNL_RECONCILIATION_MISMATCH";

    /** Предел серии убыточных закрытий, назначаемый клетками о серии. */
    private static final String STREAK_LIMIT = "2";

    /** Код отчёта о достигнутом пределе серии. */
    private static final String LOSS_STREAK_LIMIT_REACHED = "LOSS_STREAK_LIMIT_REACHED";

    /** Реализованный результат эпизода: убыток. */
    private static final String LOSS = "-5";

    /** Реализованный результат эпизода: прибыль. */
    private static final String PROFIT = "5";

    /** Реализованный результат эпизода: ноль. */
    private static final String ZERO = "0";

    /** Машинный код хвостов заявок, не объяснимых живой сделкой. */
    private static final String ORPHAN_ORDERS = "INSTRUMENT_ORPHAN_ORDERS";

    /**
     * Клиентский идентификатор НАШЕЙ заявки: маркер контура впереди
     * ({@code InternalIdFactory#isOurs}).
     */
    private static final String OUR_CLIENT_ID = "vtbboxsafetyone";

    /** Ключ отдельной условной защиты, поставленной шагом сопровождения. */
    private static final String SEPARATE_STOP = "separate-stop";

    /** Дистанция отдельной защиты, процент якоря: дальше встроенной. */
    private static final String SEPARATE_STOP_PERCENTS = "3";

    /** Ключ трейлинга на сопровождении. */
    private static final String TRAILING = "trailing";

    /** Откат трейлинга от экстремума, процент. */
    private static final String TRAILING_CALLBACK_PERCENTS = "1";

    /** Определение, которым проверяется отбор входа после закрытия сделки. */
    private static final String NEXT_DEFINITION = "S-SAFE-NEXT";

    @Test
    @DisplayName("B5.1 — порядок полной реакции: статус, отчёт, снятие риска, терминал отчёта, каскад")
    void theFullReactionRunsStatusReportTeardownReportTerminalAndCascadeInOrder() {
        openLiveDeal();
        standConfirmedTeardown();

        tick(Tick.ANOMALY_DETECTION);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(1L);
        Map<String, Object> report = rows.row("anomaly_reports", "code", FOREIGN_INSTRUMENT_RISK);
        // Снятие риска ушло к площадке: и закрытие позиции, и снятие
        // живой защиты — у налитого входа живых входных заявок нет.
        List<LoggedRequest> closures = connector.requests(closurePath(ACCOUNT));
        List<LoggedRequest> protectionCancels = connector.requests(attachedCancellationPath(ACCOUNT));
        assertThat(closures).isNotEmpty();
        assertThat(protectionCancels).isNotEmpty();
        // Отчёт заведён ДО снятия риска и несёт снимок «до»: момент его
        // заведения раньше первой команды площадке.
        assertThat(report.get("internal_before")).isNotNull();
        assertThat(momentOf(report.get("created_at")))
                .isBefore(closures.getFirst().getLoggedDate().toInstant());
        // Отчёт завершён снимком «после», и завершён ПОСЛЕ последней
        // команды снятия.
        assertThat(report.get("status")).isEqualTo("COMPLETED");
        assertThat(report.get("internal_after")).isNotNull();
        assertThat(momentOf(report.get("modified_at")))
                .isAfter(protectionCancels.getLast().getLoggedDate().toInstant());
        // Каскад увёл активную сделку радиуса в ошибку с причиной ступени.
        assertThat(dealStatus()).isEqualTo("ERROR");
        assertThat(dealRow().get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
    }

    @Test
    @DisplayName("B5.2 — защита снимается последней и только после подтверждённого закрытия позиции")
    void theProtectionIsRemovedLastAndOnlyAfterTheConfirmedPositionClosure() {
        // Вход налит половиной: у сделки живая входная нога, живая позиция её
        // наливом и живая встроенная защита — все три носителя риска сразу.
        openPartiallyFilledDeal();
        standExchangeFollowingCommands(LOSS, LOSS, partialFill());

        fullHalt(ACCOUNT);

        // Порядок команд площадке: отмена входной ноги, закрытие позиции,
        // снятие защиты — и ни одной сверх.
        assertThat(commandCalls()).containsExactly(cancellationPath(ACCOUNT), closurePath(ACCOUNT),
                attachedCancellationPath(ACCOUNT));
        // Между закрытием и снятием защиты позиция перечитана: снятие идёт на
        // подтверждённом ФАКТЕ закрытия, а не на приёме команды.
        List<String> calls = connector.paths();
        Integer closure = calls.indexOf(closurePath(ACCOUNT));
        Integer protectionCancel = calls.indexOf(attachedCancellationPath(ACCOUNT));
        assertThat(calls.subList(closure, protectionCancel)).contains(positionPath(ACCOUNT));
        // Живых сущностей радиуса после хода не осталось, и снятие метит
        // причину ступени.
        assertThat(entryStatus()).isEqualTo("CANCELED");
        assertThat(entryRow().get("close_reason")).isEqualTo(KILL_SWITCH);
        assertThat(protectionRow().get("status")).isEqualTo("CANCELED");
        assertThat(protectionRow().get("close_reason")).isEqualTo(KILL_SWITCH);
        assertThat(rows.row("anomaly_reports", "code", MANUAL_HALT_REQUESTED).get("status"))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("B5.4 — неподтверждённое снятие риска повторяется ограниченно и кончается отказом")
    void theUnconfirmedTeardownRepeatsBoundedlyAndEndsInRefusal() {
        openLiveDeal();
        String ours = livePositionOf(entrySize());
        // Площадка закрытие ПРИНИМАЕТ, а позицию продолжает отдавать живой:
        // ни одно чтение после команды снятия риска не подтверждает.
        connector.answersInTurn(positionsPath(ACCOUNT), Feed.array(ours, foreignPosition()), Feed.array(ours));
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));
        connector.answers(attachedCancellationPath(ACCOUNT), Feed.ack(protectionExternalId(), protectionClientId()));

        tick(Tick.ANOMALY_DETECTION);

        // Попыток ровно три — предел kill-switch.max-teardown-attempts.
        assertThat(connector.requests(closurePath(ACCOUNT))).hasSize(3);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        // Отчёт НЕ закрыт: снимка «после» нет, терминала нет.
        Map<String, Object> report = rows.row("anomaly_reports", "code", FOREIGN_INSTRUMENT_RISK);
        assertThat(report.get("status")).isNotEqualTo("COMPLETED");
        assertThat(report.get("internal_after")).isNull();

        PeerStub.all().forEach(PeerStub::forgetRequests);
        tick(Tick.ANOMALY_DETECTION);
        tick(Tick.DEAL_ORCHESTRATOR);

        // Автоматических повторов сверх предела нет: ни тик детекции, ни
        // проход сопровождения закрытия больше не шлют — анкер стоящей
        // ступени поглощает повтор, и выход даёт только доведение по
        // вызову держателя.
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(rows.row("anomaly_reports", "code", FOREIGN_INSTRUMENT_RISK).get("status"))
                .isNotEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("B5.3 — встроенная защита родителя снимается наравне с отдельной")
    void theParentsEmbeddedProtectionIsRemovedAlongWithTheSeparateOne() {
        // Родитель налит и терминален, его встроенная защита материализована
        // источником в самостоятельную условную заявку; рядом — отдельная
        // условная защита, поставленная шагом сопровождения.
        openLiveDeal(Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, MarketPhase.Type.BULL_TREND,
                List.of(Definitions.managingStep(StrategyStepType.MAIN_PROTECTION,
                        Definitions.stopLossAlgo(SEPARATE_STOP, SEPARATE_STOP_PERCENTS)))));
        standAlgoOrdersFollowingCommands(1);
        passesUntil(() -> algoOrdersOfDeal().size() == 1
                && Objects.equals("ACTIVE", algoOrdersOfDeal().getFirst().get("status")));
        assertThat(entryStatus()).isEqualTo("COMPLETED");
        standExchangeFollowingCommands(LOSS);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        fullHalt(ACCOUNT);

        // Снятие ушло к площадке по ОБЕИМ защитам: и по отдельной условной
        // заявке, и по материализованной встроенной.
        List<LoggedRequest> separateCancels = connector.requests(algoCancellationPath(ACCOUNT));
        List<LoggedRequest> embeddedCancels = connector.requests(attachedCancellationPath(ACCOUNT));
        assertThat(separateCancels).isNotEmpty();
        assertThat(embeddedCancels).isNotEmpty();
        // Живых сущностей радиуса после хода не осталось: площадка сняла
        // обе, и обе сняты С ПРИЧИНОЙ ступени — снятие метит намерение
        // (docs/components/KillSwitchExecutor.md §Порядок). Встроенная не
        // читается потерянной, хотя экспозиция транша вне окна атрибуции
        // остаётся налитой: стоящее намерение закрывает ветвь потерянного
        // покрытия, и терминал даёт нога разбора истории
        // (docs/lifecycles/Order.md §«Исход ненайденности — вторая ступень»).
        assertThat(algoOrdersOfDeal().getFirst().get("status")).isEqualTo("CANCELED");
        assertThat(algoOrdersOfDeal().getFirst().get("close_reason")).isEqualTo(KILL_SWITCH);
        assertThat(protectionRow().get("status")).isEqualTo("CANCELED");
        assertThat(protectionRow().get("close_reason")).isEqualTo(KILL_SWITCH);
        // «Риск снят» объявлен только ПОСЛЕ снятия встроенной: отчёт
        // закрыт снимком «после» позже последней команды по ней —
        // живую встроенную защиту предикат не пропустил.
        Map<String, Object> report = rows.row("anomaly_reports", "code", MANUAL_HALT_REQUESTED);
        assertThat(report.get("status")).isEqualTo("COMPLETED");
        assertThat(momentOf(report.get("modified_at")))
                .isAfter(embeddedCancels.getLast().getLoggedDate().toInstant())
                .isAfter(separateCancels.getLast().getLoggedDate().toInstant());
    }

    @Test
    @DisplayName("B5.10 — мягкая ступень живых сделок не трогает")
    void theSoftRungLeavesLiveDealsAlone() {
        Strategy definition = Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, List.of(Definitions.managingStep(StrategyStepType.PROTECTION_ADJUSTMENT,
                        Definitions.trailingAlgo(TRAILING, TRAILING_CALLBACK_PERCENTS))));
        openLiveDeal(definition, () -> assertThat(freeze(ACCOUNT).status()).isEqualTo(204));
        assertThat(accountRung()).isEqualTo(HOLD);
        standAlgoOrdersFollowingCommands(1);

        passesUntil(() -> connector.count(algoPlacementPath(ACCOUNT)) > 0);

        // Трейлинг под мягкой ступенью исполнился: условная заявка поставлена.
        assertThat(algoOrdersOfDeal()).extracting(row -> row.get("condition_type"))
                .containsExactly("TRAILING_PERCENTS");
        // Сделка активна, стоп не заморожен и не снят: ни одной команды
        // снятия или закрытия ступень не произвела.
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(connector.requests(attachedCancellationPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
        assertThat(protectionRow().get("close_reason")).isNull();

        standExchangeFollowingCommands(PROFIT);
        exitByDeletion(definition);
        passesUntilDealTerminal();

        // Выход под мягкой ступенью исполнился штатно.
        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(accountRung()).isEqualTo(HOLD);
        // Новых сделок отбор входа не заводит: счёт выпал из выборки. Слот
        // счёта свободен — прежняя сделка закрыта, — и отказ принадлежит
        // ступени, а не занятому контуру.
        Long dealsBefore = rows.count("deals");
        activate(Definitions.withEntryCommandOnPhase(NEXT_DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(dealsBefore);
    }

    @Test
    @DisplayName("B5.11 — серия убыточных закрытий доводит счёт до мягкой ступени")
    void theLosingStreakBringsTheAccountToTheSoftRung() {
        consecutiveLossLimit = STREAK_LIMIT;

        closeLiveDealWith("S-STREAK-1", LOSS);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        // Прибыльное закрытие до предела счётчик обнуляет: следующий убыток
        // считается первым, и предела не достигает.
        closeLiveDealWith("S-STREAK-2", PROFIT);
        closeLiveDealWith("S-STREAK-3", LOSS);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        // Нулевой результат счётчика не двигает — ни в убыток, ни в сброс.
        closeLiveDealWith("S-STREAK-4", ZERO);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        // Недоступный результат не двигает тоже: сделка кончается аварийным
        // терминалом без числа.
        closeLiveDealWithoutResult("S-STREAK-5");
        assertThat(dealRow().get("result_profit")).isNull();
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        Long raisedBefore = countEvents(HOLD_RAISED);

        closeLiveDealWith("S-STREAK-6", LOSS);

        // Сделка, доводящая серию до предела: мягкая ступень счёта, отчёт
        // своим кодом, факт подъёма в outbox.
        assertThat(accountRung()).isEqualTo(HOLD);
        assertThat(codesOfReports()).contains(LOSS_STREAK_LIMIT_REACHED);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedBefore + 1);
        // Живой риск не снимается: у мягкой ступени снятия в составе нет, и
        // к площадке не ушло ничего сверх собственного выхода сделки.
        PeerStub.all().forEach(PeerStub::forgetRequests);
        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.ANOMALY_DETECTION);
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B5.12 — снятие ступени счётчик серии не обнуляет")
    void clearingTheRungDoesNotResetTheStreak() {
        consecutiveLossLimit = STREAK_LIMIT;
        closeLiveDealWith("S-CLEAR-1", LOSS);
        closeLiveDealWith("S-CLEAR-2", LOSS);
        assertThat(accountRung()).isEqualTo(HOLD);

        assertThat(post(HALT_CLEARANCES, Bodies.halt("FREEZE", ACCOUNT)).status()).isEqualTo(204);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        closeLiveDealWith("S-CLEAR-3", LOSS);

        // Следующее убыточное закрытие снова доводит до предела: счётчик
        // снятием не тронут. Обнули его снятие — это закрытие было бы
        // первым в серии, и ступени не было бы.
        assertThat(accountRung()).isEqualTo(HOLD);
    }

    @Test
    @DisplayName("B5.13 — расхождение сверки в разведочном режиме лестницу не триггерит")
    void theReconciliationMismatchInTheExploratoryModeDoesNotTriggerTheLadder() {
        openLiveDeal();
        // Запись закрытия и движение закрытия расходятся на два — сверх
        // пола допуска при любом обороте этой сделки.
        standExchangeFollowingCommands("-7", "-5");

        exitByDeletion(workingDefinition());
        passesUntilDealTerminal();

        // Признак расхождения на сделке проставлен, отчёт заведён, а
        // счёт остаётся в рабочем состоянии: режим допуска штатно
        // разведочный. Вторая половина кейса — тот же вход при
        // выключенном разведочном режиме — живёт своим контекстом
        // (StrictReconciliationBoxTest).
        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(dealRow().get("reconciliation_status")).isEqualTo("MISMATCHED");
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(countEvents(HOLD_RAISED)).isZero();
        // Отчёт заводит исполнитель терминального ребра сам, без сигнала
        // ступени (docs/rules/pnl-reconciliation.md §«Реакция на расхождение»).
        assertThat(codesOfReports()).containsExactly(RECONCILIATION_MISMATCH);
    }

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
     * Срез счёта с признаком жёсткой ступени поверх ЖИВОЙ сделки и
     * площадка, подтверждающая снятие риска фактами.
     *
     * <p><b>Каждое чтение после команды видит её исход:</b> позиции сделки
     * по инструменту больше нет, запись её закрытия добыта, материализованной
     * защиты среди живых нет, а срез счёта отдаёт чужую позицию, пока её не
     * закрыл шаг вне графа сделок, и пустоту после. Иначе снятие риска не
     * подтвердилось бы, и клетка наблюдала бы исчерпание предела
     * ({@code B5.4}) вместо полной реакции.
     */
    private void standConfirmedTeardown() {
        String ours = livePositionOf(entrySize());
        connector.answersInTurn(positionsPath(ACCOUNT), Feed.array(ours, foreignPosition()),
                Feed.array(foreignPosition()), Feed.emptyArray());
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));
        connector.answers(attachedCancellationPath(ACCOUNT), Feed.ack(protectionExternalId(), protectionClientId()));
        connector.answers(positionPath(ACCOUNT), Feed.absent());
        connector.answers(closedPositionsPath(ACCOUNT), Feed.array(Feed.closedPosition(positionExternalId(),
                POSITION_CREATED_AT, "2026-09-20T11:00:00Z", "-5")));
        connector.answers(pendingProtectionsPath(ACCOUNT), Feed.emptyArray());
        connector.answers(protectionHistoryPath(ACCOUNT), Feed.emptyArray());
        // Снятая защита видна разбором истории ногой снятых: без этой записи
        // судьба защиты под намерением снятия не определена, и «риск снят»
        // не подтверждается (docs/lifecycles/Order.md §«Пустой разбор истории»).
        connector.answersWhen(protectionHistoryPath(ACCOUNT), "leg", "CANCELED", Feed.array(
                Feed.materializedProtection(protectionClientId(), protectionExternalId(), entrySize(),
                        protectionTrigger())));
    }

    /**
     * Живая сделка, закрытая выходом БЕЗ числа: площадка записи закрытия
     * не отдаёт, бюджет добычи исчерпывается, и сделка кончается
     * аварийным терминалом, у которого результата нет.
     *
     * <p><b>Мягкая ступень пары, поднятая исчерпанием, снимается ручным
     * снятием</b> — иначе следующая сделка серии на этом инструменте не
     * открылась бы: ступень пары выбывает из отбора входа.
     */
    private void closeLiveDealWithoutResult(String definitionId) {
        Strategy definition = Definitions.withEntryCommandOnPhase(definitionId, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND);
        openLiveDeal(definition);
        standExchangeFollowingCommands(LOSS);
        connector.answers(closedPositionsPath(ACCOUNT), Feed.emptyArray());
        exitByDeletion(definition);
        passesUntilDealTerminal();
        assertThat(dealStatus()).isEqualTo("EMERGENCY_CLOSED");
        assertThat(post(HALT_CLEARANCES, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Живая позиция по инструменту вне контура: признак жёсткой ступени. */
    private String foreignPosition() {
        return Feed.livePosition("ex-foreign-1", FOREIGN_INSTRUMENT, "1", LAST_PRICE, POSITION_MOMENT);
    }

    /** Момент базы как мгновение: колонки аудита приезжают временем со смещением. */
    private Instant momentOf(Object column) {
        return ((OffsetDateTime) column).toInstant();
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
}
