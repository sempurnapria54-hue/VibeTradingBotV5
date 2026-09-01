package com.example.tradingbot.domain.deal.handler;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmHandler;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса EXIT_PENDING: сделка сворачивается — ждём, пока
 * закрывающие действия траншей дойдут до конца.
 *
 * <p>Окно сворачивания открыто именно этим статусом: в нём закрывающее
 * исполнение УРОВНЯ СДЕЛКИ приписывается траншам правилом сопоставления
 * (docs/models/domain/aggregate/DealTranche.md), и потому сверка
 * экспозиции в окне не расходится.
 *
 * <p>Все транши терминальны → намерение закрыть сделку; право на терминал
 * даёт гейт живого риска в машине сделки.
 *
 * <p>См. docs/components/DealExitPendingHandler.md.
 */
@Component
@RequiredArgsConstructor
public class DealExitPendingHandler implements DealFsmHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.EXIT_PENDING;
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        if (isTrue(dealContext.getDeal().allTranchesTerminal())) {
            return Optional.of(DealTransition.transition(Deal.Status.CLOSED));
        }
        return Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        // Сворачивание ведут транши своими выходами; собственной команды
        // у сделочного прохода здесь нет.
        return DealTransition.stay();
    }
}
