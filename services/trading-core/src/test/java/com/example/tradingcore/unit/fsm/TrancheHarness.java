package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.fsm.StrategyWorkRunner;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import com.example.tradingcore.domain.fsm.tranche.TrancheEntryFinalizedHandler;
import com.example.tradingcore.domain.fsm.tranche.TrancheEntrySubmittedHandler;
import com.example.tradingcore.domain.fsm.tranche.TrancheExitPendingHandler;
import com.example.tradingcore.domain.fsm.tranche.TrancheManagingHandler;
import com.example.tradingcore.domain.fsm.tranche.TranchePrecheckHandler;
import com.example.tradingcore.domain.fsm.tranche.TrancheProtectionSwitchedHandler;
import java.time.Duration;
import java.util.Optional;

/**
 * Шесть обработчиков транша с общими коллабораторами
 * (`.claude/tests/cases/trading-core-fsm.md` §«Чем достаются выходы»).
 *
 * <p><b>Рабочий блок — НАБЛЮДАТЕЛЬ поверх настоящего, а не мок:</b>
 * подменяется только его проход ({@code run}), а признак «блок что-то
 * сказал» остаётся настоящим. Замени его целиком — и обработчик читал бы
 * пустой ответ подменённого предиката, то есть проверялся бы против
 * машины, которой в проде не существует.
 *
 * <p><b>Диспозиция и гейт покрытия настоящие:</b> у них нет ни
 * ввода-вывода, ни своего предмета — карта реакции и инвариант покрытия
 * считаются по настоящим полям. Подменены исполнитель системных действий
 * (строка исполнения в базе) и исполнитель работы стратегии.
 */
final class TrancheHarness {

    /** Толерантность возраста снимка средств базовой сборки. */
    static final Duration BALANCE_FRESHNESS = Duration.ofMinutes(5);

    private final StrategyStepSelector stepSelector = mock(StrategyStepSelector.class);

    private final StrategyWorkRunner workRunner = mock(StrategyWorkRunner.class);

    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);

    private final TrancheActionDisposition disposition =
            new TrancheActionDisposition(systemActionExecutor, workRunner);

    private final TrancheWorkPass workPass =
            spy(new TrancheWorkPass(stepSelector, workRunner, disposition));

    private final ProtectionCoverageGate coverageGate = spy(new ProtectionCoverageGate());

    private final DealContextProperties properties = new DealContextProperties();

    TrancheHarness() {
        doReturn(TrancheTransition.stay()).when(workPass).run(any(), any());
        when(systemActionExecutor.next(any(), any(), any())).thenReturn(Optional.empty());
        when(systemActionExecutor.next(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        properties.setBalanceFreshness(BALANCE_FRESHNESS);
    }

    /** Исход рабочего блока на этом проходе. */
    void givenWork(TrancheTransition transition) {
        doReturn(transition).when(workPass).run(any(), any());
    }

    /** Звено названного типа отдаёт команду: живой строки у него нет. */
    void givenSystemCommand(SystemActionType type, ServiceCommandType commandType) {
        when(systemActionExecutor.next(eq(type), any(), any()))
                .thenReturn(Optional.of(command(commandType)));
    }

    /** Звено добычи снимка средств отдаёт свою команду. */
    void givenBalanceFetchCommand() {
        when(systemActionExecutor.next(eq(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION), any(), isNull(),
                eq(ServiceCommandType.REFRESH_BALANCE_COMMAND), any()))
                .thenReturn(Optional.of(command(ServiceCommandType.REFRESH_BALANCE_COMMAND)));
    }

    /** Толерантность возраста снимка средств; пусто — срока никто не объявил. */
    void givenBalanceFreshness(Duration freshness) {
        properties.setBalanceFreshness(freshness);
    }

    /** Рабочий блок на этом проходе не запускался. */
    void verifyWorkPassNotRun() {
        verify(workPass, never()).run(any(), any());
    }

    /** Гейт покрытия на этом проходе не спрашивался. */
    void verifyCoverageGateNotAsked() {
        verify(coverageGate, never()).trancheViolated(any(), any());
    }

    // --- обработчики --------------------------------------------------------

    TranchePrecheckHandler precheck() {
        return new TranchePrecheckHandler(workPass, disposition, properties);
    }

    TrancheEntrySubmittedHandler entrySubmitted() {
        return new TrancheEntrySubmittedHandler(workPass, disposition, systemActionExecutor);
    }

    TrancheEntryFinalizedHandler entryFinalized() {
        return new TrancheEntryFinalizedHandler(workPass, disposition, coverageGate);
    }

    TrancheProtectionSwitchedHandler protectionSwitched() {
        return new TrancheProtectionSwitchedHandler(workPass, disposition);
    }

    TrancheManagingHandler managing() {
        return new TrancheManagingHandler(workPass, coverageGate);
    }

    TrancheExitPendingHandler exitPending() {
        return new TrancheExitPendingHandler(workPass, disposition);
    }
}
