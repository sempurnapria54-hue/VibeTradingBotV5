package com.example.tradingcore.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.persistence.service.DealDataService;
import java.util.List;
import java.util.Objects;
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
 * объектом радиуса. Популяцию сделок радиуса сервис читает сам, поэтому
 * ручной вызов, у которого триггерной сделки нет, снимает тот же риск, что
 * и автоматический.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final KillSwitchExecutor killSwitchExecutor;

    /**
     * Инструментный радиус: снятие риска по нетерминальным сделкам пары и
     * позиции пары вне графа сделок. Возврат — подтверждение закрытия
     * риска; оно гейтит терминал отчёта.
     *
     * @param objectContext объект радиуса: счёт и инструмент; сделка — у
     *                      автоматического сигнала, поднятого проходом
     */
    public Boolean fireInstrument(DealContext objectContext) {
        Long exchangeAccountId = objectContext.getExchangeAccount().getId();
        List<Deal> population = dealDataService.findNonTerminalOnPair(exchangeAccountId,
                objectContext.getInstrument().getId());
        boolean dealsConfirmed = fireDeals(population, objectContext, exchangeAccountId);
        return isTrue(killSwitchExecutor.closePositionsOutsideDeals(exchangeAccountId,
                objectContext.getInstrument().getExternalId(), population)) && dealsConfirmed;
    }

    /**
     * Радиус биржевого счёта: каскадный обход всех нетерминальных сделок
     * счёта, по вызову исполнителя на каждую, и позиции счёта вне графа
     * сделок.
     *
     * <p><b>Агрегация консервативна:</b> неподтверждённая сделка либо
     * живая позиция делает неподтверждённым весь каскад, и отчёт не
     * терминализуется.
     */
    public Boolean fireExchangeAccount(Long exchangeAccountId) {
        List<Deal> population = dealDataService.findNonTerminalByExchangeAccountId(exchangeAccountId);
        boolean dealsConfirmed = fireDeals(population, null, exchangeAccountId);
        return isTrue(killSwitchExecutor.closePositionsOutsideDeals(exchangeAccountId, null, population))
                && dealsConfirmed;
    }

    /**
     * Обход best-effort по сделкам: сбой одной обход не срывает — иначе
     * первая же сорвала бы снятие риска по остальным.
     */
    private boolean fireDeals(List<Deal> population, DealContext objectContext, Long exchangeAccountId) {
        boolean allConfirmed = true;
        for (Deal deal : population) {
            try {
                allConfirmed = isTrue(execute(contextOf(deal, objectContext))) && allConfirmed;
            } catch (RuntimeException e) {
                log.error("Kill-switch failed on a deal dealId={} exchangeAccountId={}",
                        deal.getId(), exchangeAccountId, e);
                allConfirmed = false;
            }
        }
        return allConfirmed;
    }

    /**
     * Триггерная сделка идёт тем контекстом, в котором её держит проход:
     * исполнитель перечитывает её граф на месте. Прочим контекст
     * собирается.
     */
    private DealContext contextOf(Deal deal, DealContext objectContext) {
        boolean trigger = nonNull(objectContext) && nonNull(objectContext.getDeal())
                && Objects.equals(objectContext.getDeal().getId(), deal.getId());
        return trigger ? objectContext : dealContextService.build(deal);
    }

    private Boolean execute(DealContext dealContext) {
        return killSwitchExecutor.execute(dealContext).getSuccess();
    }
}
