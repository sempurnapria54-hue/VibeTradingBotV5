package com.example.tradingcore.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.BalanceContainerDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealCashFlowDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Собирает {@link DealContext} одного прохода FSM
 * (docs/components/DealContextService.md).
 *
 * <p>Сырых фактов площадки контекст не несёт: их сперва применяют к базе
 * команды добычи, и сборка читает уже обновлённый граф. Служебная сборка
 * действием не является — между проходами она ничего не переживает.
 *
 * <p><b>Радиус — биржевой счёт, а не площадка.</b> Торговое состояние
 * (база риска, серия убытков, ступень, счётчик слепоты) живёт на счёте, и
 * у двух счетов одной площадки оно разное
 * (docs/models/domain/core/ExchangeAccount.md).
 *
 * <p><b>Фичи момента снимаются ОДНИМ чтением у владельца данных</b> и
 * кладутся в контекст целиком — значения, их предыдущие значения,
 * структуры, цены и фаза. Потребляют их условия шагов; собирать их
 * россыпью по операнду значило бы составить контекст из значений разных
 * моментов (docs/architecture/market-data-collection.md §Производные).
 *
 * <p><b>У сделки без закреплённой детали фич не снимается.</b> Шагов у
 * неё нет ни одного, оценивать нечего — и вызов к соседу был бы платой за
 * ответ, который никто не спросит.
 */
@Service
@RequiredArgsConstructor
public class DealContextService {

    private final ExchangeAccountDataService exchangeAccountDataService;
    private final InstrumentDataService instrumentDataService;
    private final StrategyDataService strategyDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final PositionDataService positionDataService;
    private final DealTrancheDataService dealTrancheDataService;
    private final BalanceContainerDataService balanceContainerDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealCashFlowDataService dealCashFlowDataService;
    private final MarketFeatureService marketFeatureService;
    private final DealContextProperties properties;

    /** Контекст одного прохода: граф сделки, операнды и признаки полноты. */
    public DealContext build(Deal deal) {
        reloadRuntimeGraph(deal);
        Integer limit = properties.getCashFlowWindowLimit();
        List<DealCashFlow> cashFlows = dealCashFlowDataService.findByDealWindow(deal.getId(), limit);
        Boolean graphComplete = graphComplete(deal);
        Boolean cashFlowsComplete = cashFlowsComplete(cashFlows, limit);
        Instrument instrument = instrumentDataService.getRequiredById(deal.getInstrumentId());
        StrategyDetail pinnedDetail = pinnedDetail(deal);
        Strategy definition = ownerOf(pinnedDetail);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(exchangeAccountDataService.getRequiredById(deal.getExchangeAccountId()))
                .instrument(instrument)
                .strategyDetail(pinnedDetail)
                .strategy(definition)
                .marketFeatures(features(definition, pinnedDetail, instrument))
                .balanceContainer(balanceContainerDataService
                        .findByExchangeAccountId(deal.getExchangeAccountId()).orElse(null))
                .actionStates(dealActionStateDataService.findByDealId(deal.getId()))
                .cashFlows(cashFlows)
                .graphComplete(graphComplete)
                .flowsComplete(nonNull(deal.getBillsFetchedThrough()) && isTrue(cashFlowsComplete))
                .computationAllowed(isTrue(graphComplete) && isTrue(cashFlowsComplete))
                .build();
    }

    /**
     * Перечитать граф сделки из базы на переданную модель: ноги, отдельные
     * условные заявки, эпизоды позиции и транши со своими заявками.
     *
     * <p>Ноги читаются одним запросом на сделку и раскладываются по
     * траншам в памяти. Запрос на транш дал бы N+1 обращений на сетке из
     * N траншей, а число траншей задаёт стратегия — то есть оно росло бы
     * вместе с ней (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * <p>Транши грузятся <b>все, включая терминальные</b>: ноги
     * собираются их обходом, а числа риска и экспозиция считаются по ногам
     * всей сделки. Фильтр «нетерминальные» опустошал бы обе коллекции
     * ровно в той точке, где по ним считаются числа
     * (docs/components/DealContextService.md §«Объёмы загрузки»).
     */
    public void reloadRuntimeGraph(Deal deal) {
        List<Order> orders = orderDataService.findByDealId(deal.getId());
        List<AlgoOrder> algoOrders = algoOrderDataService.findByDealId(deal.getId());
        deal.setOrders(orders);
        deal.setAlgoOrders(algoOrders);
        deal.setPositions(positionDataService.findEpisodes(deal.getId()));
        deal.setTranches(withOwnOrders(dealTrancheDataService.findByDealId(deal.getId()), orders, algoOrders));
    }

    /**
     * Граф сделки предъявлен целиком
     * (docs/spec/deal-context-load.json §graphComplete). Загрузка идёт
     * одним заходом на коллекцию, поэтому «загружено» решается не
     * пометкой на строке, а НАЛИЧИЕМ коллекции там, где она обязана быть:
     *
     * <ul>
     *   <li>транши обязаны быть у всякой сделки — их материализует
     *       создание, и удостоверителя у их пустоты нет ни одного;</li>
     *   <li>эпизод обязан быть, если позиция по сделке наблюдалась;</li>
     *   <li>ноги обязаны быть, если входная заявка отправлялась.</li>
     * </ul>
     *
     * <p><b>Удостоверители у эпизодов и ног разные, и это счётно.</b>
     * Уровень «вход отправлялся» ошибается для эпизодов в обе стороны: у
     * восстановленной сделки заявки не было, а эпизод есть; у сделки со
     * снятым до налива входом заявка была, а эпизода нет. Поэтому у
     * эпизодов удостоверитель — «позиция наблюдалась», у ног —
     * durable-колонка нижней границы окна линковки движений.
     *
     * <p>Встроенные защиты и отдельные условные заявки грузятся вместе со
     * своими носителями и отдельного конъюнкта не требуют.
     */
    private Boolean graphComplete(Deal deal) {
        boolean tranchesComplete = isNotEmpty(deal.getTranches());
        boolean episodesComplete = isNotEmpty(deal.getPositions()) || isFalse(deal.positionObserved());
        boolean legsComplete = isNotEmpty(deal.getOrders()) || isNull(deal.getBillsWindowBegin());
        return tranchesComplete && episodesComplete && legsComplete;
    }

    /**
     * Разбивка движений предъявлена целиком: выборка не уперлась в
     * потолок окна (docs/spec/deal-context-load.json §cashFlowsComplete).
     * Читается на строку больше потолка, поэтому «уперлась» — это строго
     * больше потолка, а не равно ему.
     */
    private Boolean cashFlowsComplete(List<DealCashFlow> cashFlows, Integer limit) {
        return cashFlows.size() <= limit;
    }

    /**
     * Закреплённая деталь; пусто у ВОССТАНОВЛЕННОЙ сделки — заводил её не
     * выбор входа, и закреплять было нечего
     * (docs/models/domain/aggregate/Deal.md). Пустота здесь — факт тропы,
     * а не отсутствующая строка: требовать деталь у той сделки, у которой
     * её не бывает, значило бы оставить её живой риск без единого прохода.
     */
    private StrategyDetail pinnedDetail(Deal deal) {
        return isNull(deal.getStrategyDetailId())
                ? null
                : strategyDataService.getRequiredDetailByIdWithTree(deal.getStrategyDetailId());
    }

    /**
     * Фичи момента для оценки условий детали; пусто у сделки без
     * закреплённой детали и у детали, чья копия исчезла.
     *
     * <p><b>Привязки читаются у копии-ВЛАДЕЛЬЦА детали</b>, а не у
     * активной копии пары: сделка ведётся по закреплённой детали, а
     * активная копия к моменту прохода может быть уже другой.
     */
    private MarketFeatures features(Strategy owner, StrategyDetail detail, Instrument instrument) {
        if (isNull(owner) || isNull(detail)) {
            return null;
        }
        return marketFeatureService.readForEvaluation(owner, detail, instrument);
    }

    /**
     * Копия-владелец закреплённой детали; пусто — детали нет либо её копия
     * исчезла.
     *
     * <p><b>Читается ОДИН раз на проход.</b> Копия — операнд сразу двух
     * половин контекста: привязок вычислений для фич и административного
     * статуса определения для выходных проверок
     * (docs/components/DealActiveHandler.md §«Выходные проверки»). Второе
     * чтение той же строки дало бы второй запрос и — на разошедшихся
     * моментах — два разных ответа в одном проходе.
     */
    private Strategy ownerOf(StrategyDetail detail) {
        return isNull(detail)
                ? null
                : strategyDataService.findOwnerOfDetailWithSettings(detail.getId()).orElse(null);
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
