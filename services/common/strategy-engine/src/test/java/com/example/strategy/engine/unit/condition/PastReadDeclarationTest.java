package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.STRUCTURE_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.ema;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicator;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicators;
import static com.example.strategy.engine.unit.condition.ConditionFixture.livePosition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.price;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structure;
import static com.example.strategy.engine.unit.condition.ConditionFixture.trancheWithStandaloneProtection;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Чтения прошлого оценщиком сверяются с перечнями прошлого у грамматики.
 * Перечней два: {@link StrategyCondition#pastIndicatorKeys()} — чьё
 * предыдущее значение индикатора читается, и
 * {@link StrategyCondition#pastPriceKeys()} — по ключу какого
 * индикатора-пары читается предыдущая цена; по ним гейт покрытия ищет обе
 * половины прошлого (docs/rules/market-data-freshness.md), само же прошлое
 * читает оценка внутри себя (docs/components/StrategyConditionEvaluator.md).
 * Связи между носителями у кода нет, и проба её мерит: тип правила,
 * начавший читать прошлое без объявления в модели, роняет её — и тип,
 * объявленный в модели и прошлого не читающий, тоже.
 *
 * <p><b>Базовая сборка:</b> условие из одного правила каждого значения
 * перечня {@link StrategyConditionRuleType} — новый тип попадает в пробу
 * сам. У индикаторной пробы оба операнда индикаторные, с различимыми
 * ключами; у ценовой — цена по одну сторону и индикатор по другую, в обеих
 * ориентациях, и ключ индикатора у ориентаций различается. Раскладки
 * последних и предыдущих значений и предыдущих цен заполнены по обоим
 * ключам, контекст несёт все факты сделки — пустота соседнего операнда не
 * обрывает оценку раньше чтения прошлого. Раскладка — предмет пробы —
 * запоминает ключи, которые у неё спросили.
 *
 * <p><b>Операторы перебираются все, включая пустой,</b> и сверяется
 * объединение чтений: перечень модели от оператора не зависит, а оценка
 * вправе читать прошлое не при каждом.
 *
 * <p><b>Подменено состояние, а не предикат</b> (.claude/rules/codestyle.md
 * §«Тесты доменных моделей»): перечень считает сама модель на настоящих
 * полях, а раскладка — настоящая таблица значений, лишь запоминающая
 * адресованные чтения. Граница записи названа: чтение ключом
 * ({@code get}, {@code getOrDefault}, {@code containsKey}) она видит, обход
 * раскладки целиком — нет.
 */
class PastReadDeclarationTest {

    private static final String LEFT_KEY = "past_left";
    private static final String RIGHT_KEY = "past_right";

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    @ParameterizedTest(name = "тип правила {0}: чтения прошлого равны перечню модели")
    @EnumSource(StrategyConditionRuleType.class)
    @DisplayName("Чтения прошлого оценщиком равны перечню прошлого у грамматики — по каждому типу правила")
    void pastReadsOfTheEvaluatorEqualThePastKeysDeclaredByTheModel(StrategyConditionRuleType ruleType) {
        Set<String> readPast = new HashSet<>();
        for (StrategyConditionOperator operator : operatorsWithAbsent()) {
            readPast.addAll(pastIndicatorReadsOf(ruleOf(ruleType, operator,
                    indicator(LEFT_KEY, null), indicator(RIGHT_KEY, null))));
        }

        Set<String> declaredPast = condition(ruleOf(ruleType, null,
                indicator(LEFT_KEY, null), indicator(RIGHT_KEY, null))).pastIndicatorKeys();

        assertThat(readPast)
                .as("ключи, чьё прошлое оценщик спросил у правила %s, и перечень прошлого у модели", ruleType)
                .containsExactlyInAnyOrderElementsOf(declaredPast);
    }

    @ParameterizedTest(name = "тип правила {0}: чтения прошлого цены равны перечню модели")
    @EnumSource(StrategyConditionRuleType.class)
    @DisplayName("Чтения прошлого цены оценщиком равны перечню прошлого цены у грамматики — по каждому типу правила")
    void pastPriceReadsOfTheEvaluatorEqualThePastPriceKeysDeclaredByTheModel(StrategyConditionRuleType ruleType) {
        Set<String> readPast = new HashSet<>();
        for (StrategyConditionOperator operator : operatorsWithAbsent()) {
            readPast.addAll(pastPriceReadsOf(ruleOf(ruleType, operator, lastPrice(), indicator(RIGHT_KEY, null))));
            readPast.addAll(pastPriceReadsOf(ruleOf(ruleType, operator, indicator(LEFT_KEY, null), lastPrice())));
        }

        Set<String> declaredPast = condition(
                ruleOf(ruleType, null, lastPrice(), indicator(RIGHT_KEY, null)),
                ruleOf(ruleType, null, indicator(LEFT_KEY, null), lastPrice())).pastPriceKeys();

        assertThat(readPast)
                .as("ключи, по которым оценщик спросил прошлое цены у правила %s, и перечень модели", ruleType)
                .containsExactlyInAnyOrderElementsOf(declaredPast);
    }

    private Set<String> pastIndicatorReadsOf(StrategyConditionRule rule) {
        PastReadRecorder<IndicatorValue> previous = new PastReadRecorder<>();
        previous.putAll(previousIndicators());
        evaluator.evaluate(condition(rule), contextWith(previous, previousPrices()));
        return previous.getRequestedKeys();
    }

    private Set<String> pastPriceReadsOf(StrategyConditionRule rule) {
        PastReadRecorder<BigDecimal> previousPrices = new PastReadRecorder<>();
        previousPrices.putAll(previousPrices());
        evaluator.evaluate(condition(rule), contextWith(previousIndicators(), previousPrices));
        return previousPrices.getRequestedKeys();
    }

    /** Правило объявленного типа с названными операндами; порог хода объявлен. */
    private StrategyConditionRule ruleOf(StrategyConditionRuleType ruleType, StrategyConditionOperator operator,
                                         StrategyConditionOperand left, StrategyConditionOperand right) {
        StrategyConditionRule rule = rule(ruleType, operator, left, right);
        rule.setPercents(BigDecimal.ONE);
        return rule;
    }

    private StrategyConditionOperand lastPrice() {
        return price();
    }

    /** Все значения перечня операторов и пустой оператор. */
    private List<StrategyConditionOperator> operatorsWithAbsent() {
        List<StrategyConditionOperator> operators = new ArrayList<>(Arrays.asList(StrategyConditionOperator.values()));
        operators.add(null);
        return operators;
    }

    /** Предыдущие значения индикаторов по обоим ключам. */
    private Map<String, IndicatorValue> previousIndicators() {
        return indicators(LEFT_KEY, ema("9"), RIGHT_KEY, ema("10"));
    }

    /** Предыдущие цены по обоим ключам. */
    private Map<String, BigDecimal> previousPrices() {
        return Map.of(LEFT_KEY, new BigDecimal("95"), RIGHT_KEY, new BigDecimal("96"));
    }

    /** Контекст сделки со ВСЕМИ фактами и заполненными раскладками. */
    private ConditionEvaluationContext contextWith(Map<String, IndicatorValue> previous,
                                                   Map<String, BigDecimal> previousPrices) {
        return base()
                .latestIndicators(indicators(LEFT_KEY, ema("12"), RIGHT_KEY, ema("10")))
                .previousIndicators(previous)
                .previousPrices(previousPrices)
                .structures(Map.of(STRUCTURE_KEY, structure(MarketStructure.Type.RANGE)))
                .price(new BigDecimal("103"))
                .marketPhase(MarketPhase.Type.BULL_TREND)
                .entryMarketPhase(MarketPhase.Type.BEAR_TREND)
                .direction(StrategyTradeDirection.LONG)
                .activePosition(livePosition("100"))
                .tranche(trancheWithStandaloneProtection(AlgoOrder.ConditionType.STOP_LOSS))
                .build();
    }

    /** Раскладка, запоминающая ключи адресованных чтений. */
    private static final class PastReadRecorder<V> extends HashMap<String, V> {

        @Getter
        private final Set<String> requestedKeys = new HashSet<>();

        @Override
        public V get(Object key) {
            requestedKeys.add((String) key);
            return super.get(key);
        }

        @Override
        public V getOrDefault(Object key, V defaultValue) {
            requestedKeys.add((String) key);
            return super.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean containsKey(Object key) {
            requestedKeys.add((String) key);
            return super.containsKey(key);
        }
    }
}
