package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.FsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
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
 * docs/components/EntrySubmittedHandler.md.
 */
@Component
@RequiredArgsConstructor
public class EntrySubmittedHandler implements FsmHandler {

    private final DealFsmSupport support;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ENTRY_SUBMITTED;
    }

    @Override
    public Optional<DealTransition> checkEntry(DealContext dealContext) {
        // Субъект статуса — entry-ордер; его нет → рассинхрон, на ошибочную тропу.
        if (isNull(support.entryOrder(dealContext.getDeal()))) {
            return Optional.of(support.markError(dealContext));
        }
        return Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        Order entry = support.entryOrder(deal);
        if (isTrue(entry.isLive())) {
            return DealTransition.command(support.refreshOrderCommand(dealContext, entry.getId()));
        }
        if (isTrue(support.positionLiveRisk(deal))) {
            return finalizeEntry(dealContext);
        }
        if (nonNull(deal.getPosition())) {
            // Позиция есть, но live risk нет (закрылась на бирже SL/TP) — штатная дочистка.
            return DealTransition.transition(Deal.Status.EXIT_PENDING);
        }
        if (isTrue(entryHadFill(entry))) {
            // Entry исполнился, локальной позиции ещё нет — обнаружить её фактами.
            return DealTransition.command(support.refreshPositionCommand(dealContext));
        }
        // Entry терминален без исполнения (canceled до fill): live risk не открыт — чистое закрытие.
        return DealTransition.builder()
                .nextStatus(Deal.Status.CLOSED)
                .closeReason(Deal.CloseReason.ENTRY_CONDITION_EXPIRED)
                .build();
    }

    /** Консолидировать вход (FINALIZE_DEAL_ENTRY), затем выходная проверка → ENTRY_FINALIZED. */
    private DealTransition finalizeEntry(DealContext dealContext) {
        return support.finalizationCommand(DealFinalizationType.FINALIZE_ENTRY, dealContext)
                .map(DealTransition::command)
                .orElseGet(() -> DealTransition.transition(Deal.Status.ENTRY_FINALIZED));
    }

    private Boolean entryHadFill(Order entry) {
        if (Order.Status.COMPLETED.equals(entry.getStatus())) {
            return true;
        }
        BigDecimal filled = entry.getAccumulatedFillSize();
        return nonNull(filled) && filled.signum() > 0;
    }
}
