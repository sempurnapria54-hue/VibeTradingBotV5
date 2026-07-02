package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.BalanceContainerDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Собирает {@link DealContext} для одного прохода FSM: Deal с runtime graph
 * (orders/algoOrders/position по deal_id), Exchange, Instrument, pinned
 * StrategyDetail (по запиненному при открытии {@code strategyDetailId}, не из
 * живой активной стратегии — сопровождение и аварийное закрытие работают
 * одинаково при {@code Strategy.INACTIVE}/{@code DELETED}), последний persisted
 * BalanceContainer, action-states и finalization-states. Exchange facts сырыми
 * не кладёт — они уже применены refresh-командами к БД, сервис собирает
 * обновлённый graph. См. docs/components/DealContextService.md.
 */
@Service
@RequiredArgsConstructor
public class DealContextService {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final StrategyDataService strategyDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final PositionDataService positionDataService;
    private final BalanceContainerDataService balanceContainerDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealFinalizationStateDataService dealFinalizationStateDataService;

    public DealContext build(Deal deal) {
        Instrument instrument = instrumentDataService.getRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeDataService.getRequiredById(instrument.getExchangeId());
        StrategyDetail strategyDetail = strategyDataService.getRequiredDetailByIdWithTree(deal.getStrategyDetailId());
        reloadRuntimeGraph(deal);
        return DealContext.builder()
                .deal(deal)
                .exchange(exchange)
                .instrument(instrument)
                .strategyDetail(strategyDetail)
                .balanceContainer(balanceContainerDataService.findByExchangeId(exchange.getId()).orElse(null))
                .actionStates(dealActionStateDataService.findByDealId(deal.getId()))
                .finalizationStates(dealFinalizationStateDataService.findByDealId(deal.getId()))
                .build();
    }

    /** Перечитать runtime-граф сделки (orders/algoOrders/position) из БД на переданную модель. */
    public void reloadRuntimeGraph(Deal deal) {
        deal.setOrders(orderDataService.findByDealId(deal.getId()));
        deal.setAlgoOrders(algoOrderDataService.findByDealId(deal.getId()));
        deal.setPosition(positionDataService.findByDealId(deal.getId()).orElse(null));
    }
}
