package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B2} — проход сопровождения: порядок шагов и перехваты.
 *
 * <p><b>Предмет группы — ПОРЯДОК шагов прохода и его перехватчики</b>, а не
 * работа обработчиков: рёбра FSM, неравенства риск-гейта и арифметика
 * сайзинга принадлежат уровню 2
 * (.claude/tests/cases/trading-core.md §«Объём»). Отсюда форма клеток:
 * каждая наблюдает один шаг цикла — выборку, энфорсмент жёсткой ступени,
 * ревизию живых исполнений, диспетчеризацию, применение перехода — по
 * следу, который он оставляет снаружи процесса.
 *
 * <p><b>Сделка ставится ТРОПОЙ ЯЩИКА — тиком отбора входа</b>
 * ({@link #openDeals}), построенной группой {@code B1}. Прямая запись
 * остаётся ровно у двух состояний, которых тропа ящика поставить не может
 * по построению: у терминальных статусов ({@code B2.1}) — их пишет полный
 * выход с живой позицией, предмет групп {@code B3} и {@code B5}, — и у
 * СТОЯЩЕЙ ЖЁСТКОЙ СТУПЕНИ ({@link #standAccountRung}).
 *
 * <p><b>Почему ступень ставится прямой записью, а не ручной
 * поверхностью.</b> Постановка ступени поверхностью уводит нетерминальные
 * сделки радиуса ПЕРВЫМ ХОДОМ энфорсмента
 * (docs/components/SafetyHoldCoordinator.md), то есть исполняет ровно то
 * ребро, которое клетка мерит у ПРОХОДА, — и отнимает у клетки её вход.
 * Предмет же группы есть второй затребователь того же ребра: шаг прохода,
 * подбирающий сделки, ставшие активными после каскада.
 *
 * <p><b>Каскад траншей обязан МОЛЧАТЬ там, где предмет клетки лежит ниже
 * него.</b> Обработчик активной сделки выходит на первой же просьбе
 * каскада, а просьбой считается и одобренное ребро
 * ({@code TrancheCascadeResult#acted}); поэтому клетки о шагах, стоящих
 * после каскада, доводят транш до терминального состояния заранее
 * ({@link #closeTrancheByFalseCondition}) — и проход доходит до своего
 * предмета одним тиком, как того и требует кейс.
 *
 * <p><b>Ошибочное состояние держится ЖИВЫМ РИСКОМ, и это не украшение
 * предусловия.</b> Обработчик ошибочного состояния доводит сделку до
 * аварийного терминала ТЕМ ЖЕ проходом, как только живой риск доказанно
 * отсутствует ({@code DealTerminalGate#riskProvenAbsent}); сделка, ни
 * одной заявки не имевшая, в {@code ERROR} поэтому не задерживается, и
 * клетка о ВТОРОМ проходе по уведённой сделке не имела бы предмета.
 * Живая заявка ставится прямой записью ({@link #putLiveEntryOrder}): её
 * пишет команда площадке — предмет группы {@code B3}.
 */
class DealPassBoxTest extends SharedLiveDealBox {

    /** Основа идентичности определения: у каждой пары своё. */
    private static final String DEFINITION = "S";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Класс события остановки штатного ведения сделки. */
    private static final String DEAL_SHUTDOWN_INITIATED = "DEAL_SHUTDOWN_INITIATED";

    /** Класс события терминала сделки. */
    private static final String DEAL_CLOSED = "DEAL_CLOSED";

    /** Жёсткая ступень обоих радиусов. */
    private static final String TRADE_BLOCKED = "TRADE_BLOCKED";

    /** Отсутствие ступени: рабочее состояние счёта. */
    private static final String NO_RUNG = "ACTIVE";

    /** Номер эпизода, которого у транша нет: им ставится пережившая строка. */
    private static final Integer FOREIGN_EPISODE = 99;

    /** След общего перехватчика прохода. */
    private static final String PASS_FAILED = "Deal pass failed dealId=";

    /**
     * След ВЫДЕЛЕННОГО перехватчика ребра, присваивавшего причину.
     *
     * <p>Взят с хвостом «оставлена следующему проходу» намеренно: голова
     * фразы есть и в сообщении самого исключения, то есть общий
     * перехватчик, напечатавший его причину, дал бы то же попадание — и
     * клетка перестала бы различать два перехватчика.
     */
    private static final String EDGE_FAILED = "Deal shutdown edge failed, the deal is left to the next pass";

    /** След энфорсмента жёсткой ступени шагом прохода. */
    private static final String ENFORCED = "Deal is moved to error by the hard rung enforcement dealId=";

    /** След пропуска перекрывающего запуска. */
    private static final String OVERLAP_SKIPPED =
            "Job dealOrchestratorJob is already running: overlapping tick skipped";

    @Test
    @DisplayName("B2.1 — проход берёт нетерминальные сделки окна и терминальных не трогает")
    void thePassTakesTheNonTerminalDealsAndLeavesTheTerminalOnesAlone() {
        List<String> instruments = pairs(5);
        openDeals(instruments);
        Map<String, Long> dealIds = dealIdsByInstrument();
        // Нештатные и терминальные статусы ставятся прямой записью: их
        // рёбра пишет полный выход с живой позицией (§шапка класса).
        setStatus(dealIds.get(instruments.get(1)), "EXIT_PENDING");
        setStatus(dealIds.get(instruments.get(2)), "ERROR");
        setStatus(dealIds.get(instruments.get(3)), "CLOSED");
        setStatus(dealIds.get(instruments.get(4)), "EMERGENCY_CLOSED");
        Map<String, Object> beforePass = rows.row("deals", "id", dealIds.get(instruments.get(3)));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Контекст собран у трёх нетерминальных: сборка снимает связку фич
        // момента, и это её единственный наблюдаемый снаружи след.
        assertThat(marketData.count(featuresPath(instruments.get(0)))).isEqualTo(1);
        assertThat(marketData.count(featuresPath(instruments.get(1)))).isEqualTo(1);
        assertThat(marketData.count(featuresPath(instruments.get(2)))).isEqualTo(1);
        // У терминальных — ни чтения графа, ни вызова к коннектору, ни
        // правки строки: обработчиков у терминальных статусов нет.
        assertThat(marketData.count(featuresPath(instruments.get(3)))).isZero();
        assertThat(marketData.count(featuresPath(instruments.get(4)))).isZero();
        assertThat(connector.requestsUnder(accountPath(accountOf(4)))).isEmpty();
        assertThat(connector.requestsUnder(accountPath(accountOf(5)))).isEmpty();
        Map<String, Object> afterPass = rows.row("deals", "id", dealIds.get(instruments.get(3)));
        assertThat(afterPass.get("status")).isEqualTo("CLOSED");
        assertThat(afterPass.get("modified_at")).isEqualTo(beforePass.get("modified_at"));
    }

    @Test
    @DisplayName("B2.3 — энфорсмент жёсткой ступени стои́т раньше сборки контекста")
    void theHardRungEnforcementStandsBeforeTheContextIsAssembled() {
        openDeals(pairs(1));
        putLiveEntryOrder();
        standAccountRung(ACCOUNT);
        // Коннектор отвергает креды на ЛЮБОЕ чтение: добыча фактов
        // отказывает целиком, и энфорсмент, стоящий ПОСЛЕ сборки
        // контекста, съедался бы этим отказом — сделку увёл бы выделенный
        // перехватчик, то есть без причины и без факта.
        connector.refusesAnything(401, Feed.peerFailure("EXCHANGE_CREDENTIALS_REJECTED"));

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("status")).isEqualTo("ERROR");
        assertThat(deal.get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        assertThat(shutdownEvents()).hasSize(1);
    }

    @Test
    @DisplayName("B2.4 — жёсткая ступень энфорсится каждым проходом, а не один раз")
    void theHardRungIsEnforcedByEveryPassAndNotOnlyAtItsRaise() {
        openDeals(pairs(1));
        putLiveEntryOrder();
        // Ступень встаёт ПОСЛЕ заведения сделки и без каскада подъёма: то
        // же состояние, в котором сделку оставляет восстановительная тропа
        // — активная сделка под уже стоящей ступенью (§шапка класса).
        standAccountRung(ACCOUNT);
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Каскад подъёма — первый ход энфорсмента, а не весь: подбирает
        // такую сделку именно шаг прохода.
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ERROR");
        assertThat(AppLog.since(mark)).contains(ENFORCED);
    }

    @Test
    @DisplayName("B2.5 — при обоих стоящих радиусах причиной пишется биржевая")
    void withBothRadiiStandingTheExchangeReasonIsWritten() {
        openDeals(pairs(1));
        putLiveEntryOrder();
        standAccountRung(ACCOUNT);
        standPairRung(ACCOUNT, INSTRUMENT);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Биржевой радиус старше инструментного, и читает соответствие
        // один читатель на обоих затребователей ребра.
        assertThat(rows.all("deals").getFirst().get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        assertThat(shutdownEvents()).hasSize(1);
    }

    @Test
    @DisplayName("B2.6 — сделка без стоящей ступени шагом энфорсмента не трогается")
    void aDealUnderNoStandingRungIsNotTouchedByTheEnforcementStep() {
        openDeals(pairs(1));
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("status")).isEqualTo("ACTIVE");
        assertThat(deal.get("shutdown_reason")).isNull();
        assertThat(shutdownEvents()).isEmpty();
        assertThat(AppLog.since(mark)).doesNotContain(ENFORCED);
    }

    @Test
    @DisplayName("B2.7 — повторный проход по уведённой сделке причины не переписывает")
    void aSecondPassOverAnAlreadyShutDealDoesNotRewriteItsReason() {
        openDeals(pairs(1));
        putLiveEntryOrder();
        standAccountRung(ACCOUNT);

        ticks(Tick.DEAL_ORCHESTRATOR, 2);

        Map<String, Object> deal = rows.all("deals").getFirst();
        // Сделка стои́т в ошибке оба прохода: живой риск держит её вне
        // аварийного терминала, и второй проход её действительно берёт.
        assertThat(deal.get("status")).isEqualTo("ERROR");
        assertThat(deal.get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        // Второй строки нет: гард ребра отсекает сделку, уже стоящую в
        // ошибке, — ребра у неё больше нет.
        assertThat(shutdownEvents()).hasSize(1);
    }

    @Test
    @DisplayName("B2.8 — перезапись причины на ребре из `EXIT_PENDING` даёт вторую строку журнала")
    void rewritingTheReasonOnTheEdgeFromExitPendingYieldsASecondJournalRow() {
        List<String> instruments = pairs(1);
        Strategy definition = openDeals(instruments).getFirst();
        closeTrancheByFalseCondition(instruments);
        moveDefinition(STRATEGY_DELETED, definition, Strategy.Status.DELETED.name());
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.all("deals").getFirst().get("shutdown_reason")).isEqualTo("STRATEGY_DELETED");
        putLiveEntryOrder();
        standAccountRung(ACCOUNT);

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("status")).isEqualTo("ERROR");
        assertThat(deal.get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        // Событие публикуется на РЕБРЕ, а не однажды на сделку: второе
        // присвоение описывает другое происшествие.
        assertThat(shutdownEvents()).hasSize(2);
    }

    @Test
    @DisplayName("B2.9 — удаление определения уводит сделку в координированный выход")
    void deletingTheDefinitionMovesTheDealIntoTheCoordinatedExit() {
        List<String> instruments = pairs(1);
        Strategy definition = openDeals(instruments).getFirst();
        closeTrancheByFalseCondition(instruments);
        moveDefinition(STRATEGY_DELETED, definition, Strategy.Status.DELETED.name());

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("status")).isEqualTo("EXIT_PENDING");
        assertThat(deal.get("shutdown_reason")).isEqualTo("STRATEGY_DELETED");
        assertThat(shutdownEvents()).hasSize(1);
        // Заявок агрегат сам не снимает: снятие — работа траншей.
        assertThat(rows.count("orders")).isZero();
        assertThat(rows.count("algo_orders")).isZero();
    }

    @Test
    @DisplayName("B2.10 — ошибочное состояние FSM траншей не гоняет")
    void theErroneousStatusDoesNotRunTheTrancheStateMachine() {
        openDealsWithLevels(pairs(1), 2);
        assertThat(rows.count("deal_tranches")).isEqualTo(2L);
        putLiveEntryOrder();
        standAccountRung(ACCOUNT);
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ERROR");
        List<Map<String, Object>> beforePass = rows.all("deal_tranches");
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.all("deal_tranches")).isEqualTo(beforePass);
        // Команд стратегии не порождается: строку исполнения заводит
        // выбранное действие узла, а узлов обработчик ошибки не читает.
        assertThat(rows.count("deal_strategy_action_states")).isZero();
        // Разрешены safety, восстановление и добыча фактов: команд
        // РАЗМЕЩЕНИЯ среди обращений к коннектору нет ни одного.
        assertThat(placementCalls()).isEmpty();
    }

    @Test
    @DisplayName("B2.11 — координированный выход FSM траншей гоняет")
    void theCoordinatedExitRunsTheTrancheStateMachine() {
        openTwoTrancheLiveDeal();
        standExchangeFollowingCommands("-5");
        exitByDeletion(Definitions.withEntryCommandLevelsOnPhase(LiveDealBox.DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, 2));

        passesUntilDealTerminal();

        // Каждый транш прошёл свой выход до терминала, и только после
        // терминальности всех и доказанного отсутствия живого риска сделка
        // встала в свой: без прогона FSM траншей выходная проверка сделки
        // не наступила бы никогда.
        assertThat(tranchesOfDeal()).hasSize(2)
                .allMatch(row -> Objects.equals("CLOSED", row.get("status")));
        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(connector.requests(closurePath(ACCOUNT))).isNotEmpty();
    }

    @Test
    @DisplayName("B2.15 — диспетчеризация прерывается на первом неуспехе")
    void theDispatchStopsAtTheFirstFailure() {
        openCommandDeal(Definitions.withEntryCommandLevelsOnPhase(LiveDealBox.DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, 2));
        if (ordersOfDeal().isEmpty()) {
            tick(Tick.DEAL_ORCHESTRATOR);
        }
        List<Map<String, Object>> legs = ordersOfDeal();
        assertThat(legs).hasSize(2);
        // Площадка отказывает команде ПЕРВОГО транша повторяемым классом и
        // принимает всё, что придёт после. Второй к ней уходит нога ВТОРОГО
        // транша: первый ждёт отката повтора, второй — нет.
        connector.answersInTurn(placementPath(ACCOUNT), List.of(502, 200, 200), List.of(
                Feed.peerFailure("EXCHANGE_UNREACHABLE"),
                Feed.ack(legExternalId(1), String.valueOf(legs.get(1).get("internal_id"))),
                Feed.ack(legExternalId(0), String.valueOf(legs.get(0).get("internal_id")))));
        // Поиск без биржевого идентификатора — восстановление повторной
        // отправки — не находит ничего: первая отправка до площадки не дошла.
        // Принятая нога находится живой.
        connector.answers(lookupPath(ACCOUNT), Feed.absent());
        for (int index = 0; index < legs.size(); index++) {
            connector.answersWhen(lookupPath(ACCOUNT), "externalId", legExternalId(index),
                    Feed.order(legExternalId(index), String.valueOf(legs.get(index).get("internal_id")), "ACTIVE"));
        }
        connector.answers(pendingPath(ACCOUNT), Feed.emptyArray());
        connector.answers(historyPath(ACCOUNT), Feed.emptyArray());
        // Налива нет, позиции тоже: добыча отправленного входа наблюдает её
        // каждым проходом и идёт до работы.
        connector.answers(positionPath(ACCOUNT), Feed.absent());
        List<Object> statusesBefore = tranchesOfDeal().stream().map(row -> row.get("status")).toList();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Команда второго транша в этом проходе не уходила: к площадке одна
        // отправка — отказанная. Переход не применён, статусы прежние.
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
        assertThat(tranchesOfDeal().stream().map(row -> row.get("status")).toList())
                .isEqualTo(statusesBefore);

        // Следующие проходы подбирают оба транша: обе ноги уходят к
        // площадке, сделка остаётся в штатном ведении
        // (docs/components/DealOrchestratorJob.md §«Цикл прохода»).
        passesUntil(() -> connector.requests(placementPath(ACCOUNT)).size() >= 3
                || Objects.equals("ERROR", dealStatus()));
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(3);
    }

    @Test
    @DisplayName("B2.12 — отказ одной сделки проход по остальным не отменяет")
    void aFailureOnOneDealDoesNotCancelThePassOverTheOthers() {
        List<String> instruments = pairs(2);
        openDeals(instruments);
        // Сборка контекста первой сделки роняется: связку фич её
        // инструмента владелец не отдаёт.
        marketData.answers(featuresPath(instruments.getFirst()), 503,
                Feed.peerFailure(PEER_SERVICE_UNAVAILABLE));
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(statusOn(instruments.getFirst())).isEqualTo("ERROR");
        assertThat(statusOn(instruments.get(1))).isEqualTo("ACTIVE");
        // Вторая прошла штатно: её контекст собран, её команда ушла.
        assertThat(marketData.count(featuresPath(instruments.get(1)))).isEqualTo(1);
        assertThat(connector.count(balancePath(accountOf(2)))).isEqualTo(1);
        assertThat(AppLog.since(mark)).contains(PASS_FAILED);
        // Радиусной реакции нет: что именно сломалось, перехватчику
        // неизвестно, и блокировать по этому радиус значило бы соразмерять
        // реакцию с собственным дефектом.
        assertThat(accountRung(ACCOUNT)).isEqualTo(NO_RUNG);
        assertThat(rows.count("account_instrument_states")).isZero();
        assertThat(rows.count("anomaly_reports")).isZero();
    }

    @Test
    @DisplayName("B2.13 — перехват петлёй причины остановки не пишет")
    void theLoopInterceptorWritesNoShutdownReason() {
        List<String> instruments = pairs(2);
        openDeals(instruments);
        marketData.answers(featuresPath(instruments.getFirst()), 503,
                Feed.peerFailure(PEER_SERVICE_UNAVAILABLE));

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.row("deals", "instrument_id",
                instrumentId(instruments.getFirst()));
        assertThat(deal.get("status")).isEqualTo("ERROR");
        // Причина пуста: писателя у тропы перехвата нет по построению, и
        // факта остановки она поэтому не производит тоже.
        assertThat(deal.get("shutdown_reason")).isNull();
        assertThat(shutdownEvents()).isEmpty();
    }

    @Test
    @DisplayName("B2.14 — отказ ребра присвоения причины в ошибку не перехватывается")
    void aFailingShutdownEdgeIsNotInterceptedIntoTheErrorStatus() {
        openDeals(pairs(1));
        putLiveEntryOrder();
        standAccountRung(ACCOUNT);
        Integer mark = AppLog.mark();
        // Роняется ЗАПИСЬ РЕБРА, а не всякая правка сделки: ребро и его
        // факт идут одной транзакцией, и отказ факта откатывает ребро
        // целиком. Отказ на самой строке сделки был бы шире предмета — он
        // заодно погасил бы и прямую запись перехватчика, то есть скрыл бы
        // ровно то, что клетка различает.
        rows.refuse("outbox_events", "insert");
        try {
            tick(Tick.DEAL_ORCHESTRATOR);
        } finally {
            rows.allow("outbox_events", "insert");
        }

        // Сделка осталась в прежнем статусе: перехват увёл бы её в ERROR
        // БЕЗ причины, откуда ребро причины не применилось бы уже никогда.
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ACTIVE");
        assertThat(AppLog.since(mark)).contains(EDGE_FAILED);
        assertThat(shutdownEvents()).isEmpty();

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("status")).isEqualTo("ERROR");
        assertThat(deal.get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        assertThat(shutdownEvents()).hasSize(1);
    }

    @Test
    @DisplayName("B2.18 — частичное применение перехода наблюдаемо и рёбра траншей не откатывает")
    void aPartiallyAppliedTransitionKeepsItsTrancheEdges() {
        List<String> instruments = pairs(1);
        String account = accountOf(1);
        String instrument = instruments.getFirst();
        marketData.answers(featuresPath(instrument), Feed.features(MarketPhase.Type.BULL_TREND.name()));
        connector.answers(balancePath(account), balanceBody());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withPhaseAndGracefullyExpiringDeclarations(DEFINITION, account, instrument,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deal_tranches")).isEqualTo(2L);
        // Первый тик снимает снимок средств; ко второму фаза уже другая, и
        // тем же проходом первый транш закрывается, а второй просит выхода.
        tick(Tick.DEAL_ORCHESTRATOR);
        marketData.answers(featuresPath(instrument), Feed.features(MarketPhase.Type.RANGE.name()));
        Integer mark = AppLog.mark();
        // Роняется ФАКТ ребра, а не строка сделки — довод тот же, что у
        // B2.14: ребро и факт идут одной транзакцией, и отказ факта
        // откатывает ребро целиком.
        rows.refuse("outbox_events", "insert");
        List<Map<String, Object>> afterFirstPass;
        try {
            tick(Tick.DEAL_ORCHESTRATOR);
            afterFirstPass = rows.allOrderedBy("deal_tranches", "id");
            // Рёбра траншей применены своей транзакцией и откатом ребра
            // сделки не задеты: закрыт транш объявления, чей шаг молчит, а
            // просивший выхода остался в предвходовой проверке.
            assertThat(afterFirstPass).extracting(row -> row.get("status") + "/" + row.get("close_reason"))
                    .containsExactlyInAnyOrder("CLOSED/ENTRY_CONDITION_EXPIRED", "PRECHECK/null");
            assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ACTIVE");
            assertThat(rows.all("deals").getFirst().get("shutdown_reason")).isNull();
            assertThat(shutdownEvents()).isEmpty();

            tick(Tick.DEAL_ORCHESTRATOR);
        } finally {
            rows.allow("outbox_events", "insert");
        }

        // Строка журнала — на каждом тике, пока причина стои́т; второй
        // проход считал уже другой граф — закрытый транш каскад не трогал.
        assertThat(occurrences(AppLog.since(mark), EDGE_FAILED)).isEqualTo(2);
        assertThat(rows.allOrderedBy("deal_tranches", "id")).isEqualTo(afterFirstPass);
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ACTIVE");
        assertThat(shutdownEvents()).isEmpty();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Причина снята — ребро сделки применяется, а причина закрытия
        // транша, уже записанная частичным применением, не переписана.
        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("status")).isEqualTo("EXIT_PENDING");
        assertThat(deal.get("shutdown_reason")).isEqualTo("MARKET_DATA_EXPIRED");
        assertThat(shutdownEvents()).hasSize(1);
        assertThat(rows.allOrderedBy("deal_tranches", "id")).extracting(row -> row.get("close_reason"))
                .contains("ENTRY_CONDITION_EXPIRED");
    }

    @Test
    @DisplayName("B2.16 — перекрывающий запуск прохода пропускается молча")
    void anOverlappingRunOfThePassIsSkippedSilently() {
        openDeals(pairs(1));
        // Задержка стои́т на СТАБЕ: перекрытие, пойманное гонкой в тесте,
        // не было бы детерминированным.
        connector.answersSlowly(balancePath(ACCOUNT), balanceBody(), 2000);
        Integer mark = AppLog.mark();

        List<Answer> answers = overlappingTicks(Tick.DEAL_ORCHESTRATOR);

        // Оба ответа 202: фасад отвечает о ЗАПУСКЕ, исход работы наружу не
        // транслируется. Дом при этом обещает 409 на отказе блокировки —
        // находка F-2 документа кейсов.
        assertThat(answers).allMatch(answer -> answer.status() == 202);
        // Работа выполнена один раз: число команд к стабу не удвоено.
        assertThat(connector.count(balancePath(ACCOUNT))).isEqualTo(1);
        assertThat(AppLog.since(mark)).contains(OVERLAP_SKIPPED);
    }

    @Test
    @DisplayName("B2.17 — ревизия живых системных исполнений идёт раз за проход и до обработчика")
    void staleLiveSystemExecutionsAreRevisedOncePerPassAndBeforeTheHandler() {
        openDeals(pairs(1));
        // Строка потраншевого системного исполнения, пережившая свой
        // эпизод. Писателя такому состоянию ящик не имеет: строку заводит
        // текущий эпизод, а следующий номер ставит повторный вход —
        // предмет группы B3.
        putForeignEpisodeExecution();
        Map<String, Object> planted = staleExecution();

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> revised = staleExecution();
        assertThat(revised.get("status")).isEqualTo("SKIPPED");
        assertThat(revised.get("modified_at")).isNotEqualTo(planted.get("modified_at"));
        // Управление обработчику передано ПОСЛЕ ревизии: строка его
        // собственного исполнения заведена тем же проходом. Ассерт прямой
        // по базе — поверхности у строк исполнения нет.
        assertThat(rows.countWhere("deal_system_action_states", "system_action_type",
                "REFRESH_DEAL_CONTEXT_ACTION")).isEqualTo(1L);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Повторный тик её не трогает: живой она уже не является.
        assertThat(staleExecution().get("modified_at")).isEqualTo(revised.get("modified_at"));
    }

    @Test
    @DisplayName("B2.19 — ошибочное состояние без живого риска доходит до аварийного терминала тем же проходом")
    void anErroneousDealWithoutLiveRiskReachesTheEmergencyTerminalInTheSamePass() {
        // Живой заявки у этой сделки нет намеренно: она и есть предмет.
        openDeals(pairs(1));
        standAccountRung(ACCOUNT);

        tick(Tick.DEAL_ORCHESTRATOR);

        Map<String, Object> deal = rows.all("deals").getFirst();
        // Статус ERROR внутри прохода транзиентен: энфорсмент присвоил
        // причину, а обработчик ошибочного состояния тем же проходом
        // затребовал терминал — живой риск доказанно отсутствует.
        assertThat(deal.get("status")).isEqualTo("EMERGENCY_CLOSED");
        assertThat(deal.get("shutdown_reason")).isEqualTo("EXCHANGE_HOLD");
        assertThat(deal.get("close_reason")).isEqualTo("EMERGENCY_CLOSE");
        assertThat(shutdownEvents()).hasSize(1);
        assertThat(eventTypes()).contains(DEAL_CLOSED);
    }

    // ------------------------------------------------------------------
    // Предусловия и наблюдатели группы
    // ------------------------------------------------------------------

    /**
     * Заводит названное число пар «счёт × инструмент» проекциями тропой
     * ящика и отдаёт идентичности инструментов в порядке обхода.
     *
     * <p><b>Пар столько, сколько сделок нужно клетке.</b> Гейт отбора
     * входа закрывает счёт ЦЕЛИКОМ, как только на нём заведена сделка
     * ({@code B1.3}), поэтому вторая сделка одного тика требует второго
     * счёта.
     *
     * @param count сколько пар завести
     */
    private List<String> pairs(Integer count) {
        List<String> accounts = new ArrayList<>();
        List<String> instruments = new ArrayList<>();
        Map<String, String> catalogue = new LinkedHashMap<>();
        for (int index = 1; index <= count; index++) {
            accounts.add(accountOf(index));
            String instrument = "I" + index;
            instruments.add(instrument);
            catalogue.put(instrument, "PAIR" + index + "-USDT-SWAP");
        }
        provision(accounts, catalogue);
        return instruments;
    }

    /** Идентичность счёта по его номеру в раскладке пар. */
    private String accountOf(Integer index) {
        return "A" + index;
    }

    /**
     * Заводит по сделке на каждой паре ТРОПОЙ ЯЩИКА — одним тиком отбора
     * входа — и отдаёт снимки определений в том же порядке.
     *
     * <p>Связка фич несёт фазу, которой требует условие входа; биржевой
     * момент и снимок средств отвечают стабами — без них проход
     * сопровождения упирался бы в отказ добычи раньше своего предмета.
     *
     * @param instruments идентичности инструментов в порядке обхода
     */
    private List<Strategy> openDeals(List<String> instruments) {
        return openDealsWithLevels(instruments, 1);
    }

    /**
     * То же заведение сделок с НАЗВАННЫМ числом уровней входной сетки:
     * транши материализуются эагерно, по уровню на транш.
     *
     * @param instruments идентичности инструментов в порядке обхода
     * @param levels      сколько уровней объявляет каждое определение
     */
    private List<Strategy> openDealsWithLevels(List<String> instruments, Integer levels) {
        List<Strategy> definitions = new ArrayList<>();
        for (int index = 0; index < instruments.size(); index++) {
            String account = accountOf(index + 1);
            String instrument = instruments.get(index);
            marketData.answers(featuresPath(instrument), Feed.features(MarketPhase.Type.BULL_TREND.name()));
            connector.answers(balancePath(account), balanceBody());
            Strategy definition = Definitions.withEntryLevelsOnPhase(DEFINITION + index, account,
                    instrument, MarketPhase.Type.BULL_TREND, levels);
            activate(definition);
            definitions.add(definition);
        }
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        tick(Tick.ENTRY_SCANNER);
        PeerStub.all().forEach(PeerStub::forgetRequests);
        return definitions;
    }

    /**
     * Доводит транш до терминального состояния истёкшим условием входа —
     * тропой ящика, двумя тиками сопровождения.
     *
     * <p><b>Зачем это клеткам о шагах ПОСЛЕ каскада.</b> Обработчик
     * активной сделки выходит на первой же просьбе каскада, а просьбой
     * считается и одобренное ребро: живой транш в предвходовой проверке
     * просит либо снимок средств, либо работу шага, и до агрегатных шагов
     * проход не доходит вовсе. Терминальный транш каскад проходит молча.
     *
     * <p>Первый тик снимает снимок средств, второй — закрывает транш:
     * условие входа спрашивает фазу, а связка отдаёт уже другую.
     *
     * @param instruments идентичности инструментов, чьи связки переводятся
     */
    private void closeTrancheByFalseCondition(List<String> instruments) {
        tick(Tick.DEAL_ORCHESTRATOR);
        instruments.forEach(instrument ->
                marketData.answers(featuresPath(instrument), Feed.features(MarketPhase.Type.RANGE.name())));
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.all("deal_tranches").getFirst().get("status")).isEqualTo("CLOSED");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Обращения к коннектору, которые размещают сущность на площадке. */
    private List<String> placementCalls() {
        return connector.paths().stream()
                .filter(path -> path.endsWith("/orders") || path.endsWith("/algo-orders")
                        || path.endsWith("/attached-protections"))
                .toList();
    }

    /**
     * Ставит СТОЯЩУЮ жёсткую ступень счёта прямой записью — довод у записи
     * назван в шапке класса.
     */
    private void standAccountRung(String accountInternalId) {
        rows.put("update exchange_accounts set safety_rung = ? where internal_id = ?",
                TRADE_BLOCKED, accountInternalId);
    }

    /** Та же ступень на радиусе пары «счёт, инструмент». */
    private void standPairRung(String accountInternalId, String instrumentInternalId) {
        rows.put("""
                insert into account_instrument_states (exchange_account_id, instrument_id, safety_rung)
                values (?, ?, ?)
                """, accountId(accountInternalId), instrumentId(instrumentInternalId), TRADE_BLOCKED);
    }

    /** Ступень счёта, как её видит база. */
    private String accountRung(String accountInternalId) {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", accountInternalId)
                .get("safety_rung"));
    }

    /** Ключи сделок по идентичности их инструмента. */
    private Map<String, Long> dealIdsByInstrument() {
        Map<String, Long> byInstrument = new LinkedHashMap<>();
        rows.select("""
                select d.id as deal_id, i.internal_id as instrument_internal_id
                from deals d join instruments i on i.id = d.instrument_id
                """).forEach(row -> byInstrument.put(String.valueOf(row.get("instrument_internal_id")),
                ((Number) row.get("deal_id")).longValue()));
        return byInstrument;
    }

    /** Статус сделки на названном инструменте. */
    private String statusOn(String instrumentInternalId) {
        return String.valueOf(rows.row("deals", "instrument_id", instrumentId(instrumentInternalId))
                .get("status"));
    }

    /** Прямая правка статуса сделки — довод назван в шапке класса. */
    private void setStatus(Long dealId, String status) {
        rows.put("update deals set status = ? where id = ?", status, dealId);
    }

    /** Сколько раз фрагмент встречается в тексте журнала. */
    private Integer occurrences(String text, String fragment) {
        return text.split(Pattern.quote(fragment), -1).length - 1;
    }

    /** Строки outbox класса «остановка штатного ведения». */
    private List<Map<String, Object>> shutdownEvents() {
        return rows.all("outbox_events").stream()
                .filter(row -> DEAL_SHUTDOWN_INITIATED.equals(String.valueOf(row.get("event_type"))))
                .toList();
    }

    /**
     * Живая входная заявка первого транша прямой записью — довод у записи
     * назван в шапке класса.
     *
     * <p>Ею держится ЖИВОЙ РИСК сделки: пока он не доказанно отсутствует,
     * обработчик ошибочного состояния аварийного терминала не затребует, и
     * сделка остаётся в {@code ERROR} столько проходов, сколько нужно
     * клетке.
     */
    private void putLiveEntryOrder() {
        Map<String, Object> tranche = rows.all("deal_tranches").getFirst();
        rows.put("""
                insert into orders (deal_id, deal_tranche_id, internal_id, status, type, side, size)
                values (?, ?, ?, 'ACTIVE', 'ENTRY', 'BUY', 1)
                """, tranche.get("deal_id"), tranche.get("id"), "live-entry-" + tranche.get("id"));
    }

    /** Живая строка потраншевого системного исполнения чужого эпизода. */
    private void putForeignEpisodeExecution() {
        Map<String, Object> tranche = rows.all("deal_tranches").getFirst();
        rows.put("""
                insert into deal_system_action_states (deal_id, deal_tranche_id, tranche_episode_seq,
                                                       system_action_type, status, created_at, modified_at)
                values (?, ?, ?, 'FINALIZE_DEAL_ENTRY_ACTION', 'PLANNED', ?, ?)
                """, tranche.get("deal_id"), tranche.get("id"), FOREIGN_EPISODE,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    /** Та же строка, как её видит база. */
    private Map<String, Object> staleExecution() {
        return rows.row("deal_system_action_states", "tranche_episode_seq", FOREIGN_EPISODE);
    }
}
