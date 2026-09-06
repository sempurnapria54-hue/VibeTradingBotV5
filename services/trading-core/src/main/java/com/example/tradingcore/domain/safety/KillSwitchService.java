package com.example.tradingcore.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Триггер аварийного снятия риска для полной реакции ступени
 * (docs/components/KillSwitchService.md).
 *
 * <p>Снятие риска — состав <b>только полных</b> реакций: у мягких ступеней
 * такого шага нет, и сервис на них не зовётся. Сам ход и сверку реального
 * состояния делает исполнитель.
 *
 * <p><b>Происхождение полной реакции сервису безразлично:</b>
 * автоматический детектор и ручной вызов держателя приходят одинаково —
 * радиусом и триггерной сделкой.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final KillSwitchExecutor killSwitchExecutor;

    /**
     * Инструментный радиус: снятие риска по графу <b>триггерной</b>
     * сделки. Возврат — подтверждение закрытия риска; оно гейтит терминал
     * отчёта.
     */
    public Boolean fireInstrument(DealContext dealContext) {
        return execute(dealContext);
    }

    /**
     * Радиус биржевого счёта: каскадный обход всех нетерминальных сделок
     * счёта, по вызову исполнителя на каждую.
     *
     * <p><b>Агрегация консервативна:</b> неподтверждённая сделка делает
     * неподтверждённым весь каскад, и отчёт не терминализуется. Обход
     * best-effort по сделкам: сбой одной каскад не срывает — иначе первая
     * же сорвала бы снятие риска по остальным.
     */
    public Boolean fireExchangeAccount(Long exchangeAccountId) {
        boolean allConfirmed = true;
        for (Deal deal : dealDataService.findNonTerminalByExchangeAccountId(exchangeAccountId)) {
            try {
                allConfirmed = isTrue(execute(dealContextService.build(deal))) && allConfirmed;
            } catch (RuntimeException e) {
                log.error("Account-wide kill-switch failed dealId={} exchangeAccountId={}",
                        deal.getId(), exchangeAccountId, e);
                allConfirmed = false;
            }
        }
        return allConfirmed;
    }

    private Boolean execute(DealContext dealContext) {
        return killSwitchExecutor.execute(dealContext).getSuccess();
    }
}
