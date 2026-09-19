package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopAction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.SizeMode;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Действия, которым цена или размер не требуются — группа `U11`
 * документа `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/components/PriceCalculator.md §«Что делает»,
 * docs/components/SizeCalculator.md §«Что делает»).
 *
 * <p><b>Базовая сборка:</b> действие над позицией (не заявка и не
 * условная заявка); контекст в остальном базовый.
 *
 * <p><b>Вход этих клеток исключён ВТОРЫМ рубежом — отбором
 * исполнителя:</b> действие над позицией исполняет `ExitActionExecutor`,
 * а он калькулятора не зовёт вовсе. Клетки прогоняемы и снятию не
 * подлежат — калькулятор есть библиотека, и ветвь «расчёт не требуется»
 * суть объявленная тотальность контракта, которую зовут бэктест и
 * будущий исполнитель замещения.
 */
class NotRequiredCalculationTest {

    private final PriceCalculator priceCalculator = new PriceCalculator();

    private final SizeCalculator sizeCalculator = new SizeCalculator();

    /** Цена действию над позицией не нужна: пусто всё, включая назначение. */
    @Test
    @DisplayName("U11.1 — расчёт цены: режим NOT_REQUIRED; назначение, цены и защитные разделы пусты")
    void u11_1_thePriceIsNotRequiredForAPositionAction() {
        CalculatedPrice price = priceCalculator.calculate(base(positionAction()).build());

        assertThat(price.getPriceMode()).isEqualTo(PriceMode.NOT_REQUIRED);
        assertThat(price.getPurpose()).as("назначение пусто").isNull();
        assertThat(price.getBasePrice()).as("база пуста").isNull();
        assertThat(price.getRawPrice()).as("сырая цена пуста").isNull();
        assertThat(price.getRoundedPrice()).as("округлённая цена пуста").isNull();
        assertThat(price.getStopLossPrice()).as("стоп пуст").isNull();
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
        assertThat(price.getSendPriceToExchange()).isFalse();
    }

    /** Размер действию над позицией не нужен: пусто всё, включая исход выхода. */
    @Test
    @DisplayName("U11.2 — расчёт размера: режим NOT_REQUIRED; размер, доля, нотинал и исход пусты")
    void u11_2_theSizeIsNotRequiredForAPositionAction() {
        CalculatedSize size = sizeCalculator.calculate(base(positionAction()).build(), null);

        assertThat(size.getSizeMode()).isEqualTo(SizeMode.NOT_REQUIRED);
        assertThat(size.getSizeContracts()).as("размер пуст").isNull();
        assertThat(size.getCloseFraction()).as("доля закрытия пуста").isNull();
        assertThat(size.getNotionalUsdt()).as("нотинал пуст").isNull();
        assertThat(size.getExitOutcome()).as("исход выхода пуст").isNull();
    }

    /** На этой тропе ни инструмент, ни рынок не читаются вовсе. */
    @Test
    @DisplayName("U11.3 — правил инструмента и снапшота цен нет: отказа нет ни у цены, ни у размера")
    void u11_3_neitherTheInstrumentNorTheMarketIsReadOnThisPath() {
        CalculationContext context = base(positionAction())
                .instrumentExternalRules(null)
                .marketPriceData(null)
                .build();

        assertThatCode(() -> priceCalculator.calculate(context)).doesNotThrowAnyException();
        assertThatCode(() -> sizeCalculator.calculate(context, null)).doesNotThrowAnyException();
    }

    /** У условной заявки в заявку уезжает триггер, а не цена: режим цены — «не требуется». */
    @Test
    @DisplayName("U11.4 — условная заявка STOP_LOSS: режим NOT_REQUIRED и на биржу цена не уезжает")
    void u11_4_theAlgoOrderCarriesATriggerNotAPrice() {
        CalculatedPrice price = priceCalculator.calculate(base(stopAction("1")).build());

        assertThat(price.getStopLossPrice().getTriggerPrice()).as("уровень при этом заполнен")
                .isEqualByComparingTo("2970");
        assertThat(price.getPriceMode()).isEqualTo(PriceMode.NOT_REQUIRED);
        assertThat(price.getSendPriceToExchange()).isFalse();
    }

    private StrategyPositionAction positionAction() {
        StrategyPositionAction action = new StrategyPositionAction();
        action.setId(3L);
        action.setKey("exit");
        action.setActionType(StrategyActionType.EXIT_ACTION);
        return action;
    }
}
