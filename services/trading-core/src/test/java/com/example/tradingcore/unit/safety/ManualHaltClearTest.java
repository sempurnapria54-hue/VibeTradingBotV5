package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_EXTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.dealWithLiveRisk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.ManualHaltClass;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Ручное снятие: цель ступени, прыжок и предусловие — группа `U13`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/rules/manual-halt.md §«Снятие: что во что переходит и при
 * каком предусловии»; исполнимая форма — docs/spec/manual-halt.json,
 * величина {@code clearanceTarget}).
 *
 * <p><b>Базовая сборка</b> — {@link ManualHaltHarness}; объекты стоя́т в
 * названной ступени, гейт терминала подтверждает отсутствие живого
 * риска на радиусе.
 */
class ManualHaltClearTest {

    private final ManualHaltHarness harness = new ManualHaltHarness();

    private HoldSignal journalledSignal() {
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(harness.reports).journal(any(), captor.capture());
        return captor.getValue();
    }

    /** Биржевое сворачивание снимается в мягкую ступень: два хода вместо одного. */
    @Test
    @DisplayName("U13.1 — снятие полного класса на счёте: цель — мягкая ступень счёта")
    void u13_1_theAccountFullClearanceTargetsTheSoftRung() {
        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(harness.accounts).clearRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED,
                ExchangeAccount.SafetyRung.HOLD);
    }

    /** Мягкая ступень счёта снимается в рабочее состояние. */
    @Test
    @DisplayName("U13.2 — снятие мягкого класса на счёте: цель — рабочее состояние счёта")
    void u13_2_theAccountSoftClearanceTargetsTheWorkingState() {
        harness.manualHalt.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(harness.accounts).clearRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD,
                ExchangeAccount.SafetyRung.ACTIVE);
    }

    /**
     * Две ступени — два хода и на паре: жёсткая снимается в мягкий запрет
     * входов, а не в рабочее состояние. Одноходовое снятие пары вернуло бы
     * вход в торговлю одним нажатием, минуя второе условие снятия.
     */
    @Test
    @DisplayName("U13.3 — снятие полного класса на паре: цель — мягкий запрет входов пары")
    void u13_3_thePairFullClearanceTargetsTheSoftRung() {
        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        verify(harness.pairStates).clearRung(ACCOUNT_ID, INSTRUMENT_ID,
                Instrument.SafetyRung.TRADE_BLOCKED, Instrument.SafetyRung.ENTRY_BLOCKED);
        verify(harness.pairStates, never()).clearRung(anyLong(), anyLong(),
                eq(Instrument.SafetyRung.TRADE_BLOCKED), eq(Instrument.SafetyRung.ACTIVE));
    }

    /** Мягкая ступень пары снимается в рабочее состояние. */
    @Test
    @DisplayName("U13.4 — снятие мягкого класса на паре: цель — рабочее состояние пары")
    void u13_4_thePairSoftClearanceTargetsTheWorkingState() {
        harness.manualHalt.clear(ManualHaltClass.SOFT, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        verify(harness.pairStates).clearRung(ACCOUNT_ID, INSTRUMENT_ID,
                Instrument.SafetyRung.ENTRY_BLOCKED, Instrument.SafetyRung.ACTIVE);
    }

    /** Прыжка через ступень нет: снимать мягкую нечего, пока над ней стои́т жёсткая. */
    @Test
    @DisplayName("U13.5 — снятие мягкого класса на объекте под жёсткой ступенью: отказ при запуске")
    void u13_5_clearingTheSoftRungUnderAHardOneIsRefused() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Предусловие машинное, а не заявляемое. */
    @Test
    @DisplayName("U13.6 — снятие полного класса при живом риске на радиусе: отказ при запуске")
    void u13_6_clearingTheHardRungOverLiveRiskIsRefused() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        liveRiskRemains();

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Риск погашен — снятие применено, и строка журнала заведена кодом снятия. */
    @Test
    @DisplayName("U13.7 — снятие полного класса при погашенном риске: снятие применено, строка заведена кодом снятия")
    void u13_7_aClearedRiskLetsTheClearanceThrough() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);

        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(harness.accounts).clearRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED,
                ExchangeAccount.SafetyRung.HOLD);
        HoldSignal signal = journalledSignal();
        assertThat(signal.getCode()).isEqualTo(Constants.Hold.MANUAL_HALT_CLEARED);
        assertThat(signal.getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
    }

    /** Названная ступень не стои́т — ничего не произошло. */
    @Test
    @DisplayName("U13.8 — названная ступень не стои́т: холостой ход, строки журнала нет, отказа тоже нет")
    void u13_8_aNoOpClearanceWritesNothing() {
        when(harness.accounts.clearRung(anyLong(), any(), any())).thenReturn(false);

        assertThatCode(() -> harness.manualHalt.clear(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, null))
                .doesNotThrowAnyException();

        verify(harness.reports, never()).journal(any(), any());
        verify(harness.reports, never()).journalState(any(), any(), any());
    }

    /** Строк ровно столько, сколько применённых снятий. */
    @Test
    @DisplayName("U13.9 — применённое снятие, следом второе такое же: первое дало строку, второе холостое")
    void u13_9_theRowCountEqualsTheAppliedClearanceCount() {
        when(harness.accounts.clearRung(anyLong(), any(), any())).thenReturn(true, false);

        harness.manualHalt.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);
        harness.manualHalt.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(harness.reports, times(1)).journal(any(), any());
    }

    /** У снятия природа факта — происшествие, и дедуп к нему не применяется. */
    @Test
    @DisplayName("U13.10 — применённое снятие: строка заведена писателем происшествия, а не состояния")
    void u13_10_theClearanceRowIsAnIncident() {
        harness.manualHalt.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(harness.reports).journal(any(), any());
        verify(harness.reports, never()).journalState(any(), any(), any());
    }

    /** Исход сделки счётчик двигает, а снятие холда — нет. */
    @Test
    @DisplayName("U13.11 — применённое снятие на счёте: счётчик серии убытков не двигается")
    void u13_11_theClearanceDoesNotTouchTheLossStreak() {
        harness.manualHalt.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(harness.accounts, never()).applyLossStreak(anyLong(), any());
    }

    /**
     * Выборка предусловия идёт по радиусу и берёт только нетерминальные
     * сделки: терминальная сделка с недоказанным отсутствием риска
     * недостижима — оба терминальных ребра гейтятся тем же предикатом.
     */
    @Test
    @DisplayName("U13.12 — предусловие живого риска: выборка по радиусу, только нетерминальные сделки")
    void u13_12_thePreconditionSelectionIsPerScope() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);

        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);
        verify(harness.deals).findNonTerminalByExchangeAccountId(ACCOUNT_ID);

        harness.instrumentStands(Instrument.SafetyRung.TRADE_BLOCKED, Instrument.Status.ACTIVE);
        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);
        verify(harness.deals).findNonTerminalOnPair(ACCOUNT_ID, INSTRUMENT_ID);
        verifyNoMoreInteractions(harness.deals);
    }

    /** Первый неподтверждённый решает: обход остальных не нужен. */
    @Test
    @DisplayName("U13.13 — одна сделка радиуса риска не доказала: отказ, обход остальных не нужен")
    void u13_13_theFirstUnprovenDealDecides() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        Deal first = dealWithLiveRisk(81L);
        Deal second = dealWithLiveRisk(82L);
        when(harness.deals.findNonTerminalByExchangeAccountId(anyLong()))
                .thenReturn(List.of(first, second));
        when(harness.deals.findNonTerminalOnPair(anyLong(), anyLong()))
                .thenReturn(List.of(first, second));
        when(harness.contexts.build(any())).thenReturn(DealContext.builder().deal(first).build());
        when(harness.terminalGate.riskProvenAbsent(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(harness.contexts, times(1)).build(any());
    }

    /**
     * Снятие двигает только ступень пары: онбординговый статус инструмента —
     * другое поле в другой базе, и права торговать не выдаёт отбор входа,
     * берущий только онбординговый рабочий статус (docs/rules/manual-halt.md).
     * Цель поэтому та же, что у {@code U13.3}, — мягкий запрет входов пары.
     */
    @Test
    @DisplayName("U13.14 — снятие полного класса на паре с незавершённым онбордингом: цель — мягкий запрет входов пары")
    void u13_14_anUnfinishedOnboardingDoesNotChangeThePairClearanceTarget() {
        harness.instrumentStands(Instrument.SafetyRung.TRADE_BLOCKED, Instrument.Status.SYNC);

        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        verify(harness.pairStates).clearRung(ACCOUNT_ID, INSTRUMENT_ID,
                Instrument.SafetyRung.TRADE_BLOCKED, Instrument.SafetyRung.ENTRY_BLOCKED);
        verify(harness.instruments, never()).upsertProjection(any(), any(), any());
    }

    /**
     * Снятие и его строка — одна транзакция: отказ записи строки роняет
     * операцию, и откат транзакции оставляет ступень стоять
     * (docs/rules/manual-halt.md §«Наблюдаемость: ручное отличимо и от
     * автоматики, и друг от друга»). Откат здесь не наблюдается — у предмета
     * нет транзакции; наблюдается, что отказ не поглощён.
     */
    @Test
    @DisplayName("U13.15 — запись строки снятия бросает: операция падает, отказ не поглощён")
    void u13_15_aFailingClearanceJournalFailsTheOperation() {
        IllegalStateException journalDown = new IllegalStateException("db is down");
        when(harness.reports.journal(any(), any())).thenThrow(journalDown);

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, null))
                .isSameAs(journalDown);
    }

    /** Снятие идёт своей тропой и через общую точку входа не проходит. */
    @Test
    @DisplayName("U13.16 — снятие любого класса: сервис блокировки не позван ни разу")
    void u13_16_theClearancePathNeverEntersTheHoldService() {
        harness.manualHalt.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);
        harness.manualHalt.clear(ManualHaltClass.SOFT, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(harness.holdService, never()).raiseManual(any(), any());
    }

    /**
     * Пятый признак живого риска читается срезом позиций радиуса: позиция,
     * которую не объясняет ни одна нетерминальная сделка, снятие
     * сворачивания отвергает — сделки радиуса риска не несут, а на площадке
     * он есть.
     */
    @Test
    @DisplayName("U13.17 — снятие полного класса, сделок радиуса нет, в срезе живая позиция: отказ при запуске")
    void u13_17_aLivePositionOutsideTheDealGraphRefusesTheClearance() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        when(harness.slices.livePositions(any(), any()))
                .thenReturn(new ArrayList<>(List.of(livePosition(INSTRUMENT_EXTERNAL_ID))));

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(harness.accounts, never()).clearRung(anyLong(), any(), any());
        verify(harness.reports, never()).journal(any(), any());
    }

    /** Не добытый срез отсутствия риска не доказывает: пустота ступень не снимает. */
    @Test
    @DisplayName("U13.18 — снятие полного класса, срез позиций не добыт: отказ при запуске")
    void u13_18_anUnfetchedSliceRefusesTheClearance() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        when(harness.slices.livePositions(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(harness.accounts, never()).clearRung(anyLong(), any(), any());
    }

    /**
     * Срез читается последним: сделка, риска не доказавшая, решает раньше,
     * и к площадке снятие тогда не ходит вовсе.
     */
    @Test
    @DisplayName("U13.19 — сделка радиуса риска не доказала: отказ, срез позиций не читается")
    void u13_19_aRefusingDealLeavesTheSliceUnread() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        liveRiskRemains();

        assertThatThrownBy(() -> harness.manualHalt.clear(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(harness.slices, never()).livePositions(any(), any());
    }

    /**
     * Повторное полное снятие на паре, уже спущенной в мягкий запрет входов,
     * по лестнице не шагает: жёсткая ступень не стои́т, предусловия нет, и
     * снятие холостое — ни чтения площадки, ни строки журнала.
     */
    @Test
    @DisplayName("U13.20 — второе полное снятие на паре в мягком запрете входов: холостой ход")
    void u13_20_aSecondFullClearanceOnTheSoftPairIsANoOp() {
        harness.instrumentStands(Instrument.SafetyRung.ENTRY_BLOCKED, Instrument.Status.ACTIVE);
        when(harness.pairStates.clearRung(anyLong(), anyLong(), any(), any())).thenReturn(false);

        assertThatCode(() -> harness.manualHalt.clear(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID))
                .doesNotThrowAnyException();

        verify(harness.pairStates).clearRung(ACCOUNT_ID, INSTRUMENT_ID,
                Instrument.SafetyRung.TRADE_BLOCKED, Instrument.SafetyRung.ENTRY_BLOCKED);
        verify(harness.pairStates, never()).clearRung(anyLong(), anyLong(),
                eq(Instrument.SafetyRung.ENTRY_BLOCKED), eq(Instrument.SafetyRung.ACTIVE));
        verify(harness.slices, never()).livePositions(any(), any());
        verify(harness.reports, never()).journal(any(), any());
    }

    /** Срез — счёт целиком, у радиуса пары суженный его инструментом. */
    @Test
    @DisplayName("U13.21 — срез позиций предусловия: счёт целиком на счёте, инструмент пары на паре")
    void u13_21_theSliceIsNarrowedByThePairInstrumentOnly() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);
        verify(harness.slices).livePositions(any(), isNull());

        harness.instrumentStands(Instrument.SafetyRung.TRADE_BLOCKED, Instrument.Status.ACTIVE);
        harness.manualHalt.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);
        verify(harness.slices).livePositions(any(), eq(INSTRUMENT_EXTERNAL_ID));
    }

    private static Position livePosition(String externalInstrumentId) {
        Position position = new Position();
        position.setExternalInstrumentId(externalInstrumentId);
        position.setExternalSize(BigDecimal.ONE);
        return position;
    }

    private void liveRiskRemains() {
        Deal deal = dealWithLiveRisk(83L);
        when(harness.deals.findNonTerminalByExchangeAccountId(anyLong()))
                .thenReturn(List.of(deal));
        when(harness.deals.findNonTerminalOnPair(anyLong(), anyLong()))
                .thenReturn(List.of(deal));
        when(harness.contexts.build(deal)).thenReturn(DealContext.builder().deal(deal).build());
        when(harness.terminalGate.riskProvenAbsent(any(), any(), any())).thenReturn(false);
    }
}
