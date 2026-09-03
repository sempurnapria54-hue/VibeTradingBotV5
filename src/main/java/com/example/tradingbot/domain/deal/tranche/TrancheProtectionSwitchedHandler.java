package com.example.tradingbot.domain.deal.tranche;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.TrancheTransition;
import com.example.tradingbot.domain.deal.TrancheFsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
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
 * (docs/rules/replace-not-amend.md); точечная отмена attached —
 * forward-refinement. См. docs/components/TrancheProtectionSwitchedHandler.md.
 */
@Component
@RequiredArgsConstructor
public class TrancheProtectionSwitchedHandler implements TrancheFsmHandler {

    private final DealFsmSupport support;

    @Override
    public DealTranche.Status supportedStatus() {
        return DealTranche.Status.PROTECTION_SWITCHED;
    }

    @Override
    public Optional<TrancheTransition> checkEntry(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isFalse(support.positionLiveRisk(deal))) {
            return Optional.of(TrancheTransition.transition(DealTranche.Status.EXIT_PENDING));
        }
        if (isEmpty(support.liveAlgoOrders(deal))) {
            // Позиция с live risk, но main protection не подтверждена live — защита потеряна
            // → бесстоповая позиция постфактум → L3-холд инструмента (§8.C).
            return Optional.of(TrancheTransition.escalateToDealError());
        }
        return Optional.empty();
    }

    @Override
    public Optional<TrancheTransition> checkTransition(DealContext dealContext, DealTranche tranche) {
        boolean allActive = support.liveAlgoOrders(dealContext.getDeal()).stream()
                .allMatch(algoOrder -> AlgoOrder.Status.ACTIVE.equals(algoOrder.getStatus()));
        // Main protection активна и подтверждена → сделка готова к сопровождению.
        return allActive ? Optional.of(TrancheTransition.transition(DealTranche.Status.MANAGING)) : Optional.empty();
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        for (AlgoOrder algoOrder : support.liveAlgoOrders(dealContext.getDeal())) {
            if (isFalse(AlgoOrder.Status.ACTIVE.equals(algoOrder.getStatus()))) {
                return support.refreshAlgoOrderCommand(dealContext, algoOrder.getId())
                        .map(TrancheTransition::command)
                        .orElseGet(TrancheTransition::stay);
            }
        }
        return TrancheTransition.stay();
    }
}
