package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.FAST_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.STRUCTURE_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.ema;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicator;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicators;
import static com.example.strategy.engine.unit.condition.ConditionFixture.livePosition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
import static com.example.strategy.engine.unit.condition.ConditionFixture.order;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structure;
import static com.example.strategy.engine.unit.condition.ConditionFixture.trancheWithOrders;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов у интерпретатора — группа `U13` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/components/StrategyConditionEvaluator.md §Границы;
 * docs/architecture/services.md §«Что в библиотеку НЕ уезжает»).
 *
 * <p><b>Клеймы этой группы читаются составом класса, а не прогоном
 * одного входа:</b> «ни базы, ни соседа, ни брокера, ни часов» —
 * утверждение обо <b>всех</b> входах сразу, и прогон одного входа его не
 * устанавливает (.claude/rules/measurement-commands.md). Поэтому предмет
 * наблюдения здесь — объявленные члены предмета и неизменность моделей
 * контекста, а не выход одного вызова.
 */
class ConditionEvaluatorBoundariesTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Коллабораторов у предмета нет вовсе, а конструктор пуст. */
    @Test
    @DisplayName("U13.1 — у класса нет ни одного поля-коллаборатора, и конструктор у него один, пустой")
    void u13_1_theEvaluatorHasNoCollaborators() {
        assertThat(instanceFields()).as("полей экземпляра у интерпретатора нет").isEmpty();
        assertThat(StrategyConditionEvaluator.class.getDeclaredConstructors())
                .as("конструктор один, и аргументов у него нет")
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
    }

    /** Контекст и его модели после оценки не изменены ни одним значением. */
    @Test
    @DisplayName("U13.2 — оценка прошла: ни эпизод, ни транш, ни заявка, ни раскладки не изменены")
    void u13_2_theContextModelsAreNotMutated() {
        Position episode = livePosition("100");
        Order entry = order(800L, Order.Status.COMPLETED, Order.CloseReason.FILLED, false);
        DealTranche dealTranche = trancheWithOrders(entry);
        MarketStructure marketStructure = structure(MarketStructure.Type.RANGE);
        Map<String, IndicatorValue> latest = indicators(FAST_KEY, ema("12"));
        ConditionEvaluationContext context = base()
                .latestIndicators(latest)
                .structures(Map.of(STRUCTURE_KEY, marketStructure))
                .price(new BigDecimal("103"))
                .activePosition(episode)
                .tranche(dealTranche)
                .build();

        assertThat(evaluator.evaluate(indicatorCondition(), context)).isTrue();
        assertThat(evaluator.evaluate(condition(rule(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED)), context))
                .isTrue();

        assertThat(episode.getExternalSize()).isEqualByComparingTo("1");
        assertThat(episode.getExternalAverageEntryPrice()).isEqualByComparingTo("100");
        assertThat(episode.getStatus()).isEqualTo(Position.Status.ACTIVE);
        assertThat(entry.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(entry.getAveragePrice()).as("налив ноги не появился").isNull();
        assertThat(dealTranche.getOrders()).hasSize(1);
        assertThat(marketStructure.getBreakoutEvent()).as("события пробоя интерпретатор не заводит").isNull();
        assertThat(latest).as("раскладка нового значения не получила").hasSize(1);
    }

    /** В ответе нет ни шага, ни действия, ни решения о применении — только булево значение. */
    @Test
    @DisplayName("U13.3 — поверхность предмета: один публичный метод, и отдаёт он Boolean")
    void u13_3_theOnlyOutputIsABoolean() {
        List<Method> surface = publicMethods();

        assertThat(surface).as("вход у предмета один").singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("evaluate");
                    assertThat(method.getReturnType()).isEqualTo(Boolean.class);
                });
    }

    /**
     * Своего предиката свежести интерпретатор не имеет и его не
     * применяет: отсутствующий операнд и устаревший для него
     * НЕРАЗЛИЧИМЫ, и различать их незачем.
     */
    @Test
    @DisplayName("U13.4 — оценка на устаревших данных: ответ тот же, что на свежих")
    void u13_4_thereIsNoFreshnessPredicateOfItsOwn() {
        IndicatorValue stale = ema("12");
        stale.setCandleTimestamp(OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        ConditionEvaluationContext staleContext = base()
                .latestIndicators(indicators(FAST_KEY, stale))
                .build();
        ConditionEvaluationContext freshContext = base()
                .latestIndicators(indicators(FAST_KEY, ema("12")))
                .build();

        assertThat(evaluator.evaluate(indicatorCondition(), staleContext))
                .as("возраст значения интерпретатору не виден")
                .isEqualTo(evaluator.evaluate(indicatorCondition(), freshContext));
    }

    /**
     * Журнальных отчётов, событий и метрик интерпретатор не заводит;
     * единственный его след, кроме ответа, — предупреждение о неисполнимом
     * типе (`U12.1`).
     */
    @Test
    @DisplayName("U13.5 — оценка правил, читающих факты сделки: записей журнала нет ни одной")
    void u13_5_theOnlyTraceBesidesTheAnswerIsTheUnevaluableTypeWarning() {
        ConditionEvaluationContext context = base()
                .activePosition(livePosition("100"))
                .tranche(trancheWithOrders(order(800L, Order.Status.COMPLETED, Order.CloseReason.FILLED, false)))
                .build();

        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            evaluator.evaluate(condition(rule(StrategyConditionRuleType.POSITION_OPENED)), context);
            evaluator.evaluate(condition(rule(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED)), context);
            evaluator.evaluate(condition(rule(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS)), context);
            evaluator.evaluate(condition(rule(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS)), context);

            assertThat(log.messages()).as("отчётов, событий и метрик у предмета нет").isEmpty();
        }
    }

    /** Состояния между вызовами интерпретатор не держит — полей у него нет. */
    @Test
    @DisplayName("U13.6 — два вызова с одним и тем же контекстом: ответ один и тот же")
    void u13_6_theEvaluatorKeepsNoStateBetweenCalls() {
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(FAST_KEY, ema("12")))
                .build();

        Boolean first = evaluator.evaluate(indicatorCondition(), context);
        Boolean second = evaluator.evaluate(indicatorCondition(), context);

        assertThat(second).isEqualTo(first);
        assertThat(instanceFields()).as("держать состояние не в чем").isEmpty();
    }

    private StrategyCondition indicatorCondition() {
        return condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE, StrategyConditionOperator.GT,
                indicator(FAST_KEY, null), number("10")));
    }

    private List<String> instanceFields() {
        return Arrays.stream(StrategyConditionEvaluator.class.getDeclaredFields())
                .filter(field -> isFalse(isStatic(field.getModifiers())))
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    private List<Method> publicMethods() {
        return Arrays.stream(StrategyConditionEvaluator.class.getDeclaredMethods())
                .filter(method -> isPublic(method.getModifiers()))
                .collect(Collectors.toList());
    }
}
