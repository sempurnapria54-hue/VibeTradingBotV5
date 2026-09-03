package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.BalanceContainerDataService;
import com.example.tradingbot.config.DealOrchestratorProperties;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealCashFlowDataService;
import com.example.tradingbot.persistence.service.DealTrancheDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

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
    private final DealCashFlowDataService dealCashFlowDataService;
    private final DealTrancheDataService dealTrancheDataService;
    private final DealOrchestratorProperties orchestratorProperties;

    public DealContext build(Deal deal) {
        Instrument instrument = instrumentDataService.getRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeDataService.getRequiredById(instrument.getExchangeId());
        reloadRuntimeGraph(deal);
        Integer cashFlowLimit = orchestratorProperties.getCashFlowWindowLimit();
        List<DealCashFlow> cashFlows = dealCashFlowDataService.findByDealWindow(deal.getId(), cashFlowLimit);
        return DealContext.builder()
                .deal(deal)
                .exchange(exchange)
                .instrument(instrument)
                .strategyDetail(pinnedDetail(deal))
                .balanceContainer(balanceContainerDataService.findByExchangeId(exchange.getId()).orElse(null))
                .actionStates(dealActionStateDataService.findByDealId(deal.getId()))
                .cashFlows(cashFlows)
                .graphComplete(graphComplete(deal))
                .flowsComplete(flowsComplete(deal, cashFlows, cashFlowLimit))
                .build();
    }

    /**
     * Разбивка движений добыта И предъявлена целиком
     * (docs/spec/deal-context-load.json §flowsComplete). Конъюнкта два, и
     * они разные по природе: первый — что добыча вообще выполнялась
     * (durable-факт сделки; на аварийной тропе звено добычи не эмитится
     * вовсе), второй — что предъявленное не усечено потолком выборки.
     */
    private Boolean flowsComplete(Deal deal, List<DealCashFlow> cashFlows, Integer limit) {
        return nonNull(deal.getBillsFetchedThrough()) && cashFlows.size() <= limit;
    }

    /**
     * Закреплённая деталь; {@code null} у ВОССТАНОВЛЕННОЙ сделки —
     * заводил её не выбор входа, и выбирать было нечего
     * (docs/models/domain/aggregate/Deal.md). Пустота здесь — факт
     * тропы, а не отсутствующая строка: требовать деталь у той сделки, у
     * которой её не бывает, значило бы ронять проход на штатном
     * состоянии.
     */
    private StrategyDetail pinnedDetail(Deal deal) {
        return isNull(deal.getStrategyDetailId())
                ? null
                : strategyDataService.getRequiredDetailByIdWithTree(deal.getStrategyDetailId());
    }

    /**
     * Граф сделки предъявлен целиком
     * (docs/spec/deal-context-load.json §graphComplete). Загрузка идёт
     * одним заходом на коллекцию, поэтому «загружено» решается не
     * пометкой на строке, а НАЛИЧИЕМ коллекции там, где она обязана быть:
     *
     * <ul>
     *   <li>транши обязаны быть у всякой сделки — их материализует
     *       создание;</li>
     *   <li>эпизод обязан быть, если позиция по сделке наблюдалась;</li>
     *   <li>ноги обязаны быть, если входная заявка отправлялась
     *       (durable-признак — нижняя граница окна линковки).</li>
     * </ul>
     *
     * <p>Встроенные защиты и отдельные условные заявки грузятся вместе
     * со своими носителями и отдельного конъюнкта не требуют.
     */
    private Boolean graphComplete(Deal deal) {
        boolean tranchesComplete = isNotEmpty(deal.getTranches());
        boolean episodesComplete = isNotEmpty(deal.getPositions()) || isFalse(deal.positionObserved());
        boolean legsComplete = isNotEmpty(deal.getOrders()) || isNull(deal.getBillsWindowBegin());
        return tranchesComplete && episodesComplete && legsComplete;
    }

    /**
     * Перечитать runtime-граф сделки из БД на переданную модель: заявки,
     * позиция и ТРАНШИ со своими заявками.
     *
     * <p>Заявки читаются одним запросом на сделку и раскладываются по
     * траншам в памяти — не запросом на транш. Иначе проход давал бы
     * N+1 обращений на сетке из N траншей, а число траншей задаёт
     * стратегия, то есть росло бы вместе с ней.
     */
    public void reloadRuntimeGraph(Deal deal) {
        List<Order> orders = orderDataService.findByDealId(deal.getId());
        List<AlgoOrder> algoOrders = algoOrderDataService.findByDealId(deal.getId());
        deal.setOrders(orders);
        deal.setAlgoOrders(algoOrders);
        deal.setPositions(positionDataService.findEpisodes(deal.getId()));
        deal.setTranches(withOwnOrders(dealTrancheDataService.findByDealId(deal.getId()), orders, algoOrders));
    }

    /** Разложить заявки сделки по их траншам; заявка без транша ничьей не становится. */
    private List<DealTranche> withOwnOrders(List<DealTranche> tranches, List<Order> orders,
                                            List<AlgoOrder> algoOrders) {
        Map<Long, List<Order>> ordersByTranche = orders.stream()
                .filter(order -> nonNull(order.getDealTrancheId()))
                .collect(groupingBy(Order::getDealTrancheId));
        Map<Long, List<AlgoOrder>> algoByTranche = algoOrders.stream()
                .filter(algoOrder -> nonNull(algoOrder.getDealTrancheId()))
                .collect(groupingBy(AlgoOrder::getDealTrancheId));
        tranches.forEach(tranche -> {
            tranche.setOrders(ordersByTranche.getOrDefault(tranche.getId(), List.of()));
            tranche.setAlgoOrders(algoByTranche.getOrDefault(tranche.getId(), List.of()));
        });
        return tranches;
    }
}
