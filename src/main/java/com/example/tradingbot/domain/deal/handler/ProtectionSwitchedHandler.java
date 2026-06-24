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
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(support.positionLiveRisk(deal))) {
            return DealTransition.transition(Deal.Status.EXIT_PENDING);
        }
        List<AlgoOrder> liveAlgoOrders = support.liveAlgoOrders(deal);
        if (isEmpty(liveAlgoOrders)) {
            // Позиция с live risk, но main protection не подтверждена live — защита потеряна
            // → бесстоповая позиция постфактум → L3-холд инструмента (§8.C).
            return support.markErrorStopless(dealContext);
        }
        for (AlgoOrder algoOrder : liveAlgoOrders) {
            if (isFalse(AlgoOrder.Status.ACTIVE.equals(algoOrder.getStatus()))) {
                return DealTransition.command(support.refreshAlgoOrderCommand(dealContext, algoOrder.getId()));
            }
        }
        // Main protection активна и подтверждена → сделка готова к сопровождению.
        return DealTransition.transition(Deal.Status.MANAGING);
    }
}
