package com.example.tradingbot.domain.safety;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Эмитент команды EXECUTE_KILL_SWITCH для реактивной реакции холда. Скоуп по
 * уровню: L3 (инструмент) — kill-switch по графу триггерной сделки (доминирующая
 * позиция инструмента покрыта безусловным финальным close в
 * {@code KillSwitchExecutor}); L4 (биржа) — каскадный sweep по всем активным
 * сделкам биржи, по команде на сделку (порядок «защиту последней» — внутри
 * каждого kill-switch'а). Per-deal best-effort: сбой одной сделки не срывает
 * каскад. Это тот «тонкий эмиттер», который подключает hold-подсистема (раньше
 * — орфан DealFsmSupport.killSwitchCommand(), удалён на сверке CODE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final ServiceCommandExecutor serviceCommandExecutor;

    /** L3: kill-switch по инструменту триггерной сделки (её runtime graph + instId). */
    public void fireInstrument(DealContext dealContext) {
        execute(dealContext);
    }

    /** L4: каскадный kill-switch по всем активным сделкам биржи (best-effort per-deal). */
    public void fireExchange(Long exchangeId) {
        for (Deal deal : dealDataService.findActiveByExchangeId(exchangeId)) {
            try {
                execute(dealContextService.build(deal));
            } catch (RuntimeException e) {
                log.error("Exchange kill-switch failed dealId={} exchangeId={}", deal.getId(), exchangeId, e);
            }
        }
    }

    private void execute(DealContext dealContext) {
        ServiceCommand command = ServiceCommand.builder()
                .type(ServiceCommandType.EXECUTE_KILL_SWITCH)
                .dealId(dealContext.getDeal().getId())
                .build();
        serviceCommandExecutor.execute(command, dealContext);
    }
}
