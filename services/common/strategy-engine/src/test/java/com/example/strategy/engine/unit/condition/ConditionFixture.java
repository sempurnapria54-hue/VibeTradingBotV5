package com.example.strategy.engine.unit.condition;

import static java.util.Objects.isNull;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.BollingerBandsValue;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.MacdValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import com.example.tradingbot.domain.model.trade.indicator.RsiValue;
import com.example.tradingbot.domain.model.trade.indicator.StochasticValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Базовые сборки групп документа
 * `.claude/tests/cases/strategy-engine-condition.md`.
 *
 * <p><b>Субстрата у предмета нет вовсе:</b> ни контейнеров, ни контекста
 * каркаса, ни стабов — интерпретатор конструируется {@code new}, а
 * состояние собирается <b>настоящими полями доменных моделей</b>, а не
 * подменёнными предикатами (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»). Живой риск эпизода поэтому вытекает из его статуса и
 * наблюдённого размера, налив входной ноги — из статуса и причины
 * закрытия, встроенная защита — из состава заявок транша, подтверждённый
 * пробой — из непустоты события структуры.
 *
 * <p><b>Единица кейса здесь — ПАРА строк, а не строка:</b> у булева выхода
 * ложь одна на все причины, и кейс, называющий только ложь, зелен у
 * интерпретатора, который ложен всегда. Поэтому у ложной строки в группе
 * стои́т соседняя, отличающаяся ровно одним операндом и дающая истину
 * (§«Новая ось формы: выход булев, и ложь у него одна на все причины»
 * того же документа).
 */
final class ConditionFixture {

    /** Ключ быстрой средней базовых сборок сравнения и пересечения. */
    static final String FAST_KEY = "ema_fast";

    /** Ключ медленной средней базовых сборок сравнения и пересечения. */
    static final String SLOW_KEY = "ema_slow";

    /** Ключ многокомпонентного индикатора базовой сборки U3. */
    static final String MACD_KEY = "macd";

    /** Ключ стохастика базовой сборки U3. */
    static final String STOCH_KEY = "stoch";

    /** Ключ полос Боллинджера базовой сборки U3. */
    static final String BOLLINGER_KEY = "bb";

    /** Ключ накопительного объёма базовой сборки U6. */
    static final String OBV_KEY = "obv";

    /** Ключ структуры базовой сборки U7. */
    static final String STRUCTURE_KEY = "range_h1";

    private ConditionFixture() {
    }

    // --- контекст ----------------------------------------------------------

    /**
     * Пустой контекст: раскладки собраны и пусты, цены нет, фазы нет,
     * фактов сделки нет ни одного. Это и есть контекст классификации фазы
     * — whitelist, выраженный пустотой операндов.
     */
    static ConditionEvaluationContext.ConditionEvaluationContextBuilder base() {
        return ConditionEvaluationContext.builder()
                .latestIndicators(Map.of())
                .previousIndicators(Map.of())
                .structures(Map.of());
    }

    /** Раскладка последних значений по ключам — порядок вставки сохраняется. */
    static Map<String, IndicatorValue> indicators(Object... keysAndValues) {
        Map<String, IndicatorValue> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], (IndicatorValue) keysAndValues[index + 1]);
        }
        return map;
    }

    // --- значения индикаторов ---------------------------------------------

    static EmaValue ema(String value) {
        EmaValue indicator = new EmaValue();
        indicator.setEma(new BigDecimal(value));
        return indicator;
    }

    static RsiValue rsi(String value) {
        RsiValue indicator = new RsiValue();
        indicator.setRsi(new BigDecimal(value));
        return indicator;
    }

    static AtrValue atr(String value) {
        AtrValue indicator = new AtrValue();
        indicator.setAtr(new BigDecimal(value));
        return indicator;
    }

    static ObvValue obv(String value) {
        ObvValue indicator = new ObvValue();
        indicator.setObv(new BigDecimal(value));
        return indicator;
    }

    static EfficiencyRatioValue efficiencyRatio(String value) {
        EfficiencyRatioValue indicator = new EfficiencyRatioValue();
        indicator.setEfficiencyRatio(new BigDecimal(value));
        return indicator;
    }

    /** MACD базовой сборки U3: линия {@code 2}, сигнальная {@code 1}, гистограмма {@code 1}. */
    static MacdValue macd(String line, String signal, String histogram) {
        MacdValue indicator = new MacdValue();
        indicator.setMacdLine(new BigDecimal(line));
        indicator.setSignalLine(new BigDecimal(signal));
        indicator.setHistogram(new BigDecimal(histogram));
        return indicator;
    }

    /** Стохастик базовой сборки U3: {@code %K = 80}, {@code %D = 70}. */
    static StochasticValue stochastic(String k, String d) {
        StochasticValue indicator = new StochasticValue();
        indicator.setK(new BigDecimal(k));
        indicator.setD(new BigDecimal(d));
        return indicator;
    }

    /** Полосы Боллинджера базовой сборки U3: {@code %B = 0.8}, ширина {@code 4}. */
    static BollingerBandsValue bollinger(String percentB, String bandwidth) {
        BollingerBandsValue indicator = new BollingerBandsValue();
        indicator.setPercentB(new BigDecimal(percentB));
        indicator.setBandwidth(new BigDecimal(bandwidth));
        indicator.setUpperBand(new BigDecimal("110"));
        indicator.setMiddleBand(new BigDecimal("100"));
        indicator.setLowerBand(new BigDecimal("90"));
        return indicator;
    }

    // --- структура рынка ---------------------------------------------------

    /** Структура объявленного типа без события пробоя. */
    static MarketStructure structure(MarketStructure.Type type) {
        MarketStructure structure = new MarketStructure();
        structure.setType(type);
        return structure;
    }

    /** Та же структура с ПРЕДВЫЧИСЛЕННЫМ событием пробоя — интерпретатор его не детектирует. */
    static MarketStructure structureWithBreakout(MarketStructure.Type type) {
        MarketBreakoutEvent breakout = new MarketBreakoutEvent();
        breakout.setBrokenLevelType(MarketPriceLevel.Type.RANGE_HIGH);
        breakout.setDirection(MarketBreakoutEvent.Direction.UP);
        breakout.setLevelPrice(new BigDecimal("100"));
        MarketStructure structure = structure(type);
        structure.setBreakoutEvent(breakout);
        return structure;
    }

    // --- факты сделки ------------------------------------------------------

    /** Живой эпизод: {@code ACTIVE}, размер {@code 1}, объявленная средняя цена входа. */
    static Position livePosition(String averageEntryPrice) {
        Position position = new Position();
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(BigDecimal.ONE);
        position.setExternalAverageEntryPrice(isNull(averageEntryPrice) ? null : new BigDecimal(averageEntryPrice));
        return position;
    }

    /** Транш без заявок и без отдельных защит. */
    static DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(21L);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of());
        return tranche;
    }

    /** Заявка транша: признак входа берётся у намерения, а не у типа. */
    static Order order(Long id, Order.Status status, Order.CloseReason closeReason, Boolean reducingOnly) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setCloseReason(closeReason);
        order.setPositionReducingOnly(reducingOnly);
        return order;
    }

    /** Транш с объявленным составом заявок. */
    static DealTranche trancheWithOrders(Order... orders) {
        DealTranche tranche = tranche();
        tranche.setOrders(Arrays.asList(orders));
        return tranche;
    }

    /** Встроенная защита объявленного статуса на налитой входной ноге. */
    static DealTranche trancheWithAttachedProtection(AttachedAlgoOrder.Status status) {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setStatus(status);
        Order entry = order(800L, Order.Status.COMPLETED, Order.CloseReason.FILLED, false);
        entry.setAttachedAlgoOrders(List.of(protection));
        return trancheWithOrders(entry);
    }

    /** Отдельная живая защита объявленного типа условия. */
    static DealTranche trancheWithStandaloneProtection(AlgoOrder.ConditionType conditionType) {
        AlgoOrder protection = new AlgoOrder();
        protection.setId(900L);
        protection.setStatus(AlgoOrder.Status.ACTIVE);
        protection.setConditionType(conditionType);
        DealTranche tranche = tranche();
        tranche.setAlgoOrders(List.of(protection));
        return tranche;
    }

    // --- условие, правило, операнды ---------------------------------------

    /** Условие из перечисленных правил; перечень изменяем — элемент его бывает пустым (`U1.10`). */
    static StrategyCondition condition(StrategyConditionRule... rules) {
        StrategyCondition condition = new StrategyCondition();
        condition.setRules(new ArrayList<>(Arrays.asList(rules)));
        return condition;
    }

    /** Правило объявленного типа без оператора и операндов. */
    static StrategyConditionRule rule(StrategyConditionRuleType type) {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(type);
        return rule;
    }

    /** Правило с оператором и двумя операндами. */
    static StrategyConditionRule rule(StrategyConditionRuleType type, StrategyConditionOperator operator,
                                      StrategyConditionOperand left, StrategyConditionOperand right) {
        StrategyConditionRule rule = rule(type);
        rule.setOperator(operator);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return rule;
    }

    /** Индикаторный операнд: ключ — авторское имя, компонент адресный. */
    static StrategyConditionOperand indicator(String key, IndicatorComponent component) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.INDICATOR);
        operand.setIndicatorKey(key);
        operand.setIndicatorComponent(component);
        return operand;
    }

    /** Ценовой операнд с объявленным источником. */
    static StrategyConditionOperand price(StrategyPriceSource source) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.PRICE);
        operand.setPriceSource(source);
        return operand;
    }

    /** Структурный операнд по ключу настройки. */
    static StrategyConditionOperand structureOperand(String key) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.MARKET_STRUCTURE);
        operand.setStructureKey(key);
        return operand;
    }

    /** Константный операнд объявленного типа значения. */
    static StrategyConditionOperand constant(String value, ConstantValueType valueType) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.CONSTANT);
        operand.setValueType(valueType);
        operand.setValue(value);
        return operand;
    }

    /** Числовая константа — самая частая правая сторона сравнения. */
    static StrategyConditionOperand number(String value) {
        return constant(value, ConstantValueType.NUMBER);
    }

    /** Константа-перечисление: тип значения не читается ни равенством фазы, ни равенством структуры. */
    static StrategyConditionOperand enumConstant(String value) {
        return constant(value, ConstantValueType.ENUM);
    }

    /** Операнд объявленного типа источника — для типов, которые интерпретатор не резолвит. */
    static StrategyConditionOperand operandOfSource(StrategyConditionSourceType sourceType) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(sourceType);
        return operand;
    }
}
