package com.example.strategy.engine.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import java.math.BigDecimal;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Контракт слоя расчёта — <b>возвратный</b>, а не бросковый.
 *
 * <p>Это и есть предмет теста: контролируемая нехватка операнда обязана
 * прийти к вызывающему значением с машинным кодом, а не исключением.
 * Брошенное наружу, оно попало бы в общий перехват прохода и увело бы
 * сделку в ошибку — там, где по разбору полагается неисполнение шага
 * либо повтор бюджетом (docs/processes/strategy-action-calculation.md).
 *
 * <p>Половины расчёта подменены <b>подклассами-заглушками</b>, а не
 * библиотекой моков: у общего артефакта тестовых зависимостей ровно две,
 * и предмет здесь — оркестрация, а не сами формулы (их держат
 * {@code PriceLevelTest} и {@code OrderSizingSpecTest}).
 */
class StrategyActionCalculationTest {

    private static final CalculatedPrice PRICE = CalculatedPrice.builder()
            .roundedPrice(new BigDecimal("100"))
            .description("price")
            .build();

    private static final CalculatedSize SIZE = CalculatedSize.builder()
            .sizeContracts(new BigDecimal("3"))
            .description("size")
            .build();

    /** Успех несёт обе половины и исходное объявление действия. */
    @Test
    void successCarriesBothHalves() {
        StrategyAction action = action();
        StrategyActionCalculator calculator = calculator(context -> PRICE, context -> SIZE);

        StrategyActionCalculationResult result = calculator.calculate(context(action));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCalculatedAction().getSourceAction()).isSameAs(action);
        assertThat(result.getCalculatedAction().getCalculatedPrice()).isSameAs(PRICE);
        assertThat(result.getCalculatedAction().getCalculatedSize()).isSameAs(SIZE);
        assertThat(result.getCalculatedAction().getDescription()).contains("price").contains("size");
    }

    /**
     * Контролируемая ошибка приходит РЕЗУЛЬТАТОМ, и вторая половина при
     * этом не считается: размера по несуществующей цене не бывает.
     */
    @Test
    void controlledErrorComesBackAsResult() {
        CountingSize size = new CountingSize();
        StrategyActionCalculator calculator = calculator(context -> {
            throw new CalculationException(CalculationError.temporary("NO_REFERENCE_PRICE", "no price"));
        }, size);

        StrategyActionCalculationResult result = calculator.calculate(context(action()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCalculatedAction()).isNull();
        assertThat(result.getError().getCode()).isEqualTo("NO_REFERENCE_PRICE");
        assertThat(result.getError().getRetryable()).isTrue();
        assertThat(size.calls).isZero();
    }

    /**
     * Неожиданное исключение наружу НЕ оборачивается.
     *
     * <p>У него своя классификация: обёрнутое в контролируемую ошибку, оно
     * получило бы бюджет повторов и машинный код, которого никто не
     * назначал (docs/rules/runtime-error-classification.md).
     */
    @Test
    void unexpectedExceptionIsNotWrapped() {
        StrategyActionCalculator calculator = calculator(context -> {
            throw new IllegalStateException("boom");
        }, context -> SIZE);

        assertThatThrownBy(() -> calculator.calculate(context(action())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    /**
     * Контекст без действия — контролируемая ошибка, а не обращение к
     * половинам: на пустом операнде они упали бы неожиданным исключением.
     */
    @Test
    void contextWithoutActionIsRefusedBeforeCalculators() {
        CountingSize size = new CountingSize();
        StrategyActionCalculator calculator = calculator(context -> {
            throw new IllegalStateException("price calculator must not be reached");
        }, size);

        StrategyActionCalculationResult result = calculator.calculate(context(null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("MISSING_CALCULATION_CONTEXT");
        assertThat(size.calls).isZero();
    }

    private StrategyActionCalculator calculator(Function<CalculationContext, CalculatedPrice> price,
                                                Function<CalculationContext, CalculatedSize> size) {
        return new StrategyActionCalculator(new StubPrice(price), new StubSize(size));
    }

    private CalculationContext context(StrategyAction action) {
        return CalculationContext.builder().action(action).build();
    }

    private StrategyAction action() {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setKey("entry");
        return action;
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
    private static class CountingSize implements Function<CalculationContext, CalculatedSize> {

        private int calls;

        @Override
        public CalculatedSize apply(CalculationContext context) {
            calls++;
            return SIZE;
        }
    }
}
