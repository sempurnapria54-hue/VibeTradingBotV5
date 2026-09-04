package com.example.tradingbot.domain.command.calc;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.service.market.IndicatorService;
import com.example.tradingbot.domain.service.market.MarketPriceDataService;
import com.example.tradingbot.domain.service.market.MarketStructureService;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Собирает свежий {@link CalculationContext} для одного рассчитываемого
 * StrategyAction из DealContext + свежих runtime-data
 * (docs/components/CalculationContextFactory.md). Тяжёлые данные не
 * считает — читает готовые результаты через сервисы. Работает защитно:
 * нет свежей рыночной цены → controlled {@link CalculationException}
 * (а не расчёт по старым данным). MarketPriceData в рамках одного context
 * получается один раз. IntegrationService напрямую и REFRESH_BALANCE не
 * вызывает; freshness баланса обеспечивает FSM/handler до запуска.
 */
@Component
@RequiredArgsConstructor
public class CalculationContextFactory {

    private static final String NO_MARKET_PRICE = "NO_MARKET_PRICE";

    private final InstrumentExternalRulesDataService rulesDataService;
    private final MarketPriceDataService marketPriceDataService;
    private final StrategyDataService strategyDataService;
    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;

    public CalculationContext build(StrategyAction action, DealContext dealContext) {
        Instrument instrument = dealContext.getInstrument();
        Long instrumentId = instrument.getId();

        MarketPriceData marketPriceData = marketPriceDataService.getMarketPriceData(
                instrumentId, instrument.getExternalId());
        if (isNull(marketPriceData)) {
            throw new CalculationException(CalculationError.temporary(
                    NO_MARKET_PRICE, "No fresh market price for instrument " + instrumentId));
        }

        InstrumentExternalRules rules = rulesDataService.findByInstrumentId(instrumentId).orElse(null);
        Optional<Strategy> strategy = strategyDataService.findActiveByInstrumentIdWithSettings(instrumentId);
        List<StrategyIndicatorSetting> indicatorSettings = strategy
                .map(Strategy::getIndicatorSettings)
                .orElseGet(List::of);
        List<StrategyMarketStructureSetting> structureSettings = strategy
                .map(Strategy::getMarketStructureSettings)
                .orElseGet(List::of);

        return CalculationContext.builder()
                .deal(dealContext.getDeal())
                .instrument(instrument)
                .strategyDetail(dealContext.getStrategyDetail())
                .action(action)
                .instrumentExternalRules(rules)
                .marketPriceData(marketPriceData)
                .indicatorValues(latestIndicatorValues(instrumentId, indicatorSettings))
                .marketStructures(latestStructures(instrumentId, structureSettings))
                // marketPhase не заполняется: калькуляторы фазы 1 его не потребляют, а
                // MarketPhaseService.getCurrentPhase повторно перечитал бы те же индикаторы/
                // структуры. Заполняется, когда появится потребитель (форвард).
                .balanceContainer(dealContext.getBalanceContainer())
                .activePosition(dealContext.getDeal().livePosition())
                .entryOrder(resolveEntryOrder(dealContext))
                .strategyDirection(dealContext.getDeal().getDirection())
                .indicatorSettings(indicatorSettings)
                .marketStructureSettings(structureSettings)
                .build();
    }

    private List<IndicatorValue> latestIndicatorValues(Long instrumentId,
                                                       List<StrategyIndicatorSetting> settings) {
        if (isEmpty(settings)) {
            return List.of();
        }
        return indicatorService.getLatestValues(instrumentId, settings);
    }

    private List<MarketStructure> latestStructures(Long instrumentId,
                                                   List<StrategyMarketStructureSetting> settings) {
        if (isEmpty(settings)) {
            return List.of();
        }
        return settings.stream()
                .map(setting -> marketStructureService.getLatestStructure(instrumentId, setting))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    private Order resolveEntryOrder(DealContext dealContext) {
        List<Order> orders = dealContext.getDeal().getOrders();
        if (isEmpty(orders)) {
            return null;
        }
        return orders.stream()
                .filter(this::isEntryType)
                .findFirst()
                .orElse(null);
    }

    private boolean isEntryType(Order order) {
        return Order.Type.ENTRY.equals(order.getType())
                || Order.Type.ENTRY_ATTACHED_STOP_LOSS.equals(order.getType());
    }
}
