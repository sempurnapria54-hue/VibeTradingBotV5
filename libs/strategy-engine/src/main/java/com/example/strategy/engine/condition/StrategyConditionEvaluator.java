package com.example.strategy.engine.condition;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
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
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.util.DomainMath;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Проверяет применимость StrategyCondition на готовых рыночных данных и
 * фактах сделки (переиспользование грамматики условий, не второй движок
 * расчёта). Сам индикаторы/структуру по свечам не считает — читает готовые
 * результаты из контекста.
 *
 * <p><b>Живёт в общем артефакте, а не у потребителя:</b> грамматику
 * условий читают и живая торговля, и бэктест, и классификация фазы, и
 * вторая её копия разошлась бы с первой при первом расширении каталога
 * (.claude/rules/policy-home.md).
 *
 * <p><b>Whitelist контекста выражен ПУСТОТОЙ ОПЕРАНДОВ, а не перечнем
 * типов.</b> Классификация фазы рынка собирает контекст без эпизода,
 * транша и фазы входа — правила, читающие их, оказываются на пустом
 * операнде и консервативно ложны. Отдельный список разрешённых типов был
 * бы вторым носителем того же различения и разошёлся бы с ним при
 * добавлении первого же типа.
 *
 * <p><b>Тип правила, чей операнд собирает не этот контекст, называет себя
 * записью журнала.</b> Так себя ведёт {@code NO_ACTIVE_DEAL}: его операнд —
 * реестр сделок пары, и приносит его сканер входа, а не проход сделки.
 * Молчаливой ложью такой тип не оборачивается.
 *
 * <p>Скалярная проекция многокомпонентных индикаторов (MACD→линия,
 * Stochastic→%K, Bollinger→%B) — деталь реализации.
 * См. docs/components/StrategyConditionEvaluator.md,
 * docs/spec/deal-condition.json, docs/spec/market-phase-condition.json.
 */
@Slf4j
@Service
public class StrategyConditionEvaluator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
            case MARKET_PHASE_IS -> evaluateMarketPhaseIs(rule, context);
            case TREND_CHANGED -> evaluateTrendChanged(context);
            case POSITION_OPENED -> positionRiskBearing(context);
            case NO_OPEN_POSITION -> isFalse(positionRiskBearing(context));
            case ENTRY_ORDER_FINALIZED -> evaluateEntryOrderFinalized(context);
            case ATTACHED_STOP_LOSS_EXISTS -> evaluateAttachedProtectionExists(context);
            case MAIN_PROTECTION_EXISTS -> evaluateMainProtectionExists(context);
            case PROFIT_PERCENTS_REACHED -> evaluateProfitReached(rule, context);
            case LOSS_PERCENTS_REACHED -> evaluateLossReached(rule, context);
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

    /**
     * Фаза прохода совпала с объявленной шагом
     * (docs/spec/market-phase-condition.json, {@code marketPhaseIs}).
     *
     * <p>Неустановленная фаза даёт ЛОЖЬ, а не совпадение: условие входа
     * обязано опираться на наблюдение, и разрешающее умолчание открыло бы
     * сделку по неизвестной фазе.
     */
    private boolean evaluateMarketPhaseIs(StrategyConditionRule rule, ConditionEvaluationContext context) {
        String declared = constantEnumValue(rule);
        return isTrue(phaseKnown(context.getMarketPhase()))
                && nonNull(declared)
                && Objects.equals(context.getMarketPhase().name(), declared);
    }

    /**
     * Фаза прохода отличается от фазы ВХОДА сделки
     * (docs/spec/market-phase-condition.json, {@code trendChanged}).
     *
     * <p>Обе неустановленности дают ЛОЖЬ: выход по неизвестной фазе был бы
     * выходом по несостоявшемуся наблюдению, а живой риск в этот момент
     * держит стоп, а не это условие.
     */
    private boolean evaluateTrendChanged(ConditionEvaluationContext context) {
        return isTrue(phaseKnown(context.getMarketPhase()))
                && isTrue(phaseKnown(context.getEntryMarketPhase()))
                && isFalse(Objects.equals(context.getMarketPhase(), context.getEntryMarketPhase()));
    }

    /** Фаза установлена: {@code UNKNOWN} знанием не является. */
    private Boolean phaseKnown(MarketPhase.Type phase) {
        return nonNull(phase) && isFalse(MarketPhase.Type.UNKNOWN.equals(phase));
    }

    /**
     * У сделки есть живой эпизод, несущий риск
     * (docs/spec/deal-condition.json, {@code positionOpened}). Строка
     * эпизода без положительного размера открытой позицией не считается —
     * она факт наблюдения, а не экспозиция.
     */
    private boolean positionRiskBearing(ConditionEvaluationContext context) {
        Position position = context.getActivePosition();
        return nonNull(position) && isTrue(position.hasLiveRisk());
    }

    /** Входная нога транша налита целиком ({@code entryOrderFinalized}). */
    private boolean evaluateEntryOrderFinalized(ConditionEvaluationContext context) {
        Order entryOrder = isNull(context.getTranche()) ? null : context.getTranche().entryOrder();
        return nonNull(entryOrder) && isTrue(entryOrder.isFilled());
    }

    /** У транша живёт ВСТРОЕННАЯ защита ({@code attachedStopLossExists}). */
    private boolean evaluateAttachedProtectionExists(ConditionEvaluationContext context) {
        DealTranche tranche = context.getTranche();
        return nonNull(tranche) && isNotEmpty(tranche.liveAttachedProtections());
    }

    /** У транша живёт ОТДЕЛЬНАЯ защита с действующим уровнем ({@code mainProtectionExists}). */
    private boolean evaluateMainProtectionExists(ConditionEvaluationContext context) {
        DealTranche tranche = context.getTranche();
        return nonNull(tranche) && isTrue(tranche.hasStandaloneProtection());
    }

    /** Ход достиг объявленного порога прибыли ({@code profitPercentsReached}). */
    private boolean evaluateProfitReached(StrategyConditionRule rule, ConditionEvaluationContext context) {
        BigDecimal move = signedMovePercents(context);
        BigDecimal declared = declaredPercents(rule);
        return nonNull(move) && nonNull(declared) && move.compareTo(declared) >= 0;
    }

    /**
     * Ход достиг объявленного порога убытка ({@code lossPercentsReached}).
     *
     * <p>Порог объявляется ПОЛОЖИТЕЛЬНОЙ величиной убытка, поэтому
     * сравнение идёт с его отрицанием: иначе автор обязан был бы писать
     * знак, а забытый знак дал бы срабатывание в прибыли.
     */
    private boolean evaluateLossReached(StrategyConditionRule rule, ConditionEvaluationContext context) {
        BigDecimal move = signedMovePercents(context);
        BigDecimal declared = declaredPercents(rule);
        return nonNull(move) && nonNull(declared) && move.compareTo(declared.negate()) <= 0;
    }

    /**
     * Ход цены от ЦЕНЫ ВХОДА в процентах, со знаком направления
     * (docs/spec/deal-condition.json, {@code signedMovePercents}); пусто —
     * операнды недоступны.
     *
     * <p><b>Плечо в единицу не входит:</b> порог объявлен процентами
     * прибыли от цены входа (docs/models/domain/aggregate/Strategy.md), и
     * один и тот же порог обязан означать одно и то же на разных счетах.
     */
    private BigDecimal signedMovePercents(ConditionEvaluationContext context) {
        BigDecimal anchor = entryAnchor(context);
        BigDecimal price = context.getPrice();
        if (isNull(anchor) || isNull(price) || anchor.signum() == 0) {
            return null;
        }
        BigDecimal move = StrategyTradeDirection.LONG.equals(context.getDirection())
                ? price.subtract(anchor)
                : anchor.subtract(price);
        return move.divide(anchor, DomainMath.CONTEXT).multiply(HUNDRED);
    }

    /** Наблюдённая средняя цена входа живого эпизода; плановой не подменяется. */
    private BigDecimal entryAnchor(ConditionEvaluationContext context) {
        Position position = context.getActivePosition();
        return isNull(position) ? null : position.getExternalAverageEntryPrice();
    }

    /** Порог, объявленный правилом константным операндом любой стороны. */
    private BigDecimal declaredPercents(StrategyConditionRule rule) {
        BigDecimal fromRight = parseConstant(rule.getRightOperand());
        return nonNull(fromRight) ? fromRight : parseConstant(rule.getLeftOperand());
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
        if (isNull(operand) || isNull(operand.getValueType())) {
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
