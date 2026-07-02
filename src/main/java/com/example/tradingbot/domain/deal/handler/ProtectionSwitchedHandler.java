package com.example.tradingbot.domain.deal.handler;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.FsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса PROTECTION_SWITCHED: подтверждает switch-сценарий —
 * standalone main protection active, attached снята/не влияет. Рабочая
 * логика: REFRESH_ALGO_ORDER для неподтверждённых live algo; когда main
 * protection активна — переход в MANAGING. Перекрытие attached + main
 * (обе reduce-only) безопасно — окна без защиты нет
 * (docs/decisions/replace-not-amend.md); точечная отмена attached —
 * forward-refinement. См. docs/components/ProtectionSwitchedHandler.md.
 */
@Component
@RequiredArgsConstructor
public class ProtectionSwitchedHandler implements FsmHandler {

    private final DealFsmSupport support;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PROTECTION_SWITCHED;
    }

    @Override
    public Optional<DealTransition> checkEntry(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(support.positionLiveRisk(deal))) {
            return Optional.of(DealTransition.transition(Deal.Status.EXIT_PENDING));
        }
        if (isEmpty(support.liveAlgoOrders(deal))) {
            // Позиция с live risk, но main protection не подтверждена live — защита потеряна
            // → бесстоповая позиция постфактум → L3-холд инструмента (§8.C).
            return Optional.of(support.markErrorStopless(dealContext));
        }
        return Optional.empty();
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        boolean allActive = support.liveAlgoOrders(dealContext.getDeal()).stream()
                .allMatch(algoOrder -> AlgoOrder.Status.ACTIVE.equals(algoOrder.getStatus()));
        // Main protection активна и подтверждена → сделка готова к сопровождению.
        return allActive ? Optional.of(DealTransition.transition(Deal.Status.MANAGING)) : Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        for (AlgoOrder algoOrder : support.liveAlgoOrders(dealContext.getDeal())) {
            if (isFalse(AlgoOrder.Status.ACTIVE.equals(algoOrder.getStatus()))) {
                return DealTransition.command(support.refreshAlgoOrderCommand(dealContext, algoOrder.getId()));
            }
        }
        return DealTransition.stay();
    }
}
