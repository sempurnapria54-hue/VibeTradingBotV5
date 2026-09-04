package com.example.tradingbot.domain.command.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.ActionKind;
import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.SystemActionType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает исполнителя системного действия с его контрактом
 * (docs/components/SystemActionExecutor.md).
 *
 * <p>Несущее — <b>стадия выводится из подтверждённого факта</b>: звено
 * терминала эмитится только после того, как отработало предшествующее, а
 * снятая надобность не заводит строки вовсе; и <b>ревизия живых
 * исполнений</b>: строка, чья надобность снята фактами, закрывается — иначе
 * она держала бы частичный ключ и тратила бюджет впустую.
 */
class SystemActionExecutorTest {

    private final DealActionStateDataService dataService = mock(DealActionStateDataService.class);
    private final SystemActionExecutor executor = new SystemActionExecutor(dataService);

    @Test
    @DisplayName("Штатная тропа: сперва расчёт числа, затем терминальное ребро")
    void exitActionOrdersComputeBeforeTerminal() {
        savesIdentity();
        Deal deal = deal(Deal.Status.EXIT_PENDING);

        ServiceCommand compute = next(deal, SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        assertEquals(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND, compute.getType());

        deal.setResultProfit(BigDecimal.TEN);
        ServiceCommand terminal = next(deal, SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        assertEquals(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND, terminal.getType());
    }

    @Test
    @DisplayName("Тропа закрытия БЕЗ входа звена расчёта не имеет: считать не по чему")
    void noEntryPathSkipsComputeLink() {
        savesIdentity();
        Deal deal = deal(Deal.Status.ACTIVE);
        deal.setEntryReason(Deal.EntryReason.STRATEGY);

        ServiceCommand command = next(deal, SystemActionType.FINALIZE_DEAL_EXIT_ACTION);

        assertEquals(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND, command.getType());
    }

    @Test
    @DisplayName("Аварийная тропа: вход в ошибку и терминал — два отдельных звена")
    void errorActionSplitsEntryAndTerminal() {
        savesIdentity();
        Deal active = deal(Deal.Status.ACTIVE);
        assertEquals(ServiceCommandType.MARK_DEAL_ERROR_COMMAND,
                next(active, SystemActionType.FINALIZE_DEAL_ERROR_ACTION).getType());

        Deal errored = deal(Deal.Status.ERROR);
        assertEquals(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND,
                next(errored, SystemActionType.FINALIZE_DEAL_ERROR_ACTION).getType());
    }

    @Test
    @DisplayName("Терминальная сделка надобности не имеет: строка не заводится вовсе")
    void terminalDealProducesNoLinkAndNoRow() {
        Deal deal = deal(Deal.Status.CLOSED);
        DealContext context = context(deal);

        assertTrue(executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context, null).isEmpty());
        assertTrue(context.getActionStates().isEmpty());
    }

    @Test
    @DisplayName("Живая строка переиспользуется, а не заводится второй за проход")
    void liveRowIsReused() {
        savesIdentity();
        Deal deal = deal(Deal.Status.EXIT_PENDING);
        DealContext context = context(deal);

        ServiceCommand first = executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context, null)
                .orElseThrow();
        ServiceCommand second = executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context, null)
                .orElseThrow();

        assertEquals(1, context.getActionStates().size());
        assertEquals(first.getDealActionStateId(), second.getDealActionStateId());
    }

    @Test
    @DisplayName("Ожидающая повтора строка ждёт своего времени и не эмитит команды")
    void retryPendingWaitsForBackoff() {
        Deal deal = deal(Deal.Status.EXIT_PENDING);
        DealActionState waiting = systemRow(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        waiting.setStatus(DealActionStateStatus.RETRY_PENDING);
        waiting.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        DealContext context = context(deal, waiting);

        assertTrue(executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context, null).isEmpty());
        assertEquals(DealActionStateStatus.RETRY_PENDING, waiting.getStatus());
    }

    @Test
    @DisplayName("Наступившее время повтора перевзводит строку: звено выводится заново")
    void dueRetryRearmsRow() {
        savesIdentity();
        Deal deal = deal(Deal.Status.EXIT_PENDING);
        DealActionState waiting = systemRow(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        waiting.setStatus(DealActionStateStatus.RETRY_PENDING);
        waiting.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        DealContext context = context(deal, waiting);

        ServiceCommand command = executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context, null)
                .orElseThrow();

        assertEquals(DealActionStateStatus.PLANNED, waiting.getStatus());
        assertEquals(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND, command.getType());
    }

    @Test
    @DisplayName("Ревизия закрывает исполнение пережитого эпизода и не трогает своё")
    void revisionClosesStaleTrancheRow() {
        savesIdentity();
        DealTranche tranche = tranche(1L, 2);
        Deal deal = deal(Deal.Status.ACTIVE);
        deal.setTranches(List.of(tranche));

        DealActionState stale = systemRow(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION);
        stale.setDealTrancheId(1L);
        stale.setTrancheEpisodeSeq(1);
        DealActionState current = systemRow(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION);
        current.setDealTrancheId(1L);
        current.setTrancheEpisodeSeq(2);

        executor.reviseLiveExecutions(context(deal, stale, current));

        assertEquals(DealActionStateStatus.SKIPPED, stale.getStatus());
        assertEquals(DealActionStateStatus.PLANNED, current.getStatus());
    }

    @Test
    @DisplayName("Консолидация входа надобна, пока транш стои́т в отправленном входе")
    void entryLinkFollowsTrancheStatus() {
        savesIdentity();
        DealTranche submitted = tranche(1L, 1);
        submitted.setStatus(DealTranche.Status.ENTRY_SUBMITTED);
        Deal deal = deal(Deal.Status.ACTIVE);
        deal.setTranches(List.of(submitted));

        assertEquals(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND,
                executor.next(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, context(deal), submitted)
                        .orElseThrow().getType());

        submitted.setStatus(DealTranche.Status.ENTRY_FINALIZED);
        assertTrue(executor.next(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, context(deal), submitted)
                .isEmpty());
    }

    // ------------------------------------------------------------------

    private ServiceCommand next(Deal deal, SystemActionType type) {
        return executor.next(type, context(deal), null).orElseThrow();
    }

    /** Сохранение возвращает ту же строку: тест меряет решение, а не персистентность. */
    private void savesIdentity() {
        when(dataService.save(any())).thenAnswer(invocation -> {
            DealActionState state = invocation.getArgument(0);
            if (state.getId() == null) {
                state.setId(42L);
            }
            return state;
        });
    }

    private DealContext context(Deal deal, DealActionState... states) {
        return DealContext.builder()
                .deal(deal)
                .actionStates(List.of(states))
                .graphComplete(true)
                .build();
    }

    private Deal deal(Deal.Status status) {
        Deal deal = new Deal();
        deal.setId(7L);
        deal.setStatus(status);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        deal.setTranches(List.of());
        deal.setPositions(List.of());
        deal.setOrders(List.of());
        deal.setAlgoOrders(List.of());
        return deal;
    }

    private DealTranche tranche(Long id, Integer episodeSeq) {
        DealTranche tranche = new DealTranche();
        tranche.setId(id);
        tranche.setEpisodeSeq(episodeSeq);
        tranche.setStatus(DealTranche.Status.MANAGING);
        return tranche;
    }

    private DealActionState systemRow(SystemActionType type) {
        DealActionState state = new DealActionState();
        state.setId(1L);
        state.setDealId(7L);
        state.setActionKind(ActionKind.SYSTEM);
        state.setSystemActionType(type);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }
}
