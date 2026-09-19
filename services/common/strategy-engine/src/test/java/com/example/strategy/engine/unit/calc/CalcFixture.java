package com.example.strategy.engine.unit.calc;

import static java.util.Objects.nonNull;

import com.example.strategy.engine.calc.CalculationContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Базовая сборка групп документа
 * `.claude/tests/cases/strategy-engine-calculation.md`.
 *
 * <p><b>Субстрата у предмета нет вовсе:</b> ни контейнеров, ни контекста
 * каркаса, ни стабов — калькулятор конструируется {@code new}, а состояние
 * собирается <b>настоящими полями доменных моделей</b>, а не подменёнными
 * предикатами (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 * Живой риск эпизода поэтому вытекает из его статуса и наблюдённого
 * размера, экспозиция транша — из его налива, а последняя ступень набора —
 * из состава действий шага и их идентификаторов.
 *
 * <p><b>Десятичные ожидания сверяются значением, а не равенством
 * объекта:</b> масштаб результата деления не объявлен ни одним домом, и
 * {@code equals} у {@link BigDecimal} мерил бы его.
 */
final class CalcFixture {

    /** Последняя цена базовой сборки. */
    static final String LAST_PRICE = "3000";

    /** Лучшая цена покупки базовой сборки. */
    static final String BID_PRICE = "2999.5";

    /** Лучшая цена продажи базовой сборки. */
    static final String ASK_PRICE = "3000.5";

    /** Шаг цены инструмента базовой сборки. */
    static final String TICK_SIZE = "0.1";

    /** Ставка комиссии базовой сборки. */
    static final String FEE_RATE = "0.0005";

    /** Авторское имя операнда структуры рынка. */
    static final String STRUCTURE_KEY = "structure-1";

    /** Авторское имя операнда индикатора. */
    static final String INDICATOR_KEY = "atr-1";

    private CalcFixture() {
    }

    // --- правила инструмента ----------------------------------------------

    /** Правила базовой сборки: шаг цены {@code 0.1}, ставка {@code 0.0005}. */
    static InstrumentExternalRules rules() {
        return rules(TICK_SIZE, FEE_RATE, "1", "0.1", "0.1");
    }

    /** Правила базовой сборки с изменённым шагом цены. */
    static InstrumentExternalRules rulesWithTick(String tickSize) {
        return rules(tickSize, FEE_RATE, "1", "0.1", "0.1");
    }

    /** Правила базовой сборки с изменённой ставкой комиссии. */
    static InstrumentExternalRules rulesWithFee(String feeRate) {
        return rules(TICK_SIZE, feeRate, "1", "0.1", "0.1");
    }

    /**
     * Правила инструмента целиком.
     *
     * @param tickSize      шаг цены; {@code null} — значения нет
     * @param feeRate       ставка комиссии; {@code null} — не резолвится
     * @param contractValue стоимость контракта
     * @param lotSize       шаг лота
     * @param minSize       минимальный торговый размер
     * @return правила инструмента
     */
    static InstrumentExternalRules rules(String tickSize, String feeRate, String contractValue,
                                         String lotSize, String minSize) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalTickSize(tickSize);
        rules.setExternalTakerFeeRate(feeRate);
        rules.setExternalContractValue(contractValue);
        rules.setExternalLotSize(lotSize);
        rules.setExternalMinSize(minSize);
        return rules;
    }

    // --- рынок -------------------------------------------------------------

    /** Снапшот цен базовой сборки: последняя {@code 3000}, бид {@code 2999.5}, аск {@code 3000.5}. */
    static MarketPriceData prices() {
        return prices(LAST_PRICE, BID_PRICE, ASK_PRICE);
    }

    /** Снапшот цен; {@code null} у любой из них — цена не наблюдена. */
    static MarketPriceData prices(String last, String bid, String ask) {
        MarketPriceData priceData = new MarketPriceData();
        priceData.setExternalLastPrice(decimal(last));
        priceData.setExternalBidPrice(decimal(bid));
        priceData.setExternalAskPrice(decimal(ask));
        return priceData;
    }

    /** Структура рынка с уровнями по типам; {@code null} у цены — уровень без цены. */
    static MarketStructure structure(Map<MarketPriceLevel.Type, String> levels) {
        List<MarketPriceLevel> priceLevels = new ArrayList<>();
        long id = 1L;
        for (Map.Entry<MarketPriceLevel.Type, String> entry : levels.entrySet()) {
            MarketPriceLevel level = new MarketPriceLevel();
            level.setId(id++);
            level.setType(entry.getKey());
            level.setPrice(decimal(entry.getValue()));
            priceLevels.add(level);
        }
        MarketStructure structure = new MarketStructure();
        structure.setId(41L);
        structure.setLevels(priceLevels);
        return structure;
    }

    /** Карта уровней, сохраняющая порядок объявления. */
    static Map<MarketPriceLevel.Type, String> levels(MarketPriceLevel.Type type, String price) {
        Map<MarketPriceLevel.Type, String> levels = new LinkedHashMap<>();
        levels.put(type, price);
        return levels;
    }

    /** Структура по авторскому имени операнда базовой сборки. */
    static Map<String, MarketStructure> structures(Map<MarketPriceLevel.Type, String> levels) {
        return Map.of(STRUCTURE_KEY, structure(levels));
    }

    /** Готовое значение волатильности по авторскому имени операнда. */
    static Map<String, IndicatorValue> atr(String value) {
        AtrValue atrValue = new AtrValue();
        atrValue.setId(51L);
        atrValue.setAtr(decimal(value));
        return Map.of(INDICATOR_KEY, atrValue);
    }

    // --- состояние сделки ---------------------------------------------------

    /** Входная заявка с плановой ценой и, при надобности, средней ценой налива. */
    static Order entryOrder(String plannedPrice, String averagePrice) {
        Order order = new Order();
        order.setId(9L);
        order.setPrice(decimal(plannedPrice));
        order.setAveragePrice(decimal(averagePrice));
        return order;
    }

    /**
     * Эпизод позиции: живой риск вытекает из статуса и наблюдённого
     * размера, а не подменяется предикатом.
     */
    static Position position(Position.Status status, String observedSize, String averageEntryPrice) {
        Position position = new Position();
        position.setId(5L);
        position.setStatus(status);
        position.setExternalSize(decimal(observedSize));
        position.setExternalAverageEntryPrice(decimal(averageEntryPrice));
        return position;
    }

    /** Живой эпизод с наблюдённым размером {@code 2} и названной средней. */
    static Position liveEpisode(String averageEntryPrice) {
        return position(Position.Status.ACTIVE, "2", averageEntryPrice);
    }

    /** Транш, чья экспозиция вытекает из налива его входной ноги. */
    static DealTranche tranche(String exposure) {
        DealTranche tranche = new DealTranche();
        tranche.setId(7L);
        tranche.setEntryFilled(decimal(exposure));
        return tranche;
    }

    // --- объявление стратегии -----------------------------------------------

    /** Размещение базовой сборки: {@code {рыночная, последняя, ниже, 1}}. */
    static StrategyPricePlacement marketPlacement() {
        return placement(StrategyPriceBaseType.MARKET_PRICE, StrategyPriceSource.LAST_PRICE,
                StrategyPriceOffsetSide.BELOW, "1");
    }

    /** Размещение с названными базой, источником, стороной и процентами. */
    static StrategyPricePlacement placement(StrategyPriceBaseType baseType, StrategyPriceSource source,
                                            StrategyPriceOffsetSide side, String percents) {
        StrategyPricePlacement placement = new StrategyPricePlacement();
        placement.setBaseType(baseType);
        placement.setPriceSource(source);
        placement.setOffsetSide(side);
        placement.setPercents(decimal(percents));
        placement.setStructureKey(STRUCTURE_KEY);
        return placement;
    }

    /** Обычная заявка входа с названным размещением; {@code null} — вход по рынку. */
    static StrategyOrderAction entryAction(StrategyPricePlacement placement) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(1L);
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY);
        action.setPlacement(placement);
        action.setAllocationPercents(new BigDecimal("100"));
        return action;
    }

    /** Reduce-only обычная заявка с объявленной долей закрытия. */
    static StrategyOrderAction reduceOnlyAction(String closeFractionPercents) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(1L);
        action.setKey("exit");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY);
        action.setPositionReducingOnly(Boolean.TRUE);
        action.setAllocationPercents(decimal(closeFractionPercents));
        return action;
    }

    /** Встроенная защита входа со способом «доля от цены входа». */
    static StrategyAttachedProtectionSettings attachedStop(String distancePercents) {
        StrategyAttachedProtectionSettings attached = new StrategyAttachedProtectionSettings();
        attached.setAttachedType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        attached.setStopLossSettings(stopSettings(StopLossCalculationType.ENTRY_PRICE_PERCENT, distancePercents));
        return attached;
    }

    /** Настройки расчёта уровня остановки убытка. */
    static StopLossSettings stopSettings(StopLossCalculationType calculationType, String distancePercents) {
        StopLossSettings settings = new StopLossSettings();
        settings.setCalculationType(calculationType);
        settings.setDistancePercents(decimal(distancePercents));
        settings.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        settings.setIndicatorKey(INDICATOR_KEY);
        settings.setStructureKey(STRUCTURE_KEY);
        return settings;
    }

    /** Условная заявка названного типа условия. */
    static StrategyAlgoOrderAction algoAction(Long id, AlgoOrder.ConditionType conditionType) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setKey("algo-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(conditionType);
        action.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        return action;
    }

    /** Условная заявка `STOP_LOSS` со способом «доля от цены входа» и названной долей. */
    static StrategyAlgoOrderAction stopAction(String distancePercents) {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.ENTRY_PRICE_PERCENT, distancePercents));
        return action;
    }

    /**
     * Деталь стратегии с одним шагом, объявившим названные действия.
     *
     * @param riskPerActionPercent поактный потолок риска; {@code null} — не объявлен
     * @param actions              действия шага в порядке объявления
     * @return закреплённая деталь стратегии
     */
    static StrategyDetail detail(String riskPerActionPercent, List<StrategyAction> actions) {
        StrategyStep step = new StrategyStep();
        step.setId(11L);
        step.setStepType(StrategyStepType.MAIN_PROTECTION);
        step.setActions(actions);

        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(21L);
        declaration.setKey("t1");
        declaration.setStepsByStatus(Map.of(DealTranche.Status.MANAGING, List.of(step)));

        StrategyDetail detail = new StrategyDetail();
        detail.setId(31L);
        detail.setRiskPerActionPercent(decimal(riskPerActionPercent));
        detail.setTranches(List.of(declaration));
        return detail;
    }

    // --- контекст -----------------------------------------------------------

    /**
     * Базовая сборка контекста: направление {@code LONG}, правила
     * инструмента, снапшот цен и входная заявка с плановой ценой
     * {@code 3000}; живого эпизода нет.
     */
    static CalculationContext.CalculationContextBuilder base(StrategyAction action) {
        return CalculationContext.builder()
                .action(action)
                .strategyDirection(StrategyTradeDirection.LONG)
                .instrumentExternalRules(rules())
                .marketPriceData(prices())
                .entryOrder(entryOrder(LAST_PRICE, null));
    }

    /** Число из строки; {@code null} — значения нет. */
    static BigDecimal decimal(String value) {
        return nonNull(value) ? new BigDecimal(value) : null;
    }
}
