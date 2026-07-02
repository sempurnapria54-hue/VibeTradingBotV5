package com.example.tradingbot.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.action.KillSwitchExecutor;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Триггер аварийного kill-switch ({@link KillSwitchExecutor}) для
 * реактивной реакции холда. Скоуп по уровню: L3 (инструмент) — kill-switch по
 * графу триггерной сделки (teardown + сверка реального состояния внутри
 * действия); L4 (биржа) — каскадный sweep по всем активным сделкам биржи, по
 * вызову на сделку. Per-deal best-effort: сбой одной сделки не срывает каскад.
 * Это тот «тонкий триггер», который подключает hold-подсистема (раньше — орфан
 * DealFsmSupport.killSwitchCommand(), удалён на сверке CODE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final KillSwitchExecutor killSwitchExecutor;

    /**
     * L3: kill-switch по инструменту триггерной сделки (её runtime graph +
     * instId). {@code true} — закрытие риска подтверждено отчётом kill-switch
     * (гейтит терминал AnomalyReport).
     */
    public Boolean fireInstrument(DealContext dealContext) {
        return execute(dealContext);
    }

    /**
     * L4: каскадный kill-switch по всем активным сделкам биржи (best-effort
     * per-deal). {@code true} — закрытие подтверждено по <b>каждой</b> сделке
     * каскада; сбой/неподтверждение хоть одной → {@code false} (отчёт остаётся
     * открытым).
     */
    public Boolean fireExchange(Long exchangeId) {
        boolean allConfirmed = true;
        for (Deal deal : dealDataService.findActiveByExchangeId(exchangeId)) {
            try {
                allConfirmed = isTrue(execute(dealContextService.build(deal))) && allConfirmed;
            } catch (RuntimeException e) {
                log.error("Exchange kill-switch failed dealId={} exchangeId={}", deal.getId(), exchangeId, e);
                allConfirmed = false;
            }
        }
        return allConfirmed;
    }

    private Boolean execute(DealContext dealContext) {
        return killSwitchExecutor.execute(dealContext).getSuccess();
    }
}
