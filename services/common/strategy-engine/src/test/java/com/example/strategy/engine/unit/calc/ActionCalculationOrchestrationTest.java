package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.attachedStop;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.detail;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.marketPlacement;
import static com.example.strategy.engine.unit.calc.CalcFixture.rules;
import static com.example.strategy.engine.unit.calc.CalcFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.CalculationError;
import com.example.strategy.engine.calc.CalculationErrorType;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Оркестратор: порядок, обёртка отказа и её граница — группа `U12`
 * документа `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/components/StrategyActionCalculator.md,
 * docs/processes/strategy-action-calculation.md).
 *
 * <p><b>Половины подменяются ровно здесь и только здесь:</b> предмет
 * группы — порядок и обёртка, а не числа. Подменяются при этом
 * <b>коллабораторы</b> — подклассами-заглушками, а не доменные модели
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»); там, где
 * предмет клетки — сам исход расчёта (`U12.1`, `U12.10`), работают
 * настоящие половины.
 *
 * <p><b>Сменяет прежнюю пробу предмета целиком.</b> Класс
 * {@code com.example.strategy.engine.calc.StrategyActionCalculationTest}
 * проверял ровно те ожидания, которые теперь стоя́т клетками, и вторая
 * запись одного ожидания расходится с первой при первой же правке
 * (.claude/rules/carrier-levels.md). Клетки-преемницы: `U12.1` (успех
 * несёт обе половины), `U12.4` (контролируемая ошибка приходит
 * результатом, размер не зовётся), `U12.6` (неожиданное исключение не
 * оборачивается), `U12.3` (контекст без действия отвергается до половин).
 */
class ActionCalculationOrchestrationTest {

    private static final CalculatedPrice PRICE = CalculatedPrice.builder()
            .roundedPrice(new BigDecimal("100"))
            .description("price")
            .build();

    private static final CalculatedSize SIZE = CalculatedSize.builder()
            .sizeContracts(new BigDecimal("3"))
            .description("size")
            .build();

    /** Успех несёт обе половины, исходное действие и склейку пояснений. */
    @Test
    @DisplayName("U12.1 — базовая сборка: успех несёт обе половины и исходное действие; ошибка пуста")
    void u12_1_theSuccessCarriesBothHalves() {
        StrategyOrderAction action = attachedEntry();
        CalculationContext context = entryContext(action);

        StrategyActionCalculationResult result = realCalculator().calculate(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCalculatedAction().getSourceAction()).isSameAs(action);
        assertThat(result.getCalculatedAction().getCalculatedPrice().getRoundedPrice())
                .isEqualByComparingTo("2970");
        assertThat(result.getCalculatedAction().getCalculatedSize().getSizeContracts())
                .isEqualByComparingTo("3.0");
        assertThat(result.getError()).as("ошибка пуста").isNull();
    }

    /** Пустой контекст — контролируемая ошибка; половины не зовутся ни разу. */
    @Test
    @DisplayName("U12.2 — контекст пуст: ошибка MISSING_CALCULATION_CONTEXT, постоянная; половины не зовутся")
    void u12_2_anEmptyContextIsRefusedBeforeTheCalculators() {
        CountingPrice price = new CountingPrice(context -> PRICE);
        CountingSize size = new CountingSize(context -> SIZE);

        StrategyActionCalculationResult result = new StrategyActionCalculator(price, size).calculate(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("MISSING_CALCULATION_CONTEXT");
        assertThat(result.getError().getType()).isEqualTo(CalculationErrorType.PERMANENT);
        assertThat(result.getError().getRetryable()).isFalse();
        assertThat(result.getCalculatedAction()).as("рассчитанное действие пусто").isNull();
        assertThat(price.calls).as("расчёт цены не зовётся").isZero();
        assertThat(size.calls).as("расчёт размера не зовётся").isZero();
    }

    /** Контекст без действия — тот же результат-ошибка. */
    @Test
    @DisplayName("U12.3 — контекст есть, действия в нём нет: тот же результат-ошибка")
    void u12_3_aContextWithoutAnActionIsRefusedToo() {
        CountingPrice price = new CountingPrice(context -> PRICE);
        CountingSize size = new CountingSize(context -> SIZE);

        StrategyActionCalculationResult result = new StrategyActionCalculator(price, size)
                .calculate(CalculationContext.builder().build());

        assertThat(result.getError().getCode()).isEqualTo("MISSING_CALCULATION_CONTEXT");
        assertThat(price.calls).isZero();
        assertThat(size.calls).isZero();
    }

    /** Отказ цены приходит результатом; размера по несуществующей цене не бывает. */
    @Test
    @DisplayName("U12.4 — расчёт цены отказывает: результат-ошибка с тем же кодом; размер не зовётся")
    void u12_4_aPriceRefusalComesBackAsResultAndStopsTheSize() {
        CountingSize size = new CountingSize(context -> SIZE);
        StrategyActionCalculator calculator = new StrategyActionCalculator(
                new StubPrice(context -> {
                    throw refusal("NO_REFERENCE_PRICE");
                }), size);

        StrategyActionCalculationResult result = calculator.calculate(entryContext(attachedEntry()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("NO_REFERENCE_PRICE");
        assertThat(size.calls).as("расчёт размера не зовётся вовсе").isZero();
    }

    /** Отказ размера приходит результатом; посчитанная цена наружу не отдаётся. */
    @Test
    @DisplayName("U12.5 — расчёт размера отказывает: результат-ошибка с тем же кодом; цена наружу не идёт")
    void u12_5_aSizeRefusalHidesTheComputedPrice() {
        StrategyActionCalculator calculator = new StrategyActionCalculator(
                new StubPrice(context -> PRICE),
                new StubSize(context -> {
                    throw refusal("MISSING_RISK_BASE");
                }));

        StrategyActionCalculationResult result = calculator.calculate(entryContext(attachedEntry()));

        assertThat(result.getError().getCode()).isEqualTo("MISSING_RISK_BASE");
        assertThat(result.getCalculatedAction()).as("посчитанная цена наружу не отдаётся").isNull();
    }

    /** Неожиданное исключение уходит наружу необёрнутым: у него своя классификация. */
    @Test
    @DisplayName("U12.6 — расчёт цены бросает неожиданное исключение: наружу уходит необёрнутым")
    void u12_6_anUnexpectedExceptionIsNotWrapped() {
        StrategyActionCalculator calculator = new StrategyActionCalculator(
                new StubPrice(context -> {
                    throw new IllegalStateException("boom");
                }),
                new StubSize(context -> SIZE));
        CalculationContext context = entryContext(attachedEntry());

        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    /** Порядок «цена, затем размер» несущий: цена считается раз и передаётся тем же объектом. */
    @Test
    @DisplayName("U12.7 — базовая сборка: расчёт цены зовётся ровно раз, и его результат уезжает в размер")
    void u12_7_thePriceIsComputedOnceAndHandedToTheSize() {
        CountingPrice price = new CountingPrice(context -> PRICE);
        List<CalculatedPrice> received = new ArrayList<>();
        StrategyActionCalculator calculator = new StrategyActionCalculator(price,
                new RecordingSize(received));

        calculator.calculate(entryContext(attachedEntry()));

        assertThat(price.calls).as("расчёт цены зовётся ровно один раз").isEqualTo(1);
        assertThat(received).as("в расчёт размера уезжает тот же объект").singleElement().isSameAs(PRICE);
    }

    /** Пояснение результата — склейка обеих половин; своего текста оркестратор не добавляет. */
    @Test
    @DisplayName("U12.8 — обе половины дали пояснения: склейка обоих, своего текста нет")
    void u12_8_theDescriptionIsTheJoinOfBothHalves() {
        StrategyActionCalculator calculator = new StrategyActionCalculator(
                new StubPrice(context -> PRICE), new StubSize(context -> SIZE));

        StrategyActionCalculationResult result = calculator.calculate(entryContext(attachedEntry()));

        assertThat(result.getCalculatedAction().getDescription()).isEqualTo("price; size");
    }

    /** Пустое пояснение половины склейку не роняет и слов не подставляет. */
    @Test
    @DisplayName("U12.9 — пояснение одной из половин пусто: склейка не падает и слов не подставляет")
    void u12_9_anEmptyHalfDescriptionNeitherFailsNorSubstitutesWords() {
        StrategyActionCalculator calculator = new StrategyActionCalculator(
                new StubPrice(context -> CalculatedPrice.builder().roundedPrice(BigDecimal.ONE).build()),
                new StubSize(context -> SIZE));

        StrategyActionCalculationResult result = calculator.calculate(entryContext(attachedEntry()));

        assertThat(result.getCalculatedAction().getDescription()).isEqualTo("; size");
    }

    /**
     * Тип ошибки — ПОСТОЯННАЯ у всех троп без исключения: временной
     * ошибки расчёта не производит ни одна. Читатель по типу ветвится и
     * заводит повтор, который недостижим, — находка `F-3`
     * (`.claude/work/backlog.md` §«Временная ошибка расчёта писателя не
     * имеет, а ядро по ней ветвится»).
     */
    @Test
    @DisplayName("U12.10 — любой контролируемый отказ слоя: тип PERMANENT, повтор запрещён")
    void u12_10_everyControlledRefusalOfTheLayerIsPermanent() {
        StrategyActionCalculator calculator = realCalculator();
        List<CalculationContext> refusing = List.of(
                missingRiskBaseContext(),
                noReferencePriceContext(),
                missingStopSettingsContext());

        assertThat(refusing).allSatisfy(context -> {
            StrategyActionCalculationResult result = calculator.calculate(context);
            assertThat(result.isSuccess()).as("контекст обязан отказать").isFalse();
            assertThat(result.getError().getType()).isEqualTo(CalculationErrorType.PERMANENT);
            assertThat(result.getError().getRetryable()).isFalse();
        });
    }

    // --- базовая сборка и отклонения от неё --------------------------------

    private StrategyActionCalculator realCalculator() {
        return new StrategyActionCalculator(new PriceCalculator(), new SizeCalculator());
    }

    private StrategyOrderAction attachedEntry() {
        StrategyOrderAction action = entryAction(marketPlacement());
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setAttachedProtection(attachedStop("1"));
        return action;
    }

    private CalculationContext entryContext(StrategyOrderAction action) {
        return base(action)
                .instrumentExternalRules(rules())
                .riskBase(new BigDecimal("10000"))
                .strategyDetail(detail("1", List.of((StrategyAction) action)))
                .build();
    }

    private CalculationContext missingRiskBaseContext() {
        StrategyOrderAction action = attachedEntry();
        return base(action)
                .instrumentExternalRules(rules())
                .strategyDetail(detail("1", List.of((StrategyAction) action)))
                .build();
    }

    private CalculationContext noReferencePriceContext() {
        StrategyOrderAction action = entryAction(null);
        return base(action)
                .marketPriceData(null)
                .strategyDetail(detail("1", List.of((StrategyAction) action)))
                .build();
    }

    private CalculationContext missingStopSettingsContext() {
        StrategyAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        return base(action)
                .dealTranche(tranche("10"))
                .strategyDetail(detail("1", List.of(action)))
                .build();
    }

    private CalculationException refusal(String code) {
        return new CalculationException(CalculationError.permanent(code, "refused"));
    }

    /** Половина цены, отвечающая тем, что задал сценарий. */
    private static class StubPrice extends PriceCalculator {

        private final Function<CalculationContext, CalculatedPrice> answer;

        StubPrice(Function<CalculationContext, CalculatedPrice> answer) {
            this.answer = answer;
        }

        @Override
        public CalculatedPrice calculate(CalculationContext context) {
            return answer.apply(context);
        }
    }

    /** Половина цены, считающая обращения к себе. */
    private static class CountingPrice extends StubPrice {

        private int calls;

        CountingPrice(Function<CalculationContext, CalculatedPrice> answer) {
            super(answer);
        }

        @Override
        public CalculatedPrice calculate(CalculationContext context) {
            calls++;
            return super.calculate(context);
        }
    }

    /** Половина размера, отвечающая тем, что задал сценарий. */
    private static class StubSize extends SizeCalculator {

        private final Function<CalculationContext, CalculatedSize> answer;

        StubSize(Function<CalculationContext, CalculatedSize> answer) {
            this.answer = answer;
        }

        @Override
        public CalculatedSize calculate(CalculationContext context, CalculatedPrice price) {
            return answer.apply(context);
        }
    }

    /** Половина размера, считающая обращения к себе. */
    private static class CountingSize extends StubSize {

        private int calls;

        CountingSize(Function<CalculationContext, CalculatedSize> answer) {
            super(answer);
        }

        @Override
        public CalculatedSize calculate(CalculationContext context, CalculatedPrice price) {
            calls++;
            return super.calculate(context, price);
        }
    }

    /** Половина размера, запоминающая полученную цену. */
    private static class RecordingSize extends SizeCalculator {

        private final List<CalculatedPrice> received;

        RecordingSize(List<CalculatedPrice> received) {
            this.received = received;
        }

        @Override
        public CalculatedSize calculate(CalculationContext context, CalculatedPrice price) {
            received.add(price);
            return SIZE;
        }
    }
}
