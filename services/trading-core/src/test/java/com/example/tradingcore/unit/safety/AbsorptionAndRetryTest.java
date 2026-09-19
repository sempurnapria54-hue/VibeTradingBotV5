package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.accountContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.deals;
import static com.example.tradingcore.unit.safety.SafetyFixture.instrument;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.util.Constants;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Поглощение и доведение недоделанного — группа `U6` документа
 * `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/SafetyHoldCoordinator.md §«Анкер и доведение
 * недоделанного»; право на доведение — docs/rules/manual-halt.md
 * §«Идемпотентность наследуется, а не обходится»).
 *
 * <p><b>Базовая сборка</b> — {@link CoordinatorHarness}, но ребро
 * подъёма отвечает «переход не применился»: ступень запрошенного уровня
 * на объекте уже стои́т.
 *
 * <p><b>Исключающий ключ объекта наблюдается вложенным вызовом, а не
 * потоком.</b> Второй конкурирующий вызов подаётся из-под подменённого
 * исполнителя снятия риска — то есть ровно в том окне, которое ключ и
 * закрывает; порядок при этом детерминирован, а времени прогон не
 * платит.
 */
class AbsorptionAndRetryTest {

    private final CoordinatorHarness harness = new CoordinatorHarness();

    @BeforeEach
    void rungAlreadyStands() {
        when(harness.pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(false);
        when(harness.accounts.raiseRung(anyLong(), any())).thenReturn(false);
    }

    /** Поглощение состоит из неслучившегося — а строка журнала остаётся. */
    @Test
    @DisplayName("U6.1 — автоматический жёсткий сигнал на стоящей ступени: снятия риска, каскада и отчёта нет, строка есть")
    void u6_1_anAbsorbedSignalRunsNothingButTheJournalRow() {
        DealContext context = pairContext();
        HoldSignal signal = HoldSignal.instrument(CODE);

        harness.coordinator.react(signal, context);

        verify(harness.killSwitchService, never()).fireInstrument(any());
        verify(harness.statusEdges, never()).enforceHardRung(any(), any());
        verify(harness.reports, never()).open(any(), any());
        verify(harness.reports).journalState(context, signal, null);
    }

    /** Факта подъёма нет: перехода не было, а писатель стои́т на переходе. */
    @Test
    @DisplayName("U6.2 — то же: факта подъёма нет")
    void u6_2_anAbsorbedSignalProducesNoFact() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verifyNoInteractions(harness.coreEventWriter);
    }

    /** Журнал поглощения реакцию не гейтит. */
    @Test
    @DisplayName("U6.3 — писатель состояния бросает на поглощении: исключения наружу нет, в логе запись")
    void u6_3_aFailingAbsorptionJournalIsSwallowed() {
        when(harness.reports.journalState(any(), any(), any()))
                .thenThrow(new IllegalStateException("db is down"));

        try (SafetyLogCapture log = SafetyLogCapture.attach(SafetyHoldCoordinator.class)) {
            assertThatCode(() -> harness.coordinator.react(HoldSignal.instrument(CODE), pairContext()))
                    .doesNotThrowAnyException();

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("Journal of an absorbed safety signal failed"));
        }
    }

    /** Доведение недоделанного: тропа не поглощённая, и строки поглощения она не пишет. */
    @Test
    @DisplayName("U6.4 — вызов с правом на доведение при стоящей ступени: отчёт открыт, снятие риска заново, каскад идёт")
    void u6_4_theTeardownRetryRunsTheWholeReactionAgain() {
        harness.cascadeOnPair(deals(deal(31L)));

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

        verify(harness.reports).open(any(), any());
        verify(harness.killSwitchService).fireInstrument(any());
        verify(harness.statusEdges).enforceHardRung(any(), any());
        verify(harness.reports, never()).journalState(any(), any(), any());
    }

    /** У доведения факта подъёма нет: ступень на объекте уже стои́т. */
    @Test
    @DisplayName("U6.5 — то же: факта подъёма нет")
    void u6_5_theTeardownRetryProducesNoFact() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

        verifyNoInteractions(harness.coreEventWriter);
    }

    /** Исключающий ключ объекта: второй проход пропускается. */
    @Test
    @DisplayName("U6.6 — доведение при уже идущей реакции того же объекта: второй проход пропущен, в логе запись")
    void u6_6_theObjectKeySkipsTheSecondRun() {
        AtomicInteger entered = new AtomicInteger();
        when(harness.killSwitchService.fireInstrument(any())).thenAnswer(invocation -> {
            if (entered.incrementAndGet() == 1) {
                harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);
            }
            return true;
        });

        try (SafetyLogCapture log = SafetyLogCapture.attach(SafetyHoldCoordinator.class)) {
            harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("Full reaction is already running on the object"));
        }
        verify(harness.killSwitchService, times(1)).fireInstrument(any());
    }

    /** Ключ строится по объекту радиуса, а не глобально. */
    @Test
    @DisplayName("U6.7 — два доведения на разных счетах: оба проходят")
    void u6_7_theKeyIsPerObjectNotGlobal() {
        DealContext otherAccount = DealContext.builder()
                .exchangeAccount(otherAccount())
                .instrument(instrument())
                .build();
        AtomicInteger entered = new AtomicInteger();
        when(harness.killSwitchService.fireInstrument(any())).thenAnswer(invocation -> {
            if (entered.incrementAndGet() == 1) {
                harness.coordinator.react(HoldSignal.instrument(CODE), otherAccount, true);
            }
            return true;
        });

        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

        verify(harness.killSwitchService, times(2)).fireInstrument(any());
    }

    /** Ключи радиусов различны: счёт и пара того же счёта не вытесняют друг друга. */
    @Test
    @DisplayName("U6.8 — доведение на счёте и на паре того же счёта: оба проходят")
    void u6_8_theScopeKeysAreDistinct() {
        when(harness.killSwitchService.fireExchangeAccount(anyLong())).thenAnswer(invocation -> {
            harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);
            return true;
        });

        harness.coordinator.react(HoldSignal.exchangeAccount(CODE), accountContext(), true);

        verify(harness.killSwitchService, times(1)).fireExchangeAccount(ACCOUNT_ID);
        verify(harness.killSwitchService, times(1)).fireInstrument(any());
    }

    /** Ключ освобождается по выходу из прохода — в том числе выходом броском. */
    @Test
    @DisplayName("U6.9 — доведение прошло, следом второе на том же объекте: второе проходит")
    void u6_9_theKeyIsReleasedOnExitIncludingThrow() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

        verify(harness.killSwitchService, times(2)).fireInstrument(any());

        doThrow(new IllegalStateException("cascade query is down"))
                .when(harness.deals).findCascadeSourceOnPair(anyLong(), anyLong());
        assertThatThrownBy(() -> harness.coordinator.react(HoldSignal.instrument(CODE),
                pairContext(), true))
                .isInstanceOf(IllegalStateException.class);

        doReturn(deals()).when(harness.deals).findCascadeSourceOnPair(anyLong(), anyLong());
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

        verify(harness.killSwitchService, times(4)).fireInstrument(any());
    }

    /** У автоматического сигнала права на доведение нет по построению. */
    @Test
    @DisplayName("U6.10 — автоматический сигнал на стоящей ступени при непогашенном риске: поглощение, а не доведение")
    void u6_10_anAutomaticSignalHasNoRetryRight() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext());

        verify(harness.killSwitchService, never()).fireInstrument(any());
        verify(harness.reports).journalState(any(), any(), any());
    }

    /** Ступень не стои́т — обычная полная реакция, и исключающий ключ не берётся. */
    @Test
    @DisplayName("U6.11 — вызов с правом на доведение, ступень не стои́т: обычная реакция, ключ не взят")
    void u6_11_theKeyIsNotTakenWhenTheTransitionApplies() {
        when(harness.pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        AtomicInteger entered = new AtomicInteger();
        when(harness.killSwitchService.fireInstrument(any())).thenAnswer(invocation -> {
            if (entered.incrementAndGet() == 1) {
                harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);
            }
            return true;
        });

        try (SafetyLogCapture log = SafetyLogCapture.attach(SafetyHoldCoordinator.class)) {
            harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

            assertThat(log.messages())
                    .as("ключа никто не брал — вытеснять нечем")
                    .noneMatch(message -> message.contains("Full reaction is already running"));
        }
        verify(harness.killSwitchService, times(2)).fireInstrument(any());
        verify(harness.reports, never()).journalState(any(), any(), any());
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом и
     * спека объявляют строку отчёта незаводимой там, где стоящую ступень
     * поднял тот же ручной вызов (docs/spec/manual-halt.json, величина
     * {@code reportProduced}), а доведение открывает отчёт критичной
     * тропой, у которой дедупа нет вовсе. Красный прогон и есть
     * предъявление находки `S-6` (`.claude/work/backlog.md` §«Отчёт
     * доведения заводится и там, где дом его не заводит»).
     *
     * <p><b>Вход от U6.13 отличается только состоянием мира</b> —
     * стоящей строкой ручного ключа, — и координатор не читает его
     * нигде: ровно это находка и называет.
     */
    @Test
    @Tag("debt")
    @DisplayName("U6.12 — доведение на ступени, поднятой тем же ручным вызовом: второй строки отчёта нет")
    void u6_12_theRetryOpensNoSecondReportOverItsOwnManualRow() {
        harness.coordinator.react(HoldSignal.instrument(Constants.Hold.MANUAL_HALT_REQUESTED),
                pairContext(), true);

        verify(harness.reports, never()).open(any(), any());
    }

    /** Ступень поднята автоматикой — ручной строки по ключу нет, и доведение заводит свою. */
    @Test
    @DisplayName("U6.13 — доведение на ступени, поднятой автоматикой: доведение заводит свою строку")
    void u6_13_theRetryOpensItsOwnReportOverAnAutomaticRung() {
        harness.coordinator.react(HoldSignal.instrument(CODE), pairContext(), true);

        verify(harness.reports).open(any(), any());
    }

    private ExchangeAccount otherAccount() {
        ExchangeAccount other = account();
        other.setId(ACCOUNT_ID + 1);
        other.setInternalId("EXA-0000000042");
        return other;
    }
}
