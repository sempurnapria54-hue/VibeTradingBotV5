package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_INTERNAL_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.ManualHaltClass;
import com.example.tradingcore.domain.safety.ManualHaltService;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Ручная пара «радиус × класс» и отказы при запуске — группа `U12`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/spec/manual-halt.json, величина {@code launchRefused};
 * пары — docs/rules/manual-halt.md §«Параметры вызова — существующие
 * поля сигнала»).
 *
 * <p><b>Базовая сборка</b> — {@link ManualHaltHarness}.
 */
class ManualHaltRaiseTest {

    private final ManualHaltHarness harness = new ManualHaltHarness();

    private HoldSignal raisedSignal() {
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(harness.holdService).raise(captor.capture(), any());
        return captor.getValue();
    }

    private DealContext raisedContext() {
        ArgumentCaptor<DealContext> captor = ArgumentCaptor.forClass(DealContext.class);
        verify(harness.holdService).raise(any(), captor.capture());
        return captor.getValue();
    }

    /** Пара «мягкий класс инструмента» собирается той же фабрикой, что и автоматика. */
    @Test
    @DisplayName("U12.1 — мягкий класс инструмента с инструментом: мягкая инструментная фабрика, координатор не позван")
    void u12_1_theSoftInstrumentPair() {
        harness.manualHalt.raise(ManualHaltClass.SOFT, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.instrumentSoft(Constants.Hold.MANUAL_HALT_REQUESTED));
        verify(harness.coordinator, never()).react(any(), any(), any());
    }

    /** Мягкий класс счёта: контекст несёт только счёт. */
    @Test
    @DisplayName("U12.2 — мягкий класс счёта без инструмента: мягкая счётная фабрика, контекст несёт только счёт")
    void u12_2_theSoftAccountPair() {
        harness.manualHalt.raise(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.exchangeAccountSoft(Constants.Hold.MANUAL_HALT_REQUESTED));
        assertThat(raisedContext().getInstrument()).isNull();
    }

    /** Полный класс на радиусе пары. */
    @Test
    @DisplayName("U12.3 — полный класс с названным инструментом: жёсткая инструментная фабрика")
    void u12_3_theFullInstrumentPair() {
        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.instrument(Constants.Hold.MANUAL_HALT_REQUESTED));
    }

    /** Полный класс на счётном радиусе. */
    @Test
    @DisplayName("U12.4 — полный класс без инструмента: жёсткая счётная фабрика")
    void u12_4_theFullAccountPair() {
        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.exchangeAccount(Constants.Hold.MANUAL_HALT_REQUESTED));
    }

    /** Недопустимой пары фабрики нет — отказ при запуске. */
    @Test
    @DisplayName("U12.5 — мягкий класс инструмента без инструмента: отказ при запуске")
    void u12_5_theSoftInstrumentClassNeedsAnInstrument() {
        assertThatThrownBy(() -> harness.manualHalt.raise(ManualHaltClass.SOFT,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** То же в обратную сторону. */
    @Test
    @DisplayName("U12.6 — мягкий класс счёта с инструментом: отказ при запуске той же причины")
    void u12_6_theSoftAccountClassTakesNoInstrument() {
        assertThatThrownBy(() -> harness.manualHalt.raise(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Код один на все четыре пары и оба радиуса. */
    @Test
    @DisplayName("U12.7 — любая пара: машинный код причины один")
    void u12_7_theReasonCodeIsTheSameForEveryPair() {
        harness.manualHalt.raise(ManualHaltClass.SOFT, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);
        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);
        harness.manualHalt.raise(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);
        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(harness.holdService, org.mockito.Mockito.times(4)).raise(captor.capture(), any());
        assertThat(captor.getAllValues()).extracting(HoldSignal::getCode)
                .containsOnly(Constants.Hold.MANUAL_HALT_REQUESTED);
    }

    /** Множество входа мягкой ступени — только рабочее состояние либо стоящая своя ступень. */
    @Test
    @DisplayName("U12.8 — мягкий класс вне рабочего состояния и без стоящей ступени своего радиуса: отказ")
    void u12_8_theSoftEntrySetIsTheWorkingStateOnly() {
        harness.accountStands(ExchangeAccount.SafetyRung.ACTIVE, ExchangeAccount.Status.CLOSED);

        assertThatThrownBy(() -> harness.manualHalt.raise(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Стоящая мягкая ступень своего радиуса входит в множество входа. */
    @Test
    @DisplayName("U12.9 — мягкий класс на объекте со стоящей мягкой ступенью: отказа нет, вызов доходит до блокировки")
    void u12_9_aStandingSoftRungIsInTheEntrySet() {
        harness.accountStands(ExchangeAccount.SafetyRung.HOLD, ExchangeAccount.Status.CLOSED);

        assertThatCode(() -> harness.manualHalt.raise(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, null))
                .doesNotThrowAnyException();

        verify(harness.holdService).raise(any(), any());
    }

    /** Запрос слабее стоящей поглощается: понижение делает только снятие. */
    @Test
    @DisplayName("U12.10 — мягкий класс на объекте под жёсткой ступенью: отказа нет")
    void u12_10_aStandingHardRungIsInTheEntrySetToo() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.CLOSED);

        assertThatCode(() -> harness.manualHalt.raise(ManualHaltClass.FREEZE,
                ACCOUNT_INTERNAL_ID, null))
                .doesNotThrowAnyException();
    }

    /** Авария застаёт объект в любом состоянии. */
    @Test
    @DisplayName("U12.11 — полный класс в любом статусе: отказа при запуске нет")
    void u12_11_theFullClassRefusesOnNoStatus() {
        harness.accountStands(ExchangeAccount.SafetyRung.ACTIVE, ExchangeAccount.Status.CLOSED);

        assertThatCode(() -> harness.manualHalt.raise(ManualHaltClass.FULL,
                ACCOUNT_INTERNAL_ID, null))
                .doesNotThrowAnyException();
    }

    /** Явный вызов держателя доводит недоделанное. */
    @Test
    @DisplayName("U12.12 — полный класс, жёсткая ступень стои́т, живой риск остался: координатор позван с правом, в логе запись")
    void u12_12_theHolderRetriesTheTeardown() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);
        liveRiskRemains();

        try (SafetyLogCapture log = SafetyLogCapture.attach(ManualHaltService.class)) {
            harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("Holder retries the risk teardown"));
        }
        verify(harness.coordinator).react(
                eq(HoldSignal.exchangeAccount(Constants.Hold.MANUAL_HALT_REQUESTED)), any(), eq(true));
        verify(harness.holdService, never()).raise(any(), any());
    }

    /** Риск погашен — права на доведение нет, вызов идёт общей тропой. */
    @Test
    @DisplayName("U12.13 — полный класс, жёсткая ступень стои́т, риск погашен: общая тропа, поглощение")
    void u12_13_aClearedRiskGrantsNoRetryRight() {
        harness.accountStands(ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.Status.ACTIVE);

        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(harness.holdService).raise(any(), any());
        verify(harness.coordinator, never()).react(any(), any(), any());
    }

    /** Доведение требует уже стоящей ступени. */
    @Test
    @DisplayName("U12.14 — полный класс, жёсткая ступень не стои́т, живой риск есть: права нет")
    void u12_14_theRetryRightNeedsAStandingHardRung() {
        liveRiskRemains();

        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(harness.holdService).raise(any(), any());
        verify(harness.coordinator, never()).react(any(), any(), any());
    }

    /**
     * Рабочее состояние пары читается онбординговым статусом инструмента,
     * а не ступенью пары: смешивать две оси нельзя.
     */
    @Test
    @DisplayName("U12.15 — мягкий класс инструмента: читается онбординговый статус инструмента, а не ступень пары")
    void u12_15_theWorkingStateOfAPairIsTheInstrumentOnboardingStatus() {
        harness.instrumentStands(Instrument.SafetyRung.ACTIVE, Instrument.Status.SYNC);

        assertThatThrownBy(() -> harness.manualHalt.raise(ManualHaltClass.SOFT,
                ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID))
                .as("ступень пары рабочая, а онбординг не завершён — отказ")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Стоящая ступень своего радиуса входит в множество входа наравне с рабочим состоянием. */
    @Test
    @DisplayName("U12.16 — мягкий класс инструмента, у пары стои́т запрет входов, онбординг не завершён: отказа нет")
    void u12_16_aStandingPairRungIsInTheEntrySet() {
        harness.instrumentStands(Instrument.SafetyRung.ENTRY_BLOCKED, Instrument.Status.SYNC);

        assertThatCode(() -> harness.manualHalt.raise(ManualHaltClass.SOFT,
                ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID))
                .doesNotThrowAnyException();
    }

    /**
     * Мягкая ступень анкером идемпотентности не является: полный класс
     * поверх неё даёт полную реакцию, а не поглощение, и права на
     * доведение недоделанного вызов не получает — стоящей ЖЁСТКОЙ
     * ступени нет.
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G1`: группа выведена от
     * отказов при запуске, а здесь отказа нет и предмет — исход реакции.
     */
    @Test
    @DisplayName("U12.17 — полный класс поверх стоящей мягкой ступени своего радиуса: полная реакция, а не поглощение")
    void u12_17_theSoftRungIsNoIdempotencyAnchorForTheFullClass() {
        harness.accountStands(ExchangeAccount.SafetyRung.HOLD, ExchangeAccount.Status.ACTIVE);
        liveRiskRemains();

        harness.manualHalt.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.exchangeAccount(Constants.Hold.MANUAL_HALT_REQUESTED));
        verify(harness.coordinator, never()).react(any(), any(), any());
    }

    /** Одна сделка радиуса риска не доказала — предусловие не выполнено. */
    private void liveRiskRemains() {
        Deal deal = SafetyFixture.dealWithLiveRisk(71L);
        when(harness.deals.findNonTerminalByExchangeAccountId(anyLong()))
                .thenReturn(List.of(deal));
        when(harness.deals.findNonTerminalOnPair(anyLong(), anyLong()))
                .thenReturn(List.of(deal));
        when(harness.contexts.build(deal)).thenReturn(DealContext.builder().deal(deal).build());
        when(harness.terminalGate.riskProvenAbsent(any(), any(), any())).thenReturn(false);
    }
}
