package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.accountContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.deals;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Последовательность полной реакции и её порядок — группа `U5`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/SafetyHoldCoordinator.md §Последовательность).
 *
 * <p><b>Базовая сборка</b> — {@link CoordinatorHarness}: ребро подъёма
 * отвечает «переставилась»; исполнитель снятия риска подтверждает;
 * сервис отчёта принимает все переходы; служба сделок отдаёт одну
 * активную сделку радиуса; ребро энфорсмента принимает вызов.
 *
 * <p><b>Выход предмета — протокол вызовов, а не значение:</b> точка
 * входа возвращает {@code void}, и наблюдаемый результат есть
 * состоявшиеся и несостоявшиеся вызовы коллабораторов, их аргументы и
 * относительный порядок.
 */
class FullReactionSequenceTest {

    private final CoordinatorHarness harness = new CoordinatorHarness();

    /**
     * Пять шагов дома идут в объявленном относительном порядке. Клейма
     * «иного вызова между ними нет» кейс не несёт: дом его не объявляет,
     * а между шагами лежат записи статусов отчёта (U5.5) и чтение
     * популяции каскада с резолвом причины (U5.12).
     */
    @Test
    @DisplayName("U5.1 — жёсткий инструментный сигнал: пять шагов в объявленном относительном порядке")
    void u5_1_theFiveStepsRunInTheDeclaredOrder() {
        harness.cascadeOnPair(deals(deal(21L)));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        InOrder order = inOrder(harness.pairStates, harness.reports, harness.killSwitchService,
                harness.statusEdges);
        order.verify(harness.pairStates).raiseRung(anyLong(), anyLong(), any());
        order.verify(harness.reports).open(any(), any());
        order.verify(harness.killSwitchService).fireInstrument(any());
        order.verify(harness.reports).complete(any(), any());
        order.verify(harness.statusEdges).enforceHardRung(any(), any());
    }

    /** Тот же порядок на счётном радиусе; каскад собран по счёту, а не по паре. */
    @Test
    @DisplayName("U5.2 — жёсткий счётный сигнал: тот же порядок, снятие риска и каскад счётного радиуса")
    void u5_2_theAccountScopeKeepsTheSameOrder() {
        harness.cascadeOnAccount(deals(deal(22L)));

        harness.coordinator.react(HoldSignal.exchangeAccount(CODE), accountContext());

        InOrder order = inOrder(harness.accounts, harness.reports, harness.killSwitchService,
                harness.statusEdges);
        order.verify(harness.accounts).raiseRung(anyLong(), any());
        order.verify(harness.reports).open(any(), any());
        order.verify(harness.killSwitchService).fireExchangeAccount(ACCOUNT_ID);
        order.verify(harness.reports).complete(any(), any());
        order.verify(harness.statusEdges).enforceHardRung(any(), any());
        verify(harness.deals).findCascadeSourceOnAccount(ACCOUNT_ID);
        verify(harness.deals, never()).findCascadeSourceOnPair(anyLong(), anyLong());
    }

    /** Снимок «до» существует только при этом порядке. */
    @Test
    @DisplayName("U5.3 — отчёт создан до первого вызова снятия риска")
    void u5_3_theReportIsOpenedBeforeTheTeardown() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        InOrder order = inOrder(harness.reports, harness.killSwitchService);
        order.verify(harness.reports).open(any(), any());
        order.verify(harness.killSwitchService).fireInstrument(any());
    }

    /** Каскад начат после терминала отчёта, а не параллельно ему. */
    @Test
    @DisplayName("U5.4 — каскад начат после терминала отчёта")
    void u5_4_theCascadeStartsAfterTheReportTerminal() {
        harness.cascadeOnPair(deals(deal(23L)));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        InOrder order = inOrder(harness.reports, harness.statusEdges);
        order.verify(harness.reports).complete(any(), any());
        order.verify(harness.statusEdges).enforceHardRung(any(), any());
    }

    /** Статусы отчёта идут матрицей переходов жизненного цикла. */
    @Test
    @DisplayName("U5.5 — статусы отчёта: создан → в обработке → снятие риска → завершён")
    void u5_5_theReportStatusesFollowTheTransitionMatrix() {
        DealContext context = pairContext();

        harness.coordinator.react(HoldSignal.instrument(CODE), context);

        InOrder order = inOrder(harness.reports);
        order.verify(harness.reports).open(context, HoldSignal.instrument(CODE));
        order.verify(harness.reports).advance(harness.report, AnomalyReport.Status.IN_PROGRESS);
        order.verify(harness.reports).advance(harness.report, AnomalyReport.Status.KILL_SWITCH_EXECUTED);
        order.verify(harness.reports).complete(harness.report, context);
    }

    /** Отказ создания отчёта снятие риска не подавляет; записи становятся холостыми. */
    @Test
    @DisplayName("U5.6 — создание отчёта бросает: снятие риска гоняется, записи холостые, исключения наружу нет")
    void u5_6_aFailingReportOpenDoesNotGateTheTeardown() {
        when(harness.reports.open(any(), any())).thenThrow(new IllegalStateException("db is down"));

        assertThatCode(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                .as("U5.6: отказ журнального носителя реакцию не гейтит")
                .doesNotThrowAnyException();

        verify(harness.killSwitchService).fireInstrument(any());
        verify(harness.reports, never()).advance(any(), any());
        verify(harness.reports, never()).complete(any(), any());
    }

    /** Отказ снятия риска помечает отчёт ошибкой; каскад — локальная запись — идёт. */
    @Test
    @DisplayName("U5.7 — снятие риска бросает: отчёт получает ошибку, исключения наружу нет, каскад идёт")
    void u5_7_aThrowingTeardownFailsTheReportButNotThePass() {
        harness.cascadeOnPair(deals(deal(24L)));
        when(harness.killSwitchService.fireInstrument(any()))
                .thenThrow(new IllegalStateException("exchange is down"));

        assertThatCode(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                .doesNotThrowAnyException();

        verify(harness.reports).fail(harness.report, "exchange is down");
        verify(harness.statusEdges).enforceHardRung(any(), any());
    }

    /** Отказ ребра подъёма уходит вызывающему: применённого на шаге нет. */
    @Test
    @DisplayName("U5.8 — ребро подъёма бросает: ни отчёта, ни снятия риска, ни каскада")
    void u5_8_aFailingRungEdgeStopsTheWholeReaction() {
        doThrow(new IllegalStateException("outbox is down"))
                .when(harness.coreEventWriter).holdRaised(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                .isInstanceOf(IllegalStateException.class);

        verify(harness.reports, never()).open(any(), any());
        verify(harness.killSwitchService, never()).fireInstrument(any());
        verify(harness.statusEdges, never()).enforceHardRung(any(), any());
    }

    /** Причину каскад не выводит из радиуса своего сигнала. */
    @Test
    @DisplayName("U5.9 — сделка под счётной ступенью при инструментном сигнале: ребро получает биржевую причину")
    void u5_9_theCascadeReasonComesFromTheStandingRung() {
        Deal underAccountRung = deal(25L);
        harness.cascadeOnPair(deals(underAccountRung));
        when(harness.deals.findIdsUnderAccountRung(any())).thenReturn(List.of(25L));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.statusEdges).enforceHardRung(underAccountRung,
                Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** Отказ одной сделки каскад не останавливает: сделки независимы. */
    @Test
    @DisplayName("U5.10 — ребро бросает на первой сделке: остальные пройдены, отказ записан в лог")
    void u5_10_aFailingEdgeLeavesTheRestToTheCascade() {
        Deal first = deal(26L);
        Deal second = deal(27L);
        harness.cascadeOnPair(deals(first, second));
        when(harness.statusEdges.enforceHardRung(first, Deal.ShutdownReason.RISK_POLICY))
                .thenThrow(new IllegalStateException("edge is down"));

        try (SafetyLogCapture log = SafetyLogCapture.attach(SafetyHoldCoordinator.class)) {
            assertThatCode(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                    .doesNotThrowAnyException();

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("Hard rung cascade failed on a deal"));
        }
        verify(harness.statusEdges).enforceHardRung(second, Deal.ShutdownReason.RISK_POLICY);
    }

    /** Каскад идёт по сделке за раз тем же ребром, что и шаг прохода. */
    @Test
    @DisplayName("U5.11 — каскад по перечню: ребро энфорсмента позвано по разу на сделку")
    void u5_11_theCascadeCallsTheEdgeOncePerDeal() {
        Deal first = deal(28L);
        Deal second = deal(29L);
        Deal third = deal(30L);
        harness.cascadeOnPair(deals(first, second, third));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.statusEdges, times(3)).enforceHardRung(any(), any());
        verify(harness.statusEdges).enforceHardRung(first, Deal.ShutdownReason.RISK_POLICY);
        verify(harness.statusEdges).enforceHardRung(second, Deal.ShutdownReason.RISK_POLICY);
        verify(harness.statusEdges).enforceHardRung(third, Deal.ShutdownReason.RISK_POLICY);
    }

    /** Пустой радиус выборку и резолв не гасит; предыдущие шаги отработали полностью. */
    @Test
    @DisplayName("U5.12 — активных сделок радиуса нет: ребро не позвано, выборка и резолв позваны по разу")
    void u5_12_anEmptyCascadePopulationStillAsksItsQueries() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.statusEdges, never()).enforceHardRung(any(), any());
        verify(harness.deals, times(1)).findCascadeSourceOnPair(ACCOUNT_ID, INSTRUMENT_ID);
        verify(harness.deals, times(1)).findIdsUnderAccountRung(List.of());
        verify(harness.deals, times(1)).findIdsUnderInstrumentRung(List.of());
        verify(harness.reports).complete(any(), any());
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * жизненного цикла объявляет статус {@code KILL_SWITCH_EXECUTED}
     * подтверждением снятия риска (docs/lifecycles/AnomalyReport.md
     * §Статусы), а координатор ставит его до того, как посмотрел на исход
     * снятия. Красный прогон и есть предъявление находки `S-1`
     * (`.claude/work/backlog.md` §«Статус подтверждённого снятия риска
     * ставится и на неподтверждённом»).
     *
     * <p>Счётный радиус взят потому, что эскалировать с него некуда: ось
     * кейса — только статус отчёта.
     */
    @Test
    @Tag("debt")
    @DisplayName("U5.13 — снятие риска не подтверждено: статуса подтверждённого снятия отчёт не получает")
    void u5_13_theConfirmedTeardownStatusIsNotSetOnAnUnconfirmedTeardown() {
        when(harness.killSwitchService.fireExchangeAccount(anyLong())).thenReturn(false);

        harness.coordinator.react(HoldSignal.exchangeAccount(CODE), accountContext());

        verify(harness.reports, never())
                .advance(harness.report, AnomalyReport.Status.KILL_SWITCH_EXECUTED);
    }
}
