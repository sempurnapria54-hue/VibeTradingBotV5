package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.RefreshBalanceExecutor;
import com.example.tradingcore.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.BalanceContainerDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Добыча снимка средств и первое наблюдение базы риска.
 *
 * <p><b>Предмет — направление ошибки.</b> База пишется только из пустоты и
 * только на строго положительном остатке; всякий иной исход оставляет её
 * пустой, а пустая база отвергает risk-creating действие. Проверяется, что
 * ненаблюдение при этом РАЗЛИЧИМО в данных: без журнальной строки «команда
 * ни разу не отрабатывала» и «отработала, наблюдать было нечего»
 * неотличимы.
 */
class BalanceRefreshTest {

    private static final Long ACCOUNT_ID = 3L;
    private static final String ACCOUNT = "ea-0001";
    private static final String SETTLE = "USDT";

    private final BalanceContainerDataService balanceContainerDataService =
            mock(BalanceContainerDataService.class);
    private final DealActionStateDataService actionStateDataService = mock(DealActionStateDataService.class);
    private final ExchangeAccountDataService exchangeAccountDataService = mock(ExchangeAccountDataService.class);
    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final AnomalyReportService anomalyReportService = mock(AnomalyReportService.class);

    private final RefreshBalanceExecutor executor = new RefreshBalanceExecutor(balanceContainerDataService,
            actionStateDataService, exchangeAccountDataService, exchange, anomalyReportService);

    /**
     * Первое наблюдение: пустая база получает доступный остаток расчётной
     * валюты и её имя — одним ходом. Валюта резолвится по инструменту, ради
     * которого команда эмитирована.
     */
    @Test
    void firstObservationWritesTheRiskBaseAndItsCurrency() {
        ExchangeAccount account = account(null);
        givenObserved(observed(balance(SETTLE, "1500"), balance("BTC", "0.4")));

        executor.execute(command(), row(), context(account));

        verify(exchangeAccountDataService).applyRiskBase(ACCOUNT_ID, new BigDecimal("1500"), SETTLE);
        assertThat(account.getRiskBase()).isEqualByComparingTo("1500");
        assertThat(account.getRiskBaseCurrency()).isEqualTo(SETTLE);
    }

    /**
     * Непустая база приземлением снимка не трогается ни при каком остатке:
     * запись однократная и только из пустоты — вверх база автоматически не
     * ходит.
     */
    @Test
    void existingRiskBaseIsNeverOverwritten() {
        ExchangeAccount account = account(new BigDecimal("1000"));
        givenObserved(observed(balance(SETTLE, "9999")));

        executor.execute(command(), row(), context(account));

        verify(exchangeAccountDataService, never()).applyRiskBase(any(), any(), any());
        assertThat(account.getRiskBase()).isEqualByComparingTo("1000");
    }

    /**
     * Неположительный остаток базой не становится: записанный ноль
     * автоматически уже не поднялся бы, и счёт остался бы в отказе до
     * явного действия держателя. Ненаблюдение объявляется ПЕРСИСТЕНТНОЙ
     * строкой на счётном радиусе — лог сослаться при разборе не даёт.
     */
    @Test
    void nonPositiveBalanceLeavesTheBaseEmptyAndIsJournalled() {
        ExchangeAccount account = account(null);
        givenObserved(observed(balance(SETTLE, "0")));

        executor.execute(command(), row(), context(account));

        verify(exchangeAccountDataService, never()).applyRiskBase(any(), any(), any());
        assertThat(account.getRiskBase()).isNull();
        assertThat(journalledSignal().getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(journalledSignal().getCode()).isEqualTo(Constants.Hold.RISK_BASE_NOT_OBSERVED);
    }

    /**
     * Нерезолвившийся операнд — тот же исход, что и неположительный
     * остаток: строки расчётной валюты в снимке нет, наблюдать нечего.
     */
    @Test
    void missingSettlementRowIsJournalledToo() {
        ExchangeAccount account = account(null);
        givenObserved(observed(balance("BTC", "3")));

        executor.execute(command(), row(), context(account));

        verify(exchangeAccountDataService, never()).applyRiskBase(any(), any(), any());
        assertThat(journalledSignal().getCode()).isEqualTo(Constants.Hold.RISK_BASE_NOT_OBSERVED);
    }

    /**
     * Отказ журнала снимок не валит: иначе сбой носителя наблюдаемости
     * отнимал бы приземлившийся факт, и команда откатывалась бы вся.
     */
    @Test
    void journalFailureDoesNotBreakTheLandedSnapshot() {
        ExchangeAccount account = account(null);
        givenObserved(observed(balance(SETTLE, "0")));
        when(anomalyReportService.journalState(any(), any(), any()))
                .thenThrow(new IllegalStateException("journal unavailable"));

        DealActionState row = row();
        executor.execute(command(), row, context(account));

        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
    }

    /**
     * Валютные строки ЗАМЕЩАЮТСЯ, а не сливаются: источник отдаёт
     * состояние, и валюта, исчезнувшая из ответа, не должна пережить
     * приземление. Идентичность прежней строки снимка при этом сохраняется
     * — снимок один на счёт.
     */
    @Test
    void landingReplacesTheCurrencyRowsOnTheExistingSnapshot() {
        BalanceContainer stored = new BalanceContainer();
        stored.setId(77L);
        stored.setExchangeAccountId(ACCOUNT_ID);
        stored.setBalances(List.of(balance("ETH", "5")));
        when(balanceContainerDataService.findByExchangeAccountId(ACCOUNT_ID)).thenReturn(Optional.of(stored));
        givenObserved(observed(balance(SETTLE, "1500")));

        executor.execute(command(), row(), context(account(null)));

        ArgumentCaptor<BalanceContainer> landed = ArgumentCaptor.forClass(BalanceContainer.class);
        verify(balanceContainerDataService).save(landed.capture());
        assertThat(landed.getValue().getId()).isEqualTo(77L);
        assertThat(landed.getValue().getBalances()).extracting(Balance::getExternalCurrency)
                .containsExactly(SETTLE);
    }

    private HoldSignal journalledSignal() {
        ArgumentCaptor<HoldSignal> signal = ArgumentCaptor.forClass(HoldSignal.class);
        verify(anomalyReportService).journalState(any(), signal.capture(), eq(null));
        return signal.getValue();
    }

    private void givenObserved(BalanceContainer observed) {
        when(exchange.getBalance(ACCOUNT, SETTLE)).thenReturn(observed);
        when(balanceContainerDataService.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static BalanceContainer observed(Balance... balances) {
        BalanceContainer container = new BalanceContainer();
        container.setExternalTotalEquity(new BigDecimal("2000"));
        container.setBalances(List.of(balances));
        return container;
    }

    private static Balance balance(String currency, String available) {
        Balance balance = new Balance();
        balance.setExternalCurrency(currency);
        balance.setExternalAvailableBalance(new BigDecimal(available));
        return balance;
    }

    private static ExchangeAccount account(BigDecimal riskBase) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT);
        account.setRiskBase(riskBase);
        return account;
    }

    private static DealContext context(ExchangeAccount account) {
        Instrument instrument = new Instrument();
        instrument.setId(9L);
        instrument.setExternalId("ETH-USDT-SWAP");
        instrument.setExternalSettlementCurrency(SETTLE);
        return DealContext.builder().exchangeAccount(account).instrument(instrument).build();
    }

    private static ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_BALANCE_COMMAND)
                .dealId(1L)
                .dealActionStateId(42L)
                .payload(new RefreshBalanceCommandPayload(SETTLE))
                .build();
    }

    private static DealActionState row() {
        DealActionState row = new DealActionState();
        row.setId(42L);
        row.setDealId(1L);
        row.setStatus(DealActionStateStatus.SUBMITTED);
        return row;
    }
}
