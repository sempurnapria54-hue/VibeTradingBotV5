package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmHandler;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса ACTIVE: ведёт сделку, пока её транши живут своими
 * жизненными циклами. Стадий входа и сопровождения здесь нет — они
 * принадлежат траншу; сделочный проход отвечает на два вопроса уровня
 * сделки.
 *
 * <p><b>Запрошено сворачивание</b> (проставлен {@code shutdownReason}) →
 * EXIT_PENDING: дальше вход не открывается ни одним траншем
 * (docs/spec/deal-tranche-lifecycle.json §riskCreatingUnderCollapse).
 *
 * <p><b>Все транши терминальны</b> → намерение закрыть сделку. НАМЕРЕНИЕ,
 * а не переход: право на терминал даёт гейт живого риска, и проверяет его
 * машина сделки, а не этот handler.
 *
 * <p>См. docs/components/DealActiveHandler.md.
 */
@Component
@RequiredArgsConstructor
public class DealActiveHandler implements DealFsmHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ACTIVE;
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (nonNull(deal.getShutdownReason())) {
            return Optional.of(DealTransition.transition(Deal.Status.EXIT_PENDING));
        }
        if (isTrue(deal.allTranchesTerminal())) {
            return Optional.of(DealTransition.transition(Deal.Status.CLOSED));
        }
        return Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        // Работа сделки в ACTIVE идёт траншами: собственной команды у
        // сделочного прохода на этом статусе нет.
        return DealTransition.stay();
    }
}
