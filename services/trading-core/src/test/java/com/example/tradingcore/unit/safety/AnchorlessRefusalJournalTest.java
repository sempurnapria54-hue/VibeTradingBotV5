package com.example.tradingcore.unit.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingcore.config.ServiceCommandRetryProperties;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryBackoffType;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandRetryPolicy;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.CommandExecutor;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.exception.ExchangeIntegrationException;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.util.Constants;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Отказ отмены и закрытия без анкера — группа {@code U17} документа
 * `.claude/tests/cases/trading-core-safety.md` (дом —
 * docs/components/ServiceCommandExecutor.md §«Отказ команды без анкера —
 * происшествие, а не бюджет»).
 *
 * <p><b>Базовая сборка:</b> диспетчер команд с одним исполнителем
 * названного типа, подменёнными журналом отчётов и службой строк
 * исполнения; сделка {@code 7}; предел повторов — два.
 */
class AnchorlessRefusalJournalTest {

    private static final Long DEAL = 7L;
    private static final Long ROW = 42L;

    private final CommandExecutor executor = mock(CommandExecutor.class);
    private final DealActionStateDataService dataService = mock(DealActionStateDataService.class);
    private final AnomalyReportService journal = mock(AnomalyReportService.class);

    @Test
    @DisplayName("U17.1 — брошенный отказ отмены заявки без анкера: журнальный отчёт на сделку и тип")
    void u17_1_aThrownAnchorlessCancelRefusalIsJournaled() {
        ServiceCommandExecutor dispatcher = dispatcherFor(ServiceCommandType.CANCEL_ORDER_COMMAND);
        when(executor.execute(any(), any(), any())).thenThrow(new ExchangeIntegrationException("51400"));

        ServiceCommandExecutionResult result = dispatcher.execute(
                anchorless(ServiceCommandType.CANCEL_ORDER_COMMAND), context());

        assertThat(result.getSuccess()).isFalse();
        ArgumentCaptor<HoldSignal> signal = ArgumentCaptor.forClass(HoldSignal.class);
        verify(journal).journalOnce(any(), signal.capture(),
                eq("anchorless-command:7:CANCEL_ORDER_COMMAND"), anyMap());
        assertThat(signal.getValue().getCode()).isEqualTo(Constants.Hold.ANCHORLESS_COMMAND_REFUSED);
        assertThat(signal.getValue().getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(signal.getValue().tearsDownRisk()).isFalse();
        verify(dataService, never()).save(any());
    }

    @Test
    @DisplayName("U17.2 — возвращённый отказ закрытия позиции без анкера: тот же отчёт, операнды отказа названы")
    @SuppressWarnings("unchecked")
    void u17_2_aReturnedAnchorlessCloseRefusalIsJournaledWithItsOperands() {
        ServiceCommandExecutor dispatcher = dispatcherFor(ServiceCommandType.CLOSE_POSITION_COMMAND);
        when(executor.execute(any(), any(), any()))
                .thenReturn(ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR, "51169"));

        dispatcher.execute(anchorless(ServiceCommandType.CLOSE_POSITION_COMMAND), context());

        ArgumentCaptor<Map<String, Object>> operands = ArgumentCaptor.forClass(Map.class);
        verify(journal).journalOnce(any(), any(), eq("anchorless-command:7:CLOSE_POSITION_COMMAND"),
                operands.capture());
        Map<String, Object> refusal = (Map<String, Object>) operands.getValue().get("refusal");
        assertThat(refusal).containsEntry("commandType", "CLOSE_POSITION_COMMAND")
                .containsEntry("errorCode", "EXCHANGE_ERROR")
                .containsEntry("message", "51169");
    }

    /** Команда с анкером идёт учётом бюджета; отчёт о бюджете — чужой исход. */
    @Test
    @DisplayName("U17.3 — отказ отмены с анкером: отчёта нет, строка ждёт повтора")
    void u17_3_anAnchoredRefusalGoesToTheBudgetNotToTheJournal() {
        ServiceCommandExecutor dispatcher = dispatcherFor(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND);
        DealActionState row = strategyRow();
        when(executor.execute(any(), any(), any())).thenThrow(new ExchangeIntegrationException("51400"));

        dispatcher.execute(ServiceCommand.builder().type(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND)
                .dealId(DEAL).dealActionStateId(ROW).build(), context(row));

        verify(journal, never()).journalOnce(any(), any(), anyString(), anyMap());
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.RETRY_PENDING);
    }

    /** Добыча без анкера (подтверждение kill-switch) предметом отчёта не является. */
    @Test
    @DisplayName("U17.4 — отказ команды вне группы «отмена и закрытие» без анкера: отчёта нет")
    void u17_4_anAnchorlessRefusalOutsideCancelAndCloseIsNotJournaled() {
        ServiceCommandExecutor dispatcher = dispatcherFor(ServiceCommandType.REFRESH_POSITION_COMMAND);
        when(executor.execute(any(), any(), any())).thenThrow(new ExchangeIntegrationException("timeout"));

        dispatcher.execute(anchorless(ServiceCommandType.REFRESH_POSITION_COMMAND), context());

        verify(journal, never()).journalOnce(any(), any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("U17.5 — успешная отмена без анкера: отчёта нет")
    void u17_5_aSuccessfulAnchorlessCancelIsNotJournaled() {
        ServiceCommandExecutor dispatcher = dispatcherFor(ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND);
        when(executor.execute(any(), any(), any())).thenReturn(ServiceCommandExecutionResult.ok());

        dispatcher.execute(anchorless(ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND), context());

        verify(journal, never()).journalOnce(any(), any(), anyString(), anyMap());
    }

    /** Сбой записи отчёта исход прохода не заслоняет: вернулся отказ площадки. */
    @Test
    @DisplayName("U17.6 — запись отчёта бросает: наружу уходит отказ команды, а не сбой журнала")
    void u17_6_aJournalFailureDoesNotMaskTheRefusal() {
        ServiceCommandExecutor dispatcher = dispatcherFor(ServiceCommandType.CANCEL_ORDER_COMMAND);
        when(executor.execute(any(), any(), any())).thenThrow(new ExchangeIntegrationException("51400"));
        when(journal.journalOnce(any(), any(), anyString(), anyMap())).thenThrow(new IllegalStateException("db"));

        assertThatCode(() -> assertThat(dispatcher.execute(anchorless(ServiceCommandType.CANCEL_ORDER_COMMAND),
                context()).getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR))
                .doesNotThrowAnyException();
    }

    private ServiceCommandExecutor dispatcherFor(ServiceCommandType type) {
        when(executor.supportedType()).thenReturn(type);
        return new ServiceCommandExecutor(List.of(executor), new RetryPolicyService(properties()), dataService,
                journal);
    }

    private static ServiceCommand anchorless(ServiceCommandType type) {
        return ServiceCommand.builder().type(type).dealId(DEAL).build();
    }

    private static DealContext context(DealActionState... rows) {
        return DealContext.builder().actionStates(List.of(rows)).build();
    }

    private static DealActionState strategyRow() {
        DealActionState row = new DealActionState();
        row.setId(ROW);
        row.setDealId(DEAL);
        row.setActionKind(ActionKind.STRATEGY);
        row.setStrategyActionId(11L);
        row.setStatus(DealActionStateStatus.SUBMITTED);
        return row;
    }

    private static ServiceCommandRetryProperties properties() {
        ServiceCommandRetryPolicy policy = new ServiceCommandRetryPolicy();
        policy.setMaxAttempts(2);
        policy.setInitialDelay(Duration.ofSeconds(5));
        policy.setMaxDelay(Duration.ofMinutes(2));
        policy.setBackoff(RetryBackoffType.EXPONENTIAL);
        ServiceCommandRetryProperties properties = new ServiceCommandRetryProperties();
        properties.setDefaultPolicy(policy);
        return properties;
    }
}
