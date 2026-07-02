package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Оркестратор FSM сделки: выбирает {@link FsmHandler} по текущему
 * {@link Deal.Status}, запускает его и возвращает {@link DealTransition}.
 * Запускается DealOrchestratorJob. Владелец оркестрации порядка ног
 * REPLACE — петля/handler по подтверждённым фактам (фабрика — одна команда
 * за проход, docs/decisions/action-orchestration-vs-command.md). Сам на
 * биржу не ходит, команды не исполняет, аудит не строит. Terminal-статусы
 * handler'ов не имеют — для них проход пустой. См.
 * docs/components/DealStateMachine.md.
 */
@Slf4j
@Service
public class DealStateMachine {

    private final Map<Deal.Status, FsmHandler> handlers;

    public DealStateMachine(List<FsmHandler> handlers) {
        this.handlers = handlers.stream()
                                .collect(toMap(FsmHandler::supportedStatus, identity()));
    }

    /**
     * Один проход FSM по сделке: handler текущего статуса → команды/переход.
     */
    public DealTransition advance(DealContext dealContext) {
        Deal.Status status = dealContext.getDeal()
                                        .getStatus();
        FsmHandler handler = handlers.get(status);
        if (isNull(handler)) {
            log.debug("No FSM handler for status {} dealId={}", status, dealContext.getDeal()
                                                                                   .getId());
            return DealTransition.stay();
        }
        return handler.checkEntry(dealContext)
                      .or(() -> handler.checkTransition(dealContext))
                      .orElseGet(() -> handler.handle(dealContext));
    }
}
