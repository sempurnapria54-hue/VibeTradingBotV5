package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Выбирает обработчик по текущему статусу СДЕЛКИ, запускает его и
 * возвращает переход: команды и, если он разрешён, новый статус
 * (docs/components/DealStateMachine.md).
 *
 * <p><b>Статусов у агрегата пять, обработчиков — три:</b> активная
 * сделка, координированный выход, ошибочное состояние. Терминальные
 * статусы обработчиков не имеют — делать по ним нечего.
 *
 * <p><b>Транши прогоняет не эта машина</b>, а {@code DealTrancheStateMachine},
 * которую зовут обработчики активной сделки и координированного выхода;
 * обработчик ошибочного состояния её не зовёт. Дом ответа и цена обоих
 * неверных прочтений — docs/processes/fsm-execution-layering.md.
 */
@Slf4j
@Service
public class DealStateMachine {

    private final Map<Deal.Status, DealHandler> handlers;
    private final DealTransitionGate transitionGate;

    public DealStateMachine(List<DealHandler> handlers, DealTransitionGate transitionGate) {
        this.handlers = handlers.stream().collect(toMap(DealHandler::handledStatus, identity()));
        this.transitionGate = transitionGate;
    }

    /**
     * Один проход FSM сделки.
     *
     * <p><b>Ребро, предложенное обработчиком, гейтится матрицей здесь.</b>
     * Отвергнутое ребро команд прохода не отменяет: работа сделана, а
     * статус остаётся прежним до следующего прохода.
     */
    public DealTransition run(DealContext dealContext) {
        DealHandler handler = handlers.get(dealContext.getDeal().getStatus());
        if (isNull(handler)) {
            log.debug("No handler for deal status dealId={} status={}",
                    dealContext.getDeal().getId(), dealContext.getDeal().getStatus());
            return DealTransition.stay();
        }
        DealTransition transition = handler.handle(dealContext);
        if (isFalse(transition.movesStatus())) {
            return transition;
        }
        if (isTrue(transitionGate.transitionAllowed(dealContext, transition.getNextStatus()))) {
            return transition;
        }
        log.warn("Deal transition refused by matrix dealId={} from={} to={}",
                dealContext.getDeal().getId(), dealContext.getDeal().getStatus(), transition.getNextStatus());
        return transition.withoutStatus();
    }
}
