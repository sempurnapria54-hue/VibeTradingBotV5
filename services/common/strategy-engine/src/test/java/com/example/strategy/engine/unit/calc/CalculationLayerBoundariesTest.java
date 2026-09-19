package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.attachedStop;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.detail;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryOrder;
import static com.example.strategy.engine.unit.calc.CalcFixture.liveEpisode;
import static com.example.strategy.engine.unit.calc.CalcFixture.marketPlacement;
import static com.example.strategy.engine.unit.calc.CalcFixture.reduceOnlyAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.rules;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.tranche;
import static java.lang.reflect.Modifier.isStatic;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов у слоя — группа `U13` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/architecture/services.md §«Что в библиотеку НЕ уезжает»;
 * docs/components/StrategyActionCalculator.md §Границы).
 *
 * <p><b>Клеймы этой группы читаются составом класса, а не прогоном
 * входа:</b> «ни базы, ни соседа, ни брокера, ни часов» — утверждение обо
 * <b>всех</b> входах сразу, и прогон одного входа его не устанавливает
 * (.claude/rules/measurement-commands.md). Поэтому предмет наблюдения
 * здесь — объявленные поля предмета и состав его результата, а не выход
 * одного вызова.
 */
class CalculationLayerBoundariesTest {

    private final PriceCalculator priceCalculator = new PriceCalculator();

    private final SizeCalculator sizeCalculator = new SizeCalculator();

    private final StrategyActionCalculator calculator =
            new StrategyActionCalculator(priceCalculator, sizeCalculator);

    /** Коллабораторов у половин нет вовсе, а у оркестратора их ровно два — его же половины. */
    @Test
    @DisplayName("U13.1 — у обеих половин нет ни одного поля-коллаборатора, у оркестратора их ровно два")
    void u13_1_theLayerHasNoCollaboratorsBeyondItsOwnHalves() {
        assertThat(instanceFields(PriceCalculator.class)).as("поля расчёта цены").isEmpty();
        assertThat(instanceFields(SizeCalculator.class)).as("поля расчёта размера").isEmpty();
        assertThat(instanceFields(StrategyActionCalculator.class))
                .as("у оркестратора ровно две половины и ничего сверх")
                .containsExactlyInAnyOrder(PriceCalculator.class, SizeCalculator.class);
    }

    /** Контекст и его модели после успешного расчёта не изменены ни одним значением. */
    @Test
    @DisplayName("U13.2 — расчёт прошёл успешно: ни заявка, ни позиция, ни транш, ни деталь не изменены")
    void u13_2_theContextModelsAreNotMutated() {
        StrategyOrderAction action = attachedEntry();
        Order order = entryOrder("3000", null);
        Position episode = liveEpisode("2990");
        DealTranche dealTranche = tranche("10");
        StrategyDetail strategyDetail = detail("1", List.of((StrategyAction) action));
        CalculationContext context = base(action)
                .instrumentExternalRules(rules())
                .riskBase(new BigDecimal("10000"))
                .entryOrder(order)
                .activePosition(episode)
                .dealTranche(dealTranche)
                .strategyDetail(strategyDetail)
                .build();

        assertThat(calculator.calculate(context).isSuccess()).isTrue();

        assertThat(order.getPrice()).isEqualByComparingTo("3000");
        assertThat(order.getAveragePrice()).as("налив своей ноги не появился").isNull();
        assertThat(episode.getExternalAverageEntryPrice()).isEqualByComparingTo("2990");
        assertThat(episode.getExternalSize()).isEqualByComparingTo("2");
        assertThat(dealTranche.exposure()).isEqualByComparingTo("10");
        assertThat(strategyDetail.getRiskPerActionPercent()).isEqualByComparingTo("1");
        assertThat(action.getAllocationPercents()).isEqualByComparingTo("100");
    }

    /** В результате нет ни команды, ни признака отмены, ни решения о применимости. */
    @Test
    @DisplayName("U13.3 — успешный результат: ни команды, ни признака отмены, ни решения о применимости")
    void u13_3_theResultCarriesNoCommandAndNoApplicabilityDecision() {
        assertThat(fieldNames(CalculatedStrategyAction.class))
                .containsExactlyInAnyOrder("sourceAction", "calculatedPrice", "calculatedSize", "description");
    }

    /** На контролируемом отказе слой не заводит ни отчётов, ни событий: его выход — только ошибка. */
    @Test
    @DisplayName("U13.4 — расчёт отказал контролируемой ошибкой: выход — только ошибка, модели не изменены")
    void u13_4_aControlledRefusalProducesNothingButTheError() {
        StrategyOrderAction action = attachedEntry();
        DealTranche dealTranche = tranche("10");
        CalculationContext context = base(action)
                .instrumentExternalRules(rules())
                .dealTranche(dealTranche)
                .strategyDetail(detail("1", List.of((StrategyAction) action)))
                .build();

        StrategyActionCalculationResult result = calculator.calculate(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("MISSING_RISK_BASE");
        assertThat(result.getCalculatedAction()).isNull();
        assertThat(fieldNames(StrategyActionCalculationResult.class))
                .as("иных выходов у результата нет")
                .containsExactlyInAnyOrder("status", "calculatedAction", "error");
        assertThat(dealTranche.exposure()).as("транш не тронут").isEqualByComparingTo("10");
    }

    /** Сделочных потолков калькулятор не считает: ни остатка бюджета, ни признака исчерпания. */
    @Test
    @DisplayName("U13.5 — расчёт входа: ни остатка бюджета сделки, ни признака его исчерпания в результате нет")
    void u13_5_theResultCarriesNoDealBudgetRemainder() {
        assertThat(fieldNames(CalculatedSize.class))
                .containsExactlyInAnyOrder("sizeContracts", "closeFraction", "notionalUsdt",
                        "sizeMode", "exitOutcome", "description");
    }

    /** Нотинал считается только у входа. */
    @Test
    @DisplayName("U13.6 — расчёт размера выхода: нотинал пуст — он считается только у входа")
    void u13_6_theNotionalIsComputedForTheEntryOnly() {
        CalculationContext context = base(reduceOnlyAction("50"))
                .instrumentExternalRules(rules("0.1", "0.0005", "1", "0.1", "1"))
                .dealTranche(tranche("10"))
                .build();

        CalculatedSize size = sizeCalculator.calculate(context, null);

        assertThat(size.getNotionalUsdt()).isNull();
    }

    /**
     * Цена ноги после срабатывания ни одной тропой слоя не заполняется:
     * её пустота значаща — «рыночное исполнение». Строка добрана
     * под-шагом 3 по пробелу `G5`.
     */
    @Test
    @DisplayName("U13.7 — уровни посчитаны: цена ноги после срабатывания пуста у стопа и у тейка")
    void u13_7_theLegPriceAfterTriggerIsNeverFilled() {
        CalculatedPrice stop = priceCalculator.calculate(base(stopAction("1")).build());
        CalculatedPrice attached = priceCalculator.calculate(base(attachedEntry()).build());

        assertThat(stop.getStopLossPrice().getOrderPrice()).as("пусто — рыночное исполнение").isNull();
        assertThat(attached.getStopLossPrice().getOrderPrice()).isNull();
    }

    // --- состав класса как предмет наблюдения ------------------------------

    private StrategyOrderAction attachedEntry() {
        StrategyOrderAction action = entryAction(marketPlacement());
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setAttachedProtection(attachedStop("1"));
        return action;
    }

    private List<Class<?>> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> isFalse(isStatic(field.getModifiers())))
                .map(Field::getType)
                .collect(Collectors.toList());
    }

    private List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> isFalse(isStatic(field.getModifiers())))
                .map(Field::getName)
                .collect(Collectors.toList());
    }
}
