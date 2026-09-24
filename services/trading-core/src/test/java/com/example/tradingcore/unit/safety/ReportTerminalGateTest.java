package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACTOR;
import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.TENANT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.accountContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.deals;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Гейт терминала отчёта и эскалация радиуса — группа `U7` документа
 * `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/SafetyHoldCoordinator.md §«Гейт терминала
 * отчёта»).
 *
 * <p><b>Базовая сборка</b> — {@link CoordinatorHarness}; ось группы —
 * исход снятия риска.
 *
 * <p><b>Отчёты двух реакций разведены намеренно:</b> эскалация открывает
 * СВОЙ отчёт тем же сервисом, и без разведения ожидание «отчёт
 * инструментной реакции терминала не получает» гасилось бы терминалом
 * счётного.
 */
class ReportTerminalGateTest {

    private final CoordinatorHarness harness = new CoordinatorHarness();

    private final AnomalyReport instrumentReport = CoordinatorHarness.report(601L);

    private final AnomalyReport accountReport = CoordinatorHarness.report(602L);

    @BeforeEach
    void separateReports() {
        when(harness.reports.open(any(), any())).thenReturn(instrumentReport, accountReport);
    }

    /** Подтверждённое снятие риска терминализует отчёт; эскалации нет. */
    @Test
    @DisplayName("U7.1 — снятие риска подтверждено: отчёт завершён со снимком «после», эскалации нет")
    void u7_1_aConfirmedTeardownCompletesTheReport() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.reports).complete(eq(instrumentReport), any());
        verify(harness.accounts, never()).raiseRung(anyLong(), any());
        verify(harness.killSwitchService, never()).fireExchangeAccount(anyLong());
    }

    /** Неустранимый остаток на инструменте — эскалация на счётный радиус. */
    @Test
    @DisplayName("U7.2 — инструментный радиус, снятие не подтверждено: эскалация, отчёт инструментной реакции не закрыт")
    void u7_2_anUnconfirmedInstrumentTeardownEscalates() {
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(harness.killSwitchService).fireExchangeAccount(ACCOUNT_ID);
        verify(harness.reports, never()).complete(eq(instrumentReport), any());
    }

    /** Содержимое факта эскалации несёт её собственный радиус и код. */
    @Test
    @DisplayName("U7.3 — то же: содержимое факта эскалации несёт счётный радиус и код эскалации")
    void u7_3_theEscalationFactCarriesItsOwnScopeAndCode() {
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        ArgumentCaptor<HoldSignal> signal = ArgumentCaptor.forClass(HoldSignal.class);
        verify(harness.coreEventWriter).holdRaised(eq(TENANT_ID), signal.capture(),
                eq(ACCOUNT_INTERNAL_ID), eq(null), eq(ACTOR));
        assertThat(signal.getValue().getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(signal.getValue().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(signal.getValue().getCode())
                .as("код эскалации, а не код исходного сигнала")
                .isEqualTo(Constants.Hold.EXCHANGE_KILL_SWITCH_RESIDUAL);
    }

    /** Эскалация — автоматический ход: стоящая счётная ступень её поглощает штатно. */
    @Test
    @DisplayName("U7.4 — эскалация на счёте со стоящей ступенью: поглощается, счётного снятия риска нет")
    void u7_4_theEscalationIsAbsorbedByAStandingAccountRung() {
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);
        when(harness.accounts.raiseRung(anyLong(), any())).thenReturn(false);

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.killSwitchService, never()).fireExchangeAccount(anyLong());
    }

    /** Ход состоялся — и свой факт подъёма эскалация пишет тем же ребром. */
    @Test
    @DisplayName("U7.5 — эскалация на счёте без стоящей ступени: ступень переставлена, факт написан")
    void u7_5_theEscalationRaisesTheAccountRungAndWritesItsFact() {
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(harness.coreEventWriter, times(2))
                .holdRaised(any(), any(), any(), any(), any());
    }

    /** На счётном радиусе эскалировать некуда: незакрытый отчёт — исход по назначению. */
    @Test
    @DisplayName("U7.6 — счётный радиус, снятие не подтверждено: отчёт остаётся незакрытым, в логе запись")
    void u7_6_anUnconfirmedAccountTeardownKeepsTheReportOpen() {
        when(harness.killSwitchService.fireExchangeAccount(anyLong())).thenReturn(false);

        try (SafetyLogCapture log = SafetyLogCapture.attach(SafetyHoldCoordinator.class)) {
            harness.coordinator.react(HoldSignal.exchangeAccount(CODE), accountContext());

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("the anomaly report is kept open"));
        }
        verify(harness.reports, never()).complete(any(), any());
        verify(harness.accounts, times(1)).raiseRung(anyLong(), any());
    }

    /** Вложенность порядка наблюдаема: каскад инструмента идёт после всей счётной реакции. */
    @Test
    @DisplayName("U7.7 — эскалация: каскад инструментного радиуса идёт после всей счётной реакции")
    void u7_7_theInstrumentCascadeFollowsTheWholeAccountReaction() {
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);
        harness.cascadeOnAccount(deals(deal(41L)));
        harness.cascadeOnPair(deals(deal(42L)));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        InOrder order = inOrder(harness.deals);
        order.verify(harness.deals).findCascadeSourceOnAccount(ACCOUNT_ID);
        order.verify(harness.deals).findCascadeSourceOnPair(anyLong(), anyLong());
    }

    /**
     * Политика отказов объявляет отказ ребра подъёма уходящим вызывающему
     * (docs/components/SafetyHoldCoordinator.md §«Политика отказов»), и
     * на тропе эскалации тоже: эскалация стоит вне перехвата отказа
     * снятия риска, иначе счётная ступень оставалась бы не поднятой, а
     * повторить эскалацию было бы некому.
     */
    @Test
    @DisplayName("U7.8 — эскалация, ребро подъёма счётной ступени бросает: отказ уходит вызывающему")
    void u7_8_aFailingEscalationEdgeMustFailTheCall() {
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);
        doThrow(new IllegalStateException("outbox is down"))
                .when(harness.accounts).raiseRung(anyLong(), any());

        assertThatThrownBy(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                .as("U7.8: объявлять реакцию отработавшей нечем")
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Упавшее снятие риска на инструменте — неподтверждённое: эскалация на
     * счётный радиус идёт так же, как на ответе «не подтверждено».
     */
    @Test
    @DisplayName("U7.11 — инструментный радиус, снятие риска бросает: отчёт с ошибкой, эскалация на счётный")
    void u7_11_aThrowingInstrumentTeardownEscalates() {
        when(harness.killSwitchService.fireInstrument(any()))
                .thenThrow(new IllegalStateException("exchange is down"));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.reports).fail(instrumentReport, "exchange is down");
        verify(harness.accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(harness.killSwitchService).fireExchangeAccount(ACCOUNT_ID);
        verify(harness.reports, never()).complete(eq(instrumentReport), any());
    }

    /** Отказ журнального носителя реакцию не гейтит. */
    @Test
    @DisplayName("U7.9 — снятие подтверждено, терминал отчёта бросает: исключения наружу нет, каскад идёт")
    void u7_9_aFailingReportTerminalDoesNotGateTheCascade() {
        harness.cascadeOnPair(deals(deal(43L)));
        when(harness.reports.complete(any(), any()))
                .thenThrow(new IllegalStateException("db is down"));

        assertThatCode(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                .doesNotThrowAnyException();

        verify(harness.statusEdges).enforceHardRung(any(), any());
    }

    /** Операнд эскалации — исход снятия риска, а не наличие строки отчёта. */
    @Test
    @DisplayName("U7.10 — отчёт не открылся, снятие не подтверждено на инструменте: эскалация идёт всё равно")
    void u7_10_theEscalationOperandIsTheTeardownOutcome() {
        when(harness.reports.open(any(), any())).thenThrow(new IllegalStateException("db is down"));
        when(harness.killSwitchService.fireInstrument(any())).thenReturn(false);

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(harness.killSwitchService).fireExchangeAccount(ACCOUNT_ID);
    }
}
