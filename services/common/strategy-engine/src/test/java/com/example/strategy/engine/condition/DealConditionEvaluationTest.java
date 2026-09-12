package com.example.strategy.engine.condition;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
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
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Правила условий, читающие факты СДЕЛКИ.
 *
 * <p><b>Предмет — направление ошибки на пустом операнде.</b> Правило,
 * которому контекст фактов не собрал, обязано быть ложным: разрешающее
 * умолчание открыло бы сделку по ненаблюдённой фазе, а порог прибыли,
 * прочитанный нулём, снял бы защиту по прибыли, которой никто не видел
 * (docs/spec/deal-condition.json,
 * docs/spec/market-phase-condition.json).
 *
 * <p><b>И то же с другой стороны:</b> контекст классификации фазы этих
 * фактов не собирает вовсе, и правила на нём ложны — это и есть whitelist
 * контекста, выраженный пустотой операндов, а не вторым перечнем типов.
 */
class DealConditionEvaluationTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Фаза прохода совпала с объявленной — правило истинно; другая — ложно. */
    @Test
    void marketPhaseIsComparesThePassPhaseWithTheDeclaredOne() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_PHASE_IS,
                null, constant("BULL_TREND", null)));

        assertThat(evaluator.evaluate(condition, phase(MarketPhase.Type.BULL_TREND, null))).isTrue();
        assertThat(evaluator.evaluate(condition, phase(MarketPhase.Type.RANGE, null))).isFalse();
    }

    /**
     * Неустановленная фаза даёт ЛОЖЬ, а не совпадение.
     *
     * <p>Объявленная {@code UNKNOWN} и наблюдённая {@code UNKNOWN} — это не
     * встреча двух знаний, а встреча двух незнаний; без конъюнкта
     * установленности они совпали бы и открыли вход.
     */
    @Test
    void unknownPhaseNeverMatches() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_PHASE_IS,
                null, constant("UNKNOWN", null)));

        assertThat(evaluator.evaluate(condition, phase(MarketPhase.Type.UNKNOWN, null))).isFalse();
        assertThat(evaluator.evaluate(condition, phase(null, null))).isFalse();
    }

    /**
     * Смена тренда — уход от фазы ВХОДА этой сделки.
     *
     * <p>Своей истории у фазы нет: темпоральный операнд взят у сделки.
     * Обе неустановленности дают ложь.
     */
    @Test
    void trendChangedComparesAgainstTheEntryPhase() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.TREND_CHANGED, null, null));

        assertThat(evaluator.evaluate(condition, phase(MarketPhase.Type.RANGE, MarketPhase.Type.BULL_TREND)))
                .isTrue();
        assertThat(evaluator.evaluate(condition, phase(MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND)))
                .isFalse();
        assertThat(evaluator.evaluate(condition, phase(MarketPhase.Type.RANGE, null))).isFalse();
    }

    /** Живой эпизод есть — позиция открыта; строка без размера открытой не считается. */
    @Test
    void positionPredicatesReadTheLiveEpisode() {
        StrategyCondition opened = condition(rule(StrategyConditionRuleType.POSITION_OPENED, null, null));
        StrategyCondition absent = condition(rule(StrategyConditionRuleType.NO_OPEN_POSITION, null, null));

        assertThat(evaluator.evaluate(opened, dealContext(livePosition("100"), tranche()))).isTrue();
        assertThat(evaluator.evaluate(absent, dealContext(livePosition("100"), tranche()))).isFalse();

        Position observedButEmpty = livePosition("100");
        observedButEmpty.setExternalSize(BigDecimal.ZERO);

        assertThat(evaluator.evaluate(opened, dealContext(observedButEmpty, tranche()))).isFalse();
        assertThat(evaluator.evaluate(absent, dealContext(observedButEmpty, tranche()))).isTrue();
    }

    /**
     * Вход финализирован — нога налита ЦЕЛИКОМ.
     *
     * <p>Частичный налив финализацией не является: экспозиция ещё меняется,
     * и шаг, объявленный от завершённого входа, сработал бы на
     * неокончательном размере.
     */
    @Test
    void entryOrderFinalizedRequiresAFullFill() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, null, null));
        DealTranche partial = trancheWithEntry(Order.Status.PARTIALLY_COMPLETED, null);
        DealTranche filled = trancheWithEntry(Order.Status.COMPLETED, Order.CloseReason.FILLED);

        assertThat(evaluator.evaluate(condition, dealContext(livePosition("100"), partial))).isFalse();
        assertThat(evaluator.evaluate(condition, dealContext(livePosition("100"), filled))).isTrue();
    }

    /**
     * Встроенная и основная защита различаются предикатами.
     *
     * <p>Именно на этом различении стои́т сценарий переключения: пока
     * основная не подтверждена, встроенная снята быть не может.
     */
    @Test
    void attachedAndStandaloneProtectionAreDistinguished() {
        StrategyCondition attached = condition(rule(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS, null, null));
        StrategyCondition main = condition(rule(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS, null, null));

        DealTranche withAttached = trancheWithAttachedProtection();
        assertThat(evaluator.evaluate(attached, dealContext(livePosition("100"), withAttached))).isTrue();
        assertThat(evaluator.evaluate(main, dealContext(livePosition("100"), withAttached))).isFalse();

        DealTranche withStandalone = trancheWithStandaloneProtection();
        assertThat(evaluator.evaluate(attached, dealContext(livePosition("100"), withStandalone))).isFalse();
        assertThat(evaluator.evaluate(main, dealContext(livePosition("100"), withStandalone))).isTrue();
    }

    /**
     * Порог прибыли берётся от ЦЕНЫ ВХОДА и знает направление.
     *
     * <p>Тот же ход цены вниз — прибыль у короткой сделки и убыток у
     * длинной; без ветви направления короткая закрывалась бы шагом
     * стоп-порога ровно там, где она зарабатывает.
     */
    @Test
    void profitThresholdKnowsTheDirection() {
        StrategyCondition profit = condition(rule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED,
                null, constant("2", ConstantValueType.PERCENT)));

        assertThat(evaluator.evaluate(profit, move("100", "103", StrategyTradeDirection.LONG))).isTrue();
        assertThat(evaluator.evaluate(profit, move("100", "97", StrategyTradeDirection.LONG))).isFalse();
        assertThat(evaluator.evaluate(profit, move("100", "97", StrategyTradeDirection.SHORT))).isTrue();
    }

    /**
     * Порог убытка объявляется ПОЛОЖИТЕЛЬНОЙ величиной и сравнивается с
     * отрицанием хода: забытый знак иначе дал бы срабатывание в прибыли.
     */
    @Test
    void lossThresholdComparesAgainstTheNegatedMove() {
        StrategyCondition loss = condition(rule(StrategyConditionRuleType.LOSS_PERCENTS_REACHED,
                null, constant("2", ConstantValueType.PERCENT)));

        assertThat(evaluator.evaluate(loss, move("100", "97", StrategyTradeDirection.LONG))).isTrue();
        assertThat(evaluator.evaluate(loss, move("100", "103", StrategyTradeDirection.LONG))).isFalse();
    }

    /** Порог ровно взят — граница включающая. */
    @Test
    void thresholdBoundaryIsInclusive() {
        StrategyCondition profit = condition(rule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED,
                null, constant("2", ConstantValueType.PERCENT)));

        assertThat(evaluator.evaluate(profit, move("100", "102", StrategyTradeDirection.LONG))).isTrue();
    }

    /**
     * Недоступная цена момента и ненаблюдённая цена входа дают ЛОЖЬ, а не
     * срабатывание.
     */
    @Test
    void missingMoveOperandsMakeThresholdsFalse() {
        StrategyCondition profit = condition(rule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED,
                null, constant("2", ConstantValueType.PERCENT)));

        assertThat(evaluator.evaluate(profit, move("100", null, StrategyTradeDirection.LONG))).isFalse();
        assertThat(evaluator.evaluate(profit, move(null, "103", StrategyTradeDirection.LONG))).isFalse();
    }

    /**
     * Контекст классификации фазы фактов сделки не собирает — правила на
     * нём ложны.
     *
     * <p>Это и есть whitelist контекста: он выражен пустотой операндов, а
     * не вторым перечнем разрешённых типов, который разошёлся бы с первым
     * при добавлении первого же типа.
     */
    @Test
    void phaseClassificationContextMakesDealRulesFalse() {
        ConditionEvaluationContext phaseOnly = ConditionEvaluationContext.builder()
                .latestIndicators(Map.of())
                .previousIndicators(Map.of())
                .structures(Map.of())
                .build();

        assertThat(evaluator.evaluate(
                condition(rule(StrategyConditionRuleType.POSITION_OPENED, null, null)), phaseOnly)).isFalse();
        assertThat(evaluator.evaluate(
                condition(rule(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS, null, null)), phaseOnly)).isFalse();
        assertThat(evaluator.evaluate(
                condition(rule(StrategyConditionRuleType.TREND_CHANGED, null, null)), phaseOnly)).isFalse();
    }

    private ConditionEvaluationContext phase(MarketPhase.Type current, MarketPhase.Type entry) {
        return base().marketPhase(current).entryMarketPhase(entry).build();
    }

    private ConditionEvaluationContext dealContext(Position position, DealTranche tranche) {
        return base().activePosition(position).tranche(tranche).build();
    }

    private ConditionEvaluationContext move(String anchor, String price, StrategyTradeDirection direction) {
        Position position = new Position();
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(BigDecimal.ONE);
        position.setExternalAverageEntryPrice(isNull(anchor) ? null : new BigDecimal(anchor));
        return base()
                .activePosition(position)
                .direction(direction)
                .price(isNull(price) ? null : new BigDecimal(price))
                .build();
    }

    private ConditionEvaluationContext.ConditionEvaluationContextBuilder base() {
        return ConditionEvaluationContext.builder()
                .latestIndicators(Map.of())
                .previousIndicators(Map.of())
                .structures(Map.of());
    }

    private Position livePosition(String averageEntryPrice) {
        Position position = new Position();
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(BigDecimal.ONE);
        position.setExternalAverageEntryPrice(new BigDecimal(averageEntryPrice));
        return position;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(21L);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of());
        return tranche;
    }

    private DealTranche trancheWithEntry(Order.Status status, Order.CloseReason closeReason) {
        Order entry = new Order();
        entry.setId(800L);
        entry.setStatus(status);
        entry.setCloseReason(closeReason);
        entry.setPositionReducingOnly(false);
        DealTranche tranche = tranche();
        tranche.setOrders(List.of(entry));
        return tranche;
    }

    private DealTranche trancheWithAttachedProtection() {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setStatus(AttachedAlgoOrder.Status.ACTIVE);
        Order entry = new Order();
        entry.setId(800L);
        entry.setStatus(Order.Status.COMPLETED);
        entry.setCloseReason(Order.CloseReason.FILLED);
        entry.setPositionReducingOnly(false);
        entry.setAttachedAlgoOrders(List.of(protection));
        DealTranche tranche = tranche();
        tranche.setOrders(List.of(entry));
        return tranche;
    }

    private DealTranche trancheWithStandaloneProtection() {
        AlgoOrder protection = new AlgoOrder();
        protection.setId(900L);
        protection.setStatus(AlgoOrder.Status.ACTIVE);
        protection.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        DealTranche tranche = tranche();
        tranche.setAlgoOrders(List.of(protection));
        return tranche;
    }

    private StrategyCondition condition(StrategyConditionRule rule) {
        StrategyCondition condition = new StrategyCondition();
        condition.setRules(List.of(rule));
        return condition;
    }

    private StrategyConditionRule rule(StrategyConditionRuleType type, StrategyConditionOperand left,
                                       StrategyConditionOperand right) {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(type);
        rule.setOperator(StrategyConditionOperator.GTE);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return rule;
    }

    private StrategyConditionOperand constant(String value, ConstantValueType valueType) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.CONSTANT);
        operand.setValueType(valueType);
        operand.setValue(value);
        return operand;
    }
}
