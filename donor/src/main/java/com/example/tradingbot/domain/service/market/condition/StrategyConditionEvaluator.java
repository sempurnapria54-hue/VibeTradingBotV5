package com.example.tradingbot.domain.service.market.condition;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.BollingerBandsValue;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.MacdValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import com.example.tradingbot.domain.model.trade.indicator.RsiValue;
import com.example.tradingbot.domain.model.trade.indicator.StochasticValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Проверяет применимость StrategyCondition на готовых рыночных данных
 * (переиспользование грамматики условий, не второй движок расчёта).
 * Сам индикаторы/структуру по свечам не считает — читает готовые
 * результаты из контекста. На шаге 3 поддержан whitelist контекста
 * классификации фазы (сравнивающие/структурно-событийные ruleType,
 * операнды INDICATOR/MARKET_STRUCTURE/PRICE/CONSTANT/TIME); deal-контекст
 * (Position/Order-факты) приходит с кластером сделки — такие ruleType
 * здесь консервативно ложны. Скалярная проекция многокомпонентных
 * индикаторов (MACD→линия, Stochastic→%K, Bollinger→%B) — деталь
 * реализации (CODE). См. docs/components/StrategyConditionEvaluator.md.
 */
@Slf4j
@Service
public class StrategyConditionEvaluator {

    /** Условие истинно, когда истинны все его правила (пустое условие — истинно). */
    public Boolean evaluate(StrategyCondition condition, ConditionEvaluationContext context) {
        if (isNull(condition) || isEmpty(condition.getRules())) {
            return true;
        }
        return condition.getRules().stream().allMatch(rule -> evaluateRule(rule, context));
    }

    private boolean evaluateRule(StrategyConditionRule rule, ConditionEvaluationContext context) {
        return switch (rule.getRuleType()) {
            case INDICATOR_COMPARE, PRICE_COMPARE -> evaluateCompare(rule, context);
            case CROSSOVER -> evaluateCrossover(rule, context);
            case MARKET_STRUCTURE_IS -> evaluateMarketStructureIs(rule, context);
            case RANGE_BREAKOUT_CONFIRMED -> evaluateRangeBreakout(rule, context);
            case CANDLE_CLOSED -> true;
            case VOLUME_FILTER_PASSED -> evaluateVolumeFilter(rule, context);
            default -> {
                log.warn("Rule type {} is not evaluable in this context", rule.getRuleType());
                yield false;
            }
        };
    }

    private boolean evaluateCompare(StrategyConditionRule rule, ConditionEvaluationContext context) {
        BigDecimal left = resolveScalar(rule.getLeftOperand(), context, true);
        BigDecimal right = resolveScalar(rule.getRightOperand(), context, true);
        if (isNull(left) || isNull(right)) {
            return false;
        }
        return applyRelational(rule.getOperator(), left, right);
    }

    private boolean evaluateCrossover(StrategyConditionRule rule, ConditionEvaluationContext context) {
        BigDecimal currentLeft = resolveScalar(rule.getLeftOperand(), context, true);
        BigDecimal currentRight = resolveScalar(rule.getRightOperand(), context, true);
        BigDecimal previousLeft = resolveScalar(rule.getLeftOperand(), context, false);
        BigDecimal previousRight = resolveScalar(rule.getRightOperand(), context, false);
        if (isNull(currentLeft) || isNull(currentRight) || isNull(previousLeft) || isNull(previousRight)) {
            return false;
        }
        return switch (rule.getOperator()) {
            case CROSSED_ABOVE -> previousLeft.compareTo(previousRight) <= 0 && currentLeft.compareTo(currentRight) > 0;
            case CROSSED_BELOW -> previousLeft.compareTo(previousRight) >= 0 && currentLeft.compareTo(currentRight) < 0;
            default -> false;
        };
    }

    private boolean evaluateMarketStructureIs(StrategyConditionRule rule, ConditionEvaluationContext context) {
        MarketStructure structure = structureOf(rule, context);
        String expectedType = constantEnumValue(rule);
        if (isNull(structure) || isNull(structure.getType()) || isNull(expectedType)) {
            return false;
        }
        boolean equal = Objects.equals(structure.getType().name(), expectedType);
        return switch (rule.getOperator()) {
            case EQ -> equal;
            case NE -> isFalse(equal);
            default -> false;
        };
    }

    private boolean evaluateRangeBreakout(StrategyConditionRule rule, ConditionEvaluationContext context) {
        MarketStructure structure = structureOf(rule, context);
        return nonNull(structure) && isTrue(structure.hasConfirmedBreakout());
    }

    /**
     * Минимальная семантика объёмного фильтра на шаге 3: индикаторный
     * операнд (OBV) подтверждает направление, если значение растёт
     * (latest &gt; previous). Полная семантика — инкремент авторинга.
     */
    private boolean evaluateVolumeFilter(StrategyConditionRule rule, ConditionEvaluationContext context) {
        BigDecimal current = resolveScalar(rule.getLeftOperand(), context, true);
        BigDecimal previous = resolveScalar(rule.getLeftOperand(), context, false);
        return nonNull(current) && nonNull(previous) && current.compareTo(previous) > 0;
    }

    private BigDecimal resolveScalar(StrategyConditionOperand operand, ConditionEvaluationContext context,
                                     boolean current) {
        if (isNull(operand) || isNull(operand.getSourceType())) {
            return null;
        }
        return switch (operand.getSourceType()) {
            case INDICATOR -> scalarOf(indicatorOf(operand, context, current), operand.getIndicatorComponent());
            case PRICE -> current ? context.getPrice() : null;
            case CONSTANT -> parseConstant(operand);
            default -> null;
        };
    }

    private IndicatorValue indicatorOf(StrategyConditionOperand operand, ConditionEvaluationContext context,
                                       boolean current) {
        if (isNull(operand.getIndicatorKey())) {
            return null;
        }
        return current
                ? context.getLatestIndicators().get(operand.getIndicatorKey())
                : context.getPreviousIndicators().get(operand.getIndicatorKey());
    }

    /** Скаляр индикатора по адресуемому компоненту (для многокомпонентных); null-компонент → дефолт типа. */
    private BigDecimal scalarOf(IndicatorValue value, IndicatorComponent component) {
        if (isNull(value)) {
            return null;
        }
        return switch (value.getType()) {
            case EMA -> ((EmaValue) value).getEma();
            case RSI -> ((RsiValue) value).getRsi();
            case ATR -> ((AtrValue) value).getAtr();
            case OBV -> ((ObvValue) value).getObv();
            case EFFICIENCY_RATIO -> ((EfficiencyRatioValue) value).getEfficiencyRatio();
            case MACD -> macdComponent((MacdValue) value, component);
            case STOCHASTIC -> stochasticComponent((StochasticValue) value, component);
            case BOLLINGER_BANDS -> bollingerComponent((BollingerBandsValue) value, component);
        };
    }

    private BigDecimal macdComponent(MacdValue value, IndicatorComponent component) {
        if (isNull(component)) {
            return value.getMacdLine();
        }
        return switch (component) {
            case SIGNAL_LINE -> value.getSignalLine();
            case HISTOGRAM -> value.getHistogram();
            default -> value.getMacdLine();
        };
    }

    private BigDecimal stochasticComponent(StochasticValue value, IndicatorComponent component) {
        return Objects.equals(component, IndicatorComponent.STOCH_D) ? value.getD() : value.getK();
    }

    private BigDecimal bollingerComponent(BollingerBandsValue value, IndicatorComponent component) {
        if (isNull(component)) {
            return value.getPercentB();
        }
        return switch (component) {
            case UPPER_BAND -> value.getUpperBand();
            case MIDDLE_BAND -> value.getMiddleBand();
            case LOWER_BAND -> value.getLowerBand();
            case BANDWIDTH -> value.getBandwidth();
            default -> value.getPercentB();
        };
    }

    private BigDecimal parseConstant(StrategyConditionOperand operand) {
        if (isNull(operand.getValueType())) {
            return null;
        }
        return switch (operand.getValueType()) {
            case NUMBER, PERCENT -> isNull(operand.getValue()) ? null : new BigDecimal(operand.getValue());
            default -> null;
        };
    }

    private boolean applyRelational(StrategyConditionOperator operator, BigDecimal left, BigDecimal right) {
        if (isNull(operator)) {
            return false;
        }
        int comparison = left.compareTo(right);
        return switch (operator) {
            case EQ -> comparison == 0;
            case NE -> comparison != 0;
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            default -> false;
        };
    }

    private MarketStructure structureOf(StrategyConditionRule rule, ConditionEvaluationContext context) {
        MarketStructure fromLeft = structureOfOperand(rule.getLeftOperand(), context);
        return nonNull(fromLeft) ? fromLeft : structureOfOperand(rule.getRightOperand(), context);
    }

    private MarketStructure structureOfOperand(StrategyConditionOperand operand, ConditionEvaluationContext context) {
        if (isNull(operand)
                || isFalse(Objects.equals(operand.getSourceType(), StrategyConditionSourceType.MARKET_STRUCTURE))
                || isNull(operand.getStructureKey())) {
            return null;
        }
        return context.getStructures().get(operand.getStructureKey());
    }

    private String constantEnumValue(StrategyConditionRule rule) {
        String fromLeft = constantEnumValue(rule.getLeftOperand());
        return nonNull(fromLeft) ? fromLeft : constantEnumValue(rule.getRightOperand());
    }

    private String constantEnumValue(StrategyConditionOperand operand) {
        if (isNull(operand) || isFalse(Objects.equals(operand.getSourceType(), StrategyConditionSourceType.CONSTANT))) {
            return null;
        }
        return operand.getValue();
    }

}
