package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.ASK_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.BID_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.attachedStop;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.prices;
import static com.example.strategy.engine.unit.calc.CalcFixture.rulesWithTick;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Вход по рынку: размещения нет — группа `U2` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/components/PriceCalculator.md §«Что делает»).
 *
 * <p><b>Базовая сборка</b> — та же, что у `U1`, но размещение у действия
 * <b>не объявлено</b>: цена такой заявке не рассчитывается, а рыночный
 * ориентир нужен для размера и логов.
 *
 * <p><b>Сменяет часть прежней пробы предмета</b>
 * ({@code com.example.strategy.engine.calc.PriceLevelTest}): её
 * клетка-преемница здесь — `U2.5` (встроенная защита считается от цены
 * своей ноги).
 */
class MarketEntryPriceTest {

    private final PriceCalculator calculator = new PriceCalculator();

    /** Рыночный ориентир: на биржу цена не уезжает, защитные разделы пусты. */
    @Test
    @DisplayName("U2.1 — базовая сборка: режим MARKET_LIKE, все три цены 3000, на биржу цена не уезжает")
    void u2_1_theMarketReferenceDoesNotGoToTheExchange() {
        CalculatedPrice price = calculator.calculate(base(entryAction(null)).build());

        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.ORDER_MARKET_REFERENCE_PRICE);
        assertThat(price.getPriceMode()).isEqualTo(PriceMode.MARKET_LIKE);
        assertThat(price.getBasePrice()).isEqualByComparingTo("3000");
        assertThat(price.getRawPrice()).isEqualByComparingTo("3000");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3000");
        assertThat(price.getSendPriceToExchange()).isFalse();
        assertThat(price.getStopLossPrice()).as("стоп пуст").isNull();
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
    }

    /** Последней цены нет — ориентира не из чего взять. */
    @Test
    @DisplayName("U2.2 — последней цены в снапшоте нет: отказ NO_REFERENCE_PRICE")
    void u2_2_anAbsentLastPriceRefuses() {
        CalculationContext context = base(entryAction(null))
                .marketPriceData(prices(null, BID_PRICE, ASK_PRICE))
                .build();

        assertRefuses(context, "NO_REFERENCE_PRICE");
    }

    /** Снапшота нет вовсе — тот же отказ. */
    @Test
    @DisplayName("U2.3 — снапшота цен нет вовсе: отказ NO_REFERENCE_PRICE")
    void u2_3_anAbsentSnapshotRefuses() {
        CalculationContext context = base(entryAction(null))
                .marketPriceData(null)
                .build();

        assertRefuses(context, "NO_REFERENCE_PRICE");
    }

    /**
     * Рыночный ориентир по шагу не округляется, и правила инструмента на
     * этой тропе не читаются вовсе; §Округление дома при этом объявляет
     * округление <b>всех</b> цен — находка `F-7`.
     */
    @Test
    @DisplayName("U2.4 — шага цены нет: цена отдаётся без отказа — ориентир по шагу не округляется")
    void u2_4_theMarketReferenceIsNotRoundedByTheTick() {
        CalculationContext context = base(entryAction(null))
                .instrumentExternalRules(rulesWithTick(null))
                .build();

        assertThat(calculator.calculate(context).getRoundedPrice()).isEqualByComparingTo("3000");
    }

    /**
     * Встроенная защита считается от цены СВОЕЙ ноги — здесь от рыночного
     * ориентира этой же заявки, а не от цены соседнего действия.
     */
    @Test
    @DisplayName("U2.5 — вход со встроенной защитой долей 1: стоп 2970 — от цены своей ноги")
    void u2_5_theAttachedProtectionIsAnchoredAtItsOwnLegPrice() {
        CalculatedPrice price = calculator.calculate(attachedEntry(attachedStop("1")).build());

        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3000");
        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2970");
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
    }

    /** Блок защиты объявлен, настроек стопа в нём нет — отказ. */
    @Test
    @DisplayName("U2.6 — блок защиты объявлен, настроек стопа нет: отказ MISSING_STOP_LOSS_SETTINGS")
    void u2_6_anAttachedBlockWithoutStopSettingsRefuses() {
        StrategyAttachedProtectionSettings attached = new StrategyAttachedProtectionSettings();
        attached.setAttachedType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);

        assertRefuses(attachedEntry(attached).build(), "MISSING_STOP_LOSS_SETTINGS");
    }

    /** Блок встроенной защиты читается только у типа со встроенной защитой. */
    @Test
    @DisplayName("U2.7 — обычный вход с объявленным блоком защиты: стоп пуст, отказа нет")
    void u2_7_theAttachedBlockIsReadOnlyByItsOwnOrderType() {
        StrategyOrderAction action = entryAction(null);
        action.setAttachedProtection(attachedStop("1"));

        CalculatedPrice price = calculator.calculate(base(action).build());

        assertThat(price.getStopLossPrice()).as("блок не читается у обычного входа").isNull();
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3000");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private CalculationContext.CalculationContextBuilder attachedEntry(
            StrategyAttachedProtectionSettings attached) {
        StrategyOrderAction action = entryAction(null);
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setAttachedProtection(attached);
        return base(action);
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
