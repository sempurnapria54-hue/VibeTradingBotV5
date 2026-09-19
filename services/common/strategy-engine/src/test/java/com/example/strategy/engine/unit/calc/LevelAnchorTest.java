package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.ASK_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.BID_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.LAST_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryOrder;
import static com.example.strategy.engine.unit.calc.CalcFixture.liveEpisode;
import static com.example.strategy.engine.unit.calc.CalcFixture.position;
import static com.example.strategy.engine.unit.calc.CalcFixture.prices;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopAction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.core.position.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Якорь уровня: ветвь по живому эпизоду — группа `U3` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/spec/stop-distance.json, величина {@code entryAnchor}; перечень
 * потребителей якоря закрыт — docs/components/PriceCalculator.md
 * §«Якорь уровня — один на все уровни»).
 *
 * <p><b>Базовая сборка:</b> направление `LONG`; шаг цены {@code 0.1};
 * последняя цена {@code 3000}; входная заявка с плановой ценой
 * {@code 3000}, налива у неё нет; живого эпизода нет; действие — условная
 * заявка `STOP_LOSS` со способом «доля от цены входа», доля {@code 1}.
 *
 * <p><b>Якорь наблюдается через посчитанный уровень</b> — собственного
 * выхода у него нет: доля {@code 1} делает уровень линейной функцией
 * якоря, и подмена якоря видна числом.
 *
 * <p><b>Сменяет часть прежней пробы предмета</b>
 * ({@code com.example.strategy.engine.calc.PriceLevelTest}): её
 * клетки-преемницы здесь — `U3.1` (без эпизода якорь плановый), `U3.2`
 * (якорь есть наблюдённая средняя живого эпизода) и `U3.3` (эпизод без
 * наблюдённой средней отказывает, а не откатывается к плановой).
 */
class LevelAnchorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    /** Пока живого эпизода нет, якорь — плановая цена своей ноги. */
    @Test
    @DisplayName("U3.1 — базовая сборка: якорь 3000, стоп 2970; цена заявки не рассчитывается")
    void u3_1_withoutALiveEpisodeTheAnchorIsThePlannedLegPrice() {
        CalculatedPrice price = calculator.calculate(base(stopAction("1")).build());

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2970");
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
        assertThat(price.getPriceMode()).isEqualTo(PriceMode.NOT_REQUIRED);
        assertThat(price.getSendPriceToExchange()).isFalse();
    }

    /** Живой эпизод есть — якорь есть его фактическая средняя цена входа. */
    @Test
    @DisplayName("U3.2 — живой эпизод со средней 2990: якорь 2990, стоп 2960.1")
    void u3_2_theLiveEpisodeAverageIsTheAnchor() {
        CalculationContext context = base(stopAction("1"))
                .activePosition(liveEpisode("2990"))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2960.1");
    }

    /** Средняя не наблюдена — отказ, а не откат к плановой на непредъявленном факте. */
    @Test
    @DisplayName("U3.3 — живой эпизод без наблюдённой средней: отказ ENTRY_ANCHOR_UNAVAILABLE")
    void u3_3_aLiveEpisodeWithoutAnObservedAverageRefuses() {
        CalculationContext context = base(stopAction("1"))
                .activePosition(liveEpisode(null))
                .build();

        assertRefuses(context, "ENTRY_ANCHOR_UNAVAILABLE");
    }

    /**
     * Ветвь мерит живой РИСК, а не наличие строки эпизода: нулевой
     * наблюдённый размер риска не несёт, и якорь остаётся плановым. Этим
     * якорь и отличается от базы размещения (`U1.12`).
     */
    @Test
    @DisplayName("U3.4 — эпизод ACTIVE с наблюдённым размером 0: якорь 3000 — плановая")
    void u3_4_theBranchMeasuresLiveRiskNotTheEpisodeRow() {
        CalculationContext context = base(stopAction("1"))
                .activePosition(position(Position.Status.ACTIVE, "0", "2990"))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .as("2990 означало бы, что ветвь мерит строку эпизода")
                .isEqualByComparingTo("2970");
    }

    /** Входной заявки нет — якорь есть рыночный ориентир. */
    @Test
    @DisplayName("U3.5 — входной заявки в контексте нет: якорь 3000 — рыночный ориентир")
    void u3_5_withoutAnEntryOrderTheAnchorIsTheMarketReference() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(null)
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2970");
    }

    /** Плановой цены у входной заявки нет (вход по рынку) — тот же ориентир. */
    @Test
    @DisplayName("U3.6 — у входной заявки плановой цены нет: якорь 3000 — рыночный ориентир")
    void u3_6_anEntryOrderWithoutAPlannedPriceFallsBackToTheMarketReference() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(entryOrder(null, null))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2970");
    }

    /** Ни заявки, ни последней цены — якорю взяться неоткуда. */
    @Test
    @DisplayName("U3.7 — входной заявки нет и последней цены нет: отказ NO_REFERENCE_PRICE")
    void u3_7_neitherAnEntryOrderNorALastPriceRefuses() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(null)
                .marketPriceData(prices(null, BID_PRICE, ASK_PRICE))
                .build();

        assertRefuses(context, "NO_REFERENCE_PRICE");
    }

    /**
     * Наливом входной заявки якорь не подменяется: пока эпизода нет,
     * себестоимости позиции не существует. Этим якорь отличается от базы
     * размещения (`U1.10`), которая факт налива как раз берёт.
     */
    @Test
    @DisplayName("U3.8 — у входной заявки средняя налива 2995, эпизода нет: якорь 3000 — плановая")
    void u3_8_theFillOfItsOwnLegDoesNotReplaceTheAnchor() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(entryOrder(LAST_PRICE, "2995"))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .as("2965.05 означало бы, что якорь взял среднюю налива")
                .isEqualByComparingTo("2970");
    }

    /** Факт старше плана: живой эпизод бьёт плановую цену ноги. */
    @Test
    @DisplayName("U3.9 — живой эпизод со средней 2990 при плановой 3000: якорь 2990 — факт старше плана")
    void u3_9_theFactOutranksThePlan() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(entryOrder(LAST_PRICE, null))
                .activePosition(liveEpisode("2990"))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2960.1");
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
