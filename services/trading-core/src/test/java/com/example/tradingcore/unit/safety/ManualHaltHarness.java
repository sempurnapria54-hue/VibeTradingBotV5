package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.instrument;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairState;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.ManualHaltService;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import java.util.List;

/**
 * Базовая сборка групп `U12` и `U13` документа
 * `.claude/tests/cases/trading-core-safety.md`: ручная поверхность
 * остановки.
 *
 * <p>Службы счёта, инструмента и пары отдают объекты в рабочем
 * состоянии без стоящих ступеней; гейт терминала подтверждает
 * отсутствие живого риска; сервис блокировки и координатор подменены —
 * они соседние единицы предмета, а не предмет этих групп.
 */
final class ManualHaltHarness {

    /** Служба счёта: резолв объекта радиуса и переход его лестницы. */
    final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);

    /** Служба каталога инструментов: резолв инструмента радиуса пары. */
    final InstrumentDataService instruments = mock(InstrumentDataService.class);

    /** Служба состояния пары: стоящая ступень её лестницы. */
    final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);

    /** Служба сделок: выборка предусловия живого риска. */
    final DealDataService deals = mock(DealDataService.class);

    /** Сборщик контекста сделки предусловия. */
    final DealContextService contexts = mock(DealContextService.class);

    /** Гейт терминала: предикат «живого риска не осталось» поверхность своего не заводит. */
    final DealTerminalGate terminalGate = mock(DealTerminalGate.class);

    /** Координатор полной реакции: сюда уходит доведение недоделанного. */
    final SafetyHoldCoordinator coordinator = mock(SafetyHoldCoordinator.class);

    /** Общая точка входа постановки. */
    final HoldService holdService = mock(HoldService.class);

    /** Сервис журнала: строка операции. */
    final AnomalyReportService reports = mock(AnomalyReportService.class);

    /** Предмет групп. */
    final ManualHaltService manualHalt = new ManualHaltService(accounts, instruments, pairStates,
            deals, contexts, terminalGate, coordinator, holdService, reports);

    ManualHaltHarness() {
        accountStands(ExchangeAccount.SafetyRung.ACTIVE, ExchangeAccount.Status.ACTIVE);
        instrumentStands(Instrument.SafetyRung.ACTIVE, Instrument.Status.ACTIVE);
        when(deals.findNonTerminalByExchangeAccountId(anyLong())).thenReturn(List.of());
        when(deals.findNonTerminalOnPair(anyLong(), anyLong())).thenReturn(List.of());
        when(terminalGate.riskProvenAbsent(any(), any(), any())).thenReturn(true);
        when(accounts.clearRung(anyLong(), any(), any())).thenReturn(true);
        when(pairStates.clearRung(anyLong(), anyLong(), any(), any())).thenReturn(true);
    }

    /** Счёт названной ступени и названного реестрового статуса. */
    void accountStands(ExchangeAccount.SafetyRung rung, ExchangeAccount.Status status) {
        ExchangeAccount account = account(rung);
        account.setStatus(status);
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account);
    }

    /** Пара названной ступени и инструмент названного онбордингового статуса. */
    void instrumentStands(Instrument.SafetyRung rung, Instrument.Status status) {
        when(instruments.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID))
                .thenReturn(instrument(status));
        when(pairStates.getRequiredByPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(pairState(rung));
    }
}
