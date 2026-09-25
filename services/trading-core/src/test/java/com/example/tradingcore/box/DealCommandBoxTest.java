package com.example.tradingcore.box;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} — команды площадке: жизненный цикл, повторы, классы
 * отказа.
 *
 * <p><b>Предмет группы — ЖИЗНЕННЫЙ ЦИКЛ команды, а не арифметика её
 * параметров.</b> Сколько контрактов взял вход и по какой цене —
 * предмет уровня 2 ({@code trading-core-risk}, {@code trading-core-calc});
 * здесь наблюдается, ЧТО ушло к соседу, в каком порядке, сколько раз и
 * что осталось в базе (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Тропа до команды строится ЦЕЛИКОМ тропами ящика</b>
 * ({@link #openCommandDeal}): проекции — тиком синка, ставка комиссии —
 * тиком её синка, числа риск-аппетита — своей поверхностью, сделка с
 * траншем — тиком отбора входа, снимок средств — первым тиком
 * сопровождения. Прямой записи в предусловиях группы нет ни одной, и это
 * не аккуратность: команду площадке порождает расчёт, а расчёт читает
 * ровно то, что эти тропы и кладут.
 *
 * <p><b>Определение группы объявляет то, чего не объявляло определение
 * отбора входа</b>: встроенную защиту, долю аллокации и четыре потолка
 * риска на детали ({@code Definitions#withEntryCommandOnPhase}). Без них
 * расчёт и преконтроль отвергают действие раньше команды — то есть
 * предмет группы не достигается вовсе.
 *
 * <p><b>Связка фич несёт ЦЕНЫ</b> ({@code Feed#featuresWithPrice}): чтение
 * под расчёт спрашивает их безусловно, и связка без цены даёт отказ
 * {@code NO_REFERENCE_PRICE} на первом же действии.
 *
 * <p><b>Прямой записи в группе нет ни одной.</b>
 *
 * <p><b>Клетки с меткой {@code debt} красны ПО ПОСТРОЕНИЮ</b>: их ожидание
 * взято из дома, который дерево кода на этой тропе не исполняет, и
 * ослаблять его под текущий факт значило бы закрепить дефект
 * (.claude/tests/cases/trading-core.md §«Ожидание берётся из дома, даже
 * когда сегодня оно не исполнено»).
 */
class DealCommandBoxTest extends SharedLiveDealBox {

    /** Класс события решения о заявке. */
    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    /** Системная надобность добычи контекста сделки: позиция, записи закрытия. */
    private static final String REFRESH_DEAL_CONTEXT = "REFRESH_DEAL_CONTEXT_ACTION";

    /** Код исчерпанного бюджета повторов звена. */
    private static final String BUDGET_EXHAUSTED = "INSTRUMENT_RETRY_BUDGET_EXHAUSTED";

    /** Реализованный результат закрытого эпизода: убыток. */
    private static final String REALIZED_LOSS = "-5";

    /** Основа идентичности определения клеток выхода. */
    private static final String DEFINITION = "S-EXIT";

    /** Ключ действия выхода на сопровождении. */
    private static final String EXIT = "exit";

    /** Доля экспозиции транша, которой он выходит: целиком. */
    private static final String WHOLE_TRANCHE_PERCENTS = "100";

    /** Ключ действия добора на подтверждённом входе. */
    private static final String ADD_ON = "add-on";

    /** Причина остановки сделки по устареванию данных шага. */
    private static final String MARKET_DATA_EXPIRED = "MARKET_DATA_EXPIRED";

    /** След реджекта преконтроля в окне сворачивания: пропуск действия. */
    private static final String COLLAPSE_SKIPPED = "type=SKIP_ACTION code=RISK_CREATING_UNDER_COLLAPSE";

    /** Признак reduce-only в теле команды размещения. */
    private static final String REDUCE_ONLY_ON_WIRE = "\"positionReducingOnly\":true";

    /**
     * Потолок ожидания перевзвода строки: объявленный откат политики
     * экспоненциален, и первая попытка укладывается в него с запасом.
     */
    private static final Duration RETRY_HORIZON = Duration.ofSeconds(60);

    @Test
    @DisplayName("B3.1 — создание заявки на биржу не ходит и пишет решение с событием")
    void theOrderCreationDoesNotReachTheExchangeAndWritesTheDecisionWithItsEvent() {
        openCommandDeal(workingDefinition());
        // Первый тик сопровождения снял снимок средств и работы не сделал.
        assertThat(rows.count("orders")).isZero();

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(placementCalls()).isEmpty();
        Map<String, Object> order = rows.all("orders").getFirst();
        assertThat(String.valueOf(order.get("internal_id"))).isNotBlank();
        assertThat(order.get("external_id")).isNull();
        assertThat(eventTypes()).contains(ORDER_DECIDED);
    }

    @Test
    @DisplayName("B3.2 — отправка идёт следующим проходом и переводит транш во вход")
    void theSubmissionGoesOnTheNextPassAndMovesTheTrancheToTheSubmittedEntry() {
        openCommandDeal(workingDefinition());
        tick(Tick.DEAL_ORCHESTRATOR);
        String clientId = entryClientId();
        acceptPlacement(clientId);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
        assertThat(placementBody()).contains(clientId);
        // Подтверждение приёма состоянием не считается: нога стои́т в
        // ОЖИДАНИИ, а не в исполненном — его подтверждает добыча.
        assertThat(entryStatus()).isEqualTo("PENDING");
        // Ребро транша едет ПРОХОДОМ, на котором отправка уже подтверждена
        // фактом: выходная проверка обработчика читает ногу, а нога
        // отправляется командой ПОСЛЕ того, как переход отдан.
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", clientId, "ACTIVE"));
        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(trancheStatus()).isEqualTo("ENTRY_SUBMITTED");
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
    }

    @Test
    @DisplayName("B3.3 — повторная отправка ищет сущность по клиентскому идентификатору, а не шлёт вторую")
    void theRepeatedSubmissionLooksTheEntityUpByItsClientIdentifierInsteadOfSendingASecondOne() {
        openCommandDeal(workingDefinition());
        tick(Tick.DEAL_ORCHESTRATOR);
        String clientId = entryClientId();
        // Отправка состоялась, но ответ до нас не дошёл: площадка объявлена
        // недостижимой, то есть отказ повторяем, — а заявку стаб помнит.
        connector.answers(placementPath(ACCOUNT), 502, Feed.peerFailure("EXCHANGE_UNREACHABLE"));
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(actionState().get("status")).isEqualTo("RETRY_PENDING");
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", clientId, "ACTIVE"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        ticksUntil(() -> nonNull(rows.all("orders").getFirst().get("external_id")));

        assertThat(connector.requests(lookupPath(ACCOUNT))).hasSize(1);
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
        assertThat(rows.count("orders")).isEqualTo(1L);
        assertThat(rows.all("orders").getFirst().get("external_id")).isEqualTo("ex-1");
        // Факт отправки восстановила ИМЕННО отправка, а не добыча: тот же
        // путь поиска зовут оба звена, и по одному попаданию стаба они
        // неразличимы. Различают их МЕТКИ: восстановление ставит ногу в
        // ожидание и строку исполнения в отправленное, а добыча — ногу в
        // наблюдённый статус источника и строку в завершённое.
        assertThat(entryStatus()).isEqualTo("PENDING");
        assertThat(actionState().get("status")).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("B3.4 — добыча обрывается на первом ответе, несущем искомый факт")
    void theEvidenceCycleStopsAtTheFirstAnswerThatCarriesTheSoughtFact() {
        submitEntry(workingDefinition());
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", entryClientId(), "ACTIVE"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Первая нога лестницы — поиск по идентификатору, и она же обрывает
        // добычу: ни живые заявки, ни история не спрашивались.
        assertThat(connector.requests(lookupPath(ACCOUNT))).hasSize(1);
        assertThat(connector.requests(pendingPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(historyPath(ACCOUNT))).isEmpty();
        assertThat(actionState().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("B3.5 — недобытый факт звена не завершает и повторяется по бюджету")
    void theUnfetchedFactLeavesTheLinkIncompleteAndRetriesWithinTheBudget() {
        openLiveDeal();
        standExchangeFollowingCommands(REALIZED_LOSS);
        // Запись закрытия эпизода площадка не отдаёт НИ ОДНОЙ ногой: позиция
        // закрыта, а звено добычи её факта не добывает.
        connector.answers(closedPositionsPath(ACCOUNT), Feed.emptyArray());
        exitByDeletion(workingDefinition());
        Map<Object, Integer> attemptsSeenLive = new HashMap<>();

        // До исчерпания исход «звено не завершено» отказом не является:
        // ни отчёта, ни ступени ни на одном радиусе, ни ошибки сделки — и
        // это проверяется на КАЖДОМ проходе, а не на последнем.
        passesUntil(() -> {
            Boolean exhausted = fetchRows().stream()
                    .anyMatch(row -> Objects.equals("FAILED", row.get("status")));
            if (isFalse(exhausted)) {
                assertThat(rows.count("anomaly_reports")).isZero();
                assertThat(accountRung()).isEqualTo(NO_RUNG);
                assertThat(pairRung(INSTRUMENT)).isEqualTo(NO_RUNG);
                assertThat(dealStatus()).isEqualTo("EXIT_PENDING");
                fetchRows().stream()
                        .filter(row -> Objects.equals("RETRY_PENDING", row.get("status")))
                        .forEach(row -> attemptsSeenLive.merge(row.get("id"),
                                ((Number) row.get("attempt_count")).intValue(), Math::max));
            }
            return exhausted;
        });

        Map<String, Object> exhaustedRow = fetchRows().stream()
                .filter(row -> Objects.equals("FAILED", row.get("status")))
                .findFirst()
                .orElseThrow();
        // Строка оставалась живой две попытки и исчерпалась третьей —
        // предел политики service-command-retry.
        assertThat(attemptsSeenLive.get(exhaustedRow.get("id"))).isEqualTo(2);
        assertThat(exhaustedRow.get("attempt_count")).isEqualTo(3);
        // Учёт бросает исчерпание: сделка ушла ошибочной тропой, на
        // инструменте встала мягкая ступень с кодом исчерпанного бюджета.
        assertThat(dealStatus()).isEqualTo("ERROR");
        assertThat(pairRung(INSTRUMENT)).isEqualTo("ENTRY_BLOCKED");
        assertThat(codesOfReports()).containsExactly(BUDGET_EXHAUSTED);
        Long rowsOfTheNeed = rows.countWhere("deal_system_action_states", "system_action_type",
                REFRESH_DEAL_CONTEXT);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Новой строки той же надобности следующий проход не заводит:
        // сделка уже не в сопровождении.
        assertThat(rows.countWhere("deal_system_action_states", "system_action_type", REFRESH_DEAL_CONTEXT))
                .isEqualTo(rowsOfTheNeed);
    }

    @Test
    @DisplayName("B3.7 — контролируемое исключение границы поднимает жёсткую биржевую ступень")
    void theControlledBoundaryFailureRaisesTheHardExchangeRung() {
        submitEntry(workingDefinition());
        connector.answers(lookupPath(ACCOUNT), 422, Feed.peerFailure("EXTERNAL_STATUS"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(rows.count("anomaly_reports")).isEqualTo(1L);
        assertThat(dealStatus()).isEqualTo("ERROR");
        // Отметка исхода на ноге ПЕРЕЖИЛА бросок: транзакция звена
        // завершилась отказом, а причина в базе есть.
        assertThat(rows.all("orders").getFirst().get("close_reason"))
                .isEqualTo("UNKNOWN_EXTERNAL_STATUS");
    }

    @Test
    @DisplayName("B3.8 — отказ источника в наших кредах — свой класс, и повтора он не получает")
    void theRejectedCredentialsAreTheirOwnClassAndGetNoRetry() {
        openCommandDeal(workingDefinition());
        tick(Tick.DEAL_ORCHESTRATOR);
        connector.answers(placementPath(ACCOUNT), 401, Feed.peerFailure("EXCHANGE_CREDENTIALS_REJECTED"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        // Повтора нет ни одного: класс изъят из повторяемого, и вторая
        // попытка не состоялась даже при пределе три.
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
        Map<String, Object> state = actionState();
        assertThat(state.get("attempt_count")).isEqualTo(1);
        assertThat(state.get("status")).isEqualTo("FAILED");
        assertThat(state.get("next_retry_at")).isNull();
    }

    @Test
    @Tag("debt")
    @DisplayName("B3.9 — отказ соседа по ярусу сделку в ошибку не уводит")
    void theTierPeerFailureDoesNotMoveTheDealToError() {
        openCommandDeal(workingDefinition());
        marketData.answers(featuresPath(INSTRUMENT), 503, Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Долг: общий перехватчик прохода ловит отказ соседа наравне со
        // всяким неожиданным исключением (.claude/work/backlog.md §«Отказ
        // соседа по ярусу на проходе сопровождения уводит сделку в ERROR»).
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(rows.count("orders")).isEqualTo(0L);
        assertThat(rows.count("deal_strategy_action_states")).isEqualTo(0L);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("anomaly_reports")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B3.12 (а) — выход всех траншей идёт командой закрытия позиции")
    void theExitOfAllTranchesGoesByThePositionClosure() {
        openLiveDeal(Definitions.withManagingSteps(DEFINITION, ACCOUNT, INSTRUMENT, MarketPhase.Type.BULL_TREND,
                List.of(Definitions.managingStep(StrategyStepType.EXIT, Definitions.positionExit(EXIT)))));
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));

        passesUntil(() -> connector.count(closurePath(ACCOUNT)) > 0);

        // Транш у сделки один — выходят ВСЕ, и нетто-экспозиция закрывается
        // целиком командой закрытия, а не reduce-only заявкой.
        assertThat(tranchesOfDeal()).hasSize(1);
        assertThat(connector.requests(closurePath(ACCOUNT))).hasSize(1);
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B3.12 (б) — выход одного транша из двух идёт reduce-only заявкой, а не закрытием позиции")
    void theExitOfOneTrancheOfTwoGoesByAReduceOnlyOrder() {
        openTwoTrancheLiveDeal(Definitions.withTwoDeclarationsFirstManaged(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, List.of(Definitions.managingStep(StrategyStepType.PARTIAL_EXIT,
                        Definitions.reduceOnlyExit(EXIT, WHOLE_TRANCHE_PERCENTS)))));
        // Выходящий транш опознаётся КЛЮЧОМ своего объявления, а не порядком
        // заведения: транши материализуются в порядке загрузки объявлений
        // копии, а он произволен (находка F-23).
        Object exitingTrancheId = rows.select("select t.id from deal_tranches t"
                + " join strategy_tranches s on s.id = t.strategy_tranche_id"
                + " where t.deal_id = (select max(id) from deals) and s.key = ?",
                Definitions.ENTRY_TRANCHE_KEY).getFirst().get("id");
        String exitingEntrySize = String.valueOf(ordersOfDeal().stream()
                .filter(row -> Objects.equals(exitingTrancheId, row.get("deal_tranche_id")))
                .findFirst().orElseThrow().get("size"));
        connector.answers(placementPath(ACCOUNT), Feed.ack("ex-exit-1", "exit-1"));
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));

        passesUntil(() -> connector.count(placementPath(ACCOUNT)) > 0);

        // Команды закрытия позиции не уходило: сосед по сделке жив, и
        // закрытие нетто-позиции обнулило бы его экспозицию.
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
        // Ушла reduce-only заявка, и размер её не больше экспозиции
        // выходящего транша — налива его собственной входной ноги.
        List<Map<String, Object>> exits = ordersOfDeal().stream()
                .filter(row -> Objects.equals(Boolean.TRUE, row.get("position_reducing_only")))
                .toList();
        assertThat(exits).hasSize(1);
        assertThat(new BigDecimal(String.valueOf(exits.getFirst().get("size"))))
                .isLessThanOrEqualTo(new BigDecimal(exitingEntrySize));
        assertThat(exits.getFirst().get("deal_tranche_id")).isEqualTo(exitingTrancheId);
        assertThat(connector.single(placementPath(ACCOUNT)).getBodyAsString()).contains(REDUCE_ONLY_ON_WIRE);
    }

    @Test
    @DisplayName("B3.13 — порядок выхода: сперва живые входные заявки, потом позиция")
    void theExitCancelsTheLiveEntryOrdersBeforeClosingThePosition() {
        // Вход налит половиной: нога жива, и экспозиция у того же транша уже
        // есть — ровно то состояние, где обратный порядок оставил бы окно
        // долива.
        openPartiallyFilledDeal();
        standExchangeFollowingCommands(REALIZED_LOSS, REALIZED_LOSS, partialFill());
        exitByDeletion(workingDefinition());

        passesUntil(() -> connector.count(closurePath(ACCOUNT)) > 0);

        // Отмена входной ноги ушла РАНЬШЕ закрытия экспозиции, и отменена
        // была именно она.
        List<String> commands = commandCalls();
        assertThat(commands).containsSubsequence(cancellationPath(ACCOUNT), closurePath(ACCOUNT));
        assertThat(commands.indexOf(cancellationPath(ACCOUNT))).isLessThan(commands.indexOf(closurePath(ACCOUNT)));
        assertThat(connector.requests(cancellationPath(ACCOUNT)).getFirst().getBodyAsString())
                .contains(entryExternalId());
        // Закрытие ушло, когда входная нога уже наблюдена снятой: живой
        // входной ноги у сделки к нему не осталось.
        assertThat(entryStatus()).isEqualTo("CANCELED");
        assertThat(connector.requests(closurePath(ACCOUNT))).hasSize(1);
    }

    @Test
    @DisplayName("B3.14 — в окне сворачивания нового риска не берёт ни один транш")
    void noTrancheTakesNewRiskWithinTheCollapseWindow() {
        submitTwoEntries(Definitions.withAddOnBesideGracefullyExpiringOnEntryFinalized(DEFINITION, ACCOUNT,
                INSTRUMENT, MarketPhase.Type.BULL_TREND, ADD_ON));
        // Обе ноги налиты одним ответом площадки: транши встают на
        // подтверждённый вход одним проходом, и следующий проход у одного
        // заводит строку добора, а у другого — просьбу о сворачивании.
        standTwoFilledEntries();

        passesUntil(() -> Objects.equals("EXIT_PENDING", dealStatus()));

        // Предусловие достигнуто переносом, а не иным путём: сделку увела
        // просьба соседа, транш добора остался на подтверждённом входе, его
        // строка жива и ни до ноги, ни до площадки не дошла.
        assertThat(dealRow().get("shutdown_reason")).isEqualTo(MARKET_DATA_EXPIRED);
        Object addOnTranche = trancheIdOf(Definitions.ENTRY_TRANCHE_KEY);
        assertThat(trancheStatusOf(addOnTranche)).isEqualTo("ENTRY_FINALIZED");
        List<Map<String, Object>> liveRows = rows.rowsWhere("deal_strategy_action_states", "deal_tranche_id",
                addOnTranche).stream()
                .filter(row -> isFalse(Objects.equals("COMPLETED", row.get("status"))))
                .toList();
        assertThat(liveRows).hasSize(1);
        assertThat(liveRows.getFirst().get("status")).isEqualTo("PLANNED");
        assertThat(entryLegs()).hasSize(2);
        // Площадка отвечает по исходу команд выхода: закрытие позиции уходит
        // уже проходом, который доигрывает перенесённую строку.
        standExchangeFollowingCommands(REALIZED_LOSS);
        PeerStub.all().forEach(PeerStub::forgetRequests);
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Перенесённая строка доиграна первой стадией, и преконтроль отверг
        // добор кодом окна сворачивания; реакция — пропуск действия.
        assertThat(AppLog.since(mark)).contains(COLLAPSE_SKIPPED);
        assertThat(entryLegs()).hasSize(2);
        assertThat(connector.requests(placementPath(ACCOUNT)).stream()
                .filter(request -> isFalse(request.getBodyAsString().contains(REDUCE_ONLY_ON_WIRE))))
                .isEmpty();
        // Реджект в карв-ауте: сделка не уведена в ошибку.
        assertThat(dealStatus()).isEqualTo("EXIT_PENDING");

        passesUntil(() -> List.of("CLOSED", "EMERGENCY_CLOSED", "ERROR").contains(dealStatus()));

        // Выход остальных траншей продолжается до терминала, и добора
        // площадка не получила ни на одном из его проходов. Транш соседа, чей
        // шаг устарел, доходит до выхода через сопровождение: исполненная
        // просьба о сворачивании его прохода не занимает.
        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(entryLegs()).hasSize(2);
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    @Test
    @DisplayName("B3.15 — ожидающие команды рестарт не переживают")
    void pendingCommandsDoNotSurviveARestart() {
        openCommandDeal(workingDefinition());
        tick(Tick.DEAL_ORCHESTRATOR);
        // Команда создана и не отправлена: нога заведена, а к площадке не
        // уходило ничего.
        assertThat(rows.count("orders")).isEqualTo(1L);
        assertThat(placementCalls()).isEmpty();
        String clientId = entryClientId();
        acceptPlacement(clientId);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        // Перезапуск — процесс, у которого памяти прежнего нет, а есть
        // только база. Прежний контекст общий для класса и не
        // останавливается, но тиков ему не подаётся: отправку решает
        // только новый процесс.
        try (CoreReplica restarted = CoreReplica.launch("box-restarted-core")) {
            tickAt(restarted.port(), Tick.DEAL_ORCHESTRATOR);
        }

        // Команду пересобрал проход по фактам графа и строк исполнения:
        // ушла одна отправка, её тело несёт ту же ногу, и второй ноги не
        // завелось.
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
        assertThat(placementBody()).contains(clientId);
        assertThat(rows.count("orders")).isEqualTo(1L);
        assertThat(entryStatus()).isEqualTo("PENDING");
        // Очереди команд в базе нет: пересобирать нечего было бы иначе.
        assertThat(rows.select("select table_name from information_schema.tables"
                + " where table_schema = current_schema() and table_name like '%command%'")).isEmpty();
    }

    // ------------------------------------------------------------------

    /**
     * Тикает проход сопровождения до наступления названного состояния.
     *
     * <p><b>Число тиков не пиньнуто намеренно.</b> Перевзвод строки,
     * ожидающей повтора, гейтится моментом следующей попытки
     * ({@code StrategyActionOrchestrator#retryDue}), а момент выводится из
     * объявленного отката политики; клетка эту величину не подменяет —
     * она тикает, пока откат не истечёт. Пиньнутое число тиков мерило бы
     * длину отката, а не предмет клетки.
     *
     * @param reached условие, по достижении которого тики прекращаются
     */
    private void ticksUntil(Callable<Boolean> reached) {
        Awaitility.await().atMost(RETRY_HORIZON).pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    tick(Tick.DEAL_ORCHESTRATOR);
                    return reached.call();
                });
    }

    /**
     * Строки исполнения системной добычи контекста сделки — той надобности,
     * чей бюджет мерит {@code B3.5}.
     */
    private List<Map<String, Object>> fetchRows() {
        return rows.rowsWhere("deal_system_action_states", "system_action_type", REFRESH_DEAL_CONTEXT);
    }

    /**
     * Идентичность транша последней сделки по ключу его объявления: порядок
     * заведения траншей произволен (находка F-23).
     */
    private Object trancheIdOf(String declarationKey) {
        return rows.select("select t.id from deal_tranches t"
                + " join strategy_tranches s on s.id = t.strategy_tranche_id"
                + " where t.deal_id = (select max(id) from deals) and s.key = ?", declarationKey)
                .getFirst().get("id");
    }

    /** Статус транша по его идентичности. */
    private String trancheStatusOf(Object trancheId) {
        return String.valueOf(rows.select("select status from deal_tranches where id = ?", trancheId)
                .getFirst().get("status"));
    }

    /** Ноги последней сделки, набирающие риск: всё, что не reduce-only. */
    private List<Map<String, Object>> entryLegs() {
        return ordersOfDeal().stream()
                .filter(row -> Objects.equals(Boolean.FALSE, row.get("position_reducing_only")))
                .toList();
    }

    /** Площадка принимает размещение и возвращает свой идентификатор. */
    private void acceptPlacement(String clientId) {
        connector.answers(placementPath(ACCOUNT), Feed.ack("ex-1", clientId));
    }

    /** Тело единственного запроса размещения. */
    private String placementBody() {
        return connector.single(placementPath(ACCOUNT)).getBodyAsString();
    }

    /** Единственная строка исполнения объявленного действия. */
    private Map<String, Object> actionState() {
        return rows.all("deal_strategy_action_states").getFirst();
    }

    /** Обращения к коннектору, которые размещают сущность на площадке. */
    private List<String> placementCalls() {
        return connector.paths().stream()
                .filter(path -> path.endsWith("/orders") || path.endsWith("/algo-orders")
                        || path.endsWith("/attached-protections"))
                .toList();
    }

}
