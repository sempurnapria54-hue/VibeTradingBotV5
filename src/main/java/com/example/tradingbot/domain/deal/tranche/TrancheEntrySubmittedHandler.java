package com.example.tradingbot.domain.deal.tranche;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.SystemActionType;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.TrancheTransition;
import com.example.tradingbot.domain.deal.TrancheFsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса ENTRY_SUBMITTED: подтверждает, что entry order
 * отправлен, и определяет, появилась ли позиция. Рабочая логика: live
 * entry order → REFRESH_ORDER; terminal без позиции, но с фактом
 * исполнения → REFRESH_POSITION (RefreshPositionExecutor создаёт Position);
 * позиция активна → FINALIZE_DEAL_ENTRY → выход в ENTRY_FINALIZED. Если
 * позиция уже закрылась на бирже — recovery в EXIT_PENDING. Перед повторным
 * submit поиск по client id обеспечивает SubmitOrderExecutor (D-B3). См.
 * docs/components/TrancheEntrySubmittedHandler.md.
 */
@Component
@RequiredArgsConstructor
public class TrancheEntrySubmittedHandler implements TrancheFsmHandler {

    private final DealFsmSupport support;

    @Override
    public DealTranche.Status supportedStatus() {
        return DealTranche.Status.ENTRY_SUBMITTED;
    }

    @Override
    public Optional<TrancheTransition> checkEntry(DealContext dealContext, DealTranche tranche) {
        // Субъект статуса — entry-ордер; его нет → рассинхрон, на ошибочную тропу.
        if (isNull(support.entryOrder(dealContext.getDeal()))) {
            return Optional.of(TrancheTransition.escalateToDealError());
        }
        return Optional.empty();
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        Order entry = support.entryOrder(deal);
        if (isTrue(entry.isLive())) {
            return support.refreshOrderCommand(dealContext, entry.getId())
                    .map(TrancheTransition::command)
                    .orElseGet(TrancheTransition::stay);
        }
        if (isTrue(support.positionLiveRisk(deal))) {
            return finalizeEntry(dealContext, tranche);
        }
        if (nonNull(deal.livePosition())) {
            // Позиция есть, но live risk нет (закрылась на бирже SL/TP) — штатная дочистка.
            return TrancheTransition.transition(DealTranche.Status.EXIT_PENDING);
        }
        if (isTrue(entryHadFill(entry))) {
            // Entry исполнился, локальной позиции ещё нет — обнаружить её фактами.
            return support.refreshPositionCommand(dealContext)
                    .map(TrancheTransition::command)
                    .orElseGet(TrancheTransition::stay);
        }
        // Entry терминален без исполнения (canceled до fill): live risk не открыт — чистое закрытие.
        return TrancheTransition.builder()
                .nextStatus(DealTranche.Status.CLOSED)
                .closeReason(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED)
                .build();
    }

    /**
     * Консолидировать вход — системным действием ЭТОГО транша: статус
     * подтверждённого входа пишется ему, поэтому N траншей сетки,
     * консолидирующих вход одновременно, ведут N законных исполнений
     * одного типа (docs/models/domain/other/DealActionState.md).
     */
    private TrancheTransition finalizeEntry(DealContext dealContext, DealTranche tranche) {
        return support.systemAction(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, dealContext, tranche)
                .map(TrancheTransition::command)
                .orElseGet(() -> TrancheTransition.transition(DealTranche.Status.ENTRY_FINALIZED));
    }

    private Boolean entryHadFill(Order entry) {
        if (Order.Status.COMPLETED.equals(entry.getStatus())) {
            return true;
        }
        BigDecimal filled = entry.getAccumulatedFillSize();
        return nonNull(filled) && filled.signum() > 0;
    }
}
