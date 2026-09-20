package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.episodeWithLiquidation;
import static com.example.tradingcore.unit.risk.RiskFixture.priceBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.reducingOnlyAction;
import static com.example.tradingcore.unit.risk.RiskFixture.takeProfit;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.withPrice;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сторона уровня фиксации прибыли и запас до ликвидации — группа
 * {@code U8} документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §Проверки, строки
 * {@code TAKE_PROFIT_INVALID_SIDE} и
 * {@code STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION}).
 *
 * <p><b>Базовая сборка</b> — U1.1, направление длинное. Клетки запаса
 * до ликвидации берут эпизод единичного размера и вход на пять
 * контрактов: при базовом размере живое слагаемое вместе с актом
 * перебирало бы одновременный потолок и подмешивало бы его код к
 * предмету группы.
 */
class TakeProfitAndLiquidationTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U8.1 — уровень фиксации прибыли выше якоря: отказа нет")
    void u8_1_aTakeProfitAboveTheAnchorPasses() {
        assertThat(codes(harness.validate(entryWithTakeProfit("3200"), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U8.2 — уровень фиксации прибыли ниже якоря: уровень прибыли на неверной стороне")
    void u8_2_aTakeProfitBelowTheAnchorIsRejected() {
        assertThat(codes(harness.validate(entryWithTakeProfit("2900"), workingContext())))
                .containsExactly(RiskCheckCode.TAKE_PROFIT_INVALID_SIDE);
    }

    @Test
    @DisplayName("U8.3 — уровень фиксации прибыли РАВЕН якорю: прибыли такой уровень не фиксирует")
    void u8_3_aTakeProfitExactlyAtTheAnchorIsRejected() {
        assertThat(codes(harness.validate(entryWithTakeProfit("3000"), workingContext())))
                .containsExactly(RiskCheckCode.TAKE_PROFIT_INVALID_SIDE);
    }

    @Test
    @DisplayName("U8.4 — короткое направление, уровень фиксации ниже якоря: отказа нет")
    void u8_4_aShortTakeProfitBelowTheAnchorPasses() {
        CalculatedStrategyAction shortEntry = withPrice(entryAction(),
                priceBuilder(ANCHOR, "3050").takeProfitPrice(takeProfit("2900")).build());
        Deal deal = emptyDeal();
        deal.setDirection(StrategyTradeDirection.SHORT);

        assertThat(codes(harness.validate(shortEntry, context(deal)))).isEmpty();
    }

    @Test
    @DisplayName("U8.5 — уровня фиксации прибыли нет: проверка не срабатывает ни в какую сторону")
    void u8_5_anAbsentTakeProfitIsSilent() {
        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U8.6 — уровень стопа ВЫШЕ цены ликвидации: стоп первым на пути к ней")
    void u8_6_aStopAheadOfTheLiquidationPricePasses() {
        assertThat(codes(harness.validate(smallEntry("2910"), episodeContext("2800")))).isEmpty();
    }

    @Test
    @DisplayName("U8.7 — уровень стопа ниже цены ликвидации: стоп за ценой ликвидации")
    void u8_7_aStopBeyondTheLiquidationPriceIsRejected() {
        assertThat(codes(harness.validate(smallEntry("2910"), episodeContext("2950"))))
                .containsExactly(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION);
    }

    @Test
    @DisplayName("U8.8 — уровень стопа РАВЕН цене ликвидации: запаса нет")
    void u8_8_aStopExactlyAtTheLiquidationPriceIsRejected() {
        assertThat(codes(harness.validate(smallEntry("2910"), episodeContext("2910"))))
                .containsExactly(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION);
    }

    @Test
    @DisplayName("U8.9 — короткое направление, уровень стопа выше цены ликвидации: отказ")
    void u8_9_aShortStopAboveTheLiquidationPriceIsRejected() {
        Deal deal = dealWith(episodeWithLiquidation("1", ANCHOR, "3000"));
        deal.setDirection(StrategyTradeDirection.SHORT);

        assertThat(codes(harness.validate(smallEntry("3050"), context(deal))))
                .containsExactly(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION);
    }

    @Test
    @DisplayName("U8.10 — эпизода нет либо цена ликвидации пуста: сверять не с чем")
    void u8_10_withoutALiquidationPriceTheGuardIsSilent() {
        assertThat(codes(harness.validate(smallEntry("2910"), workingContext())))
                .as("эпизода нет вовсе")
                .isEmpty();
        assertThat(codes(harness.validate(smallEntry("2910"), context(dealWith(episode("1", ANCHOR))))))
                .as("эпизод есть, цены ликвидации у него нет")
                .isEmpty();
    }

    @Test
    @DisplayName("U8.11 — эпизод есть, уровня у действия нет: отказа нет")
    void u8_11_withoutAnActLevelTheGuardIsSilent() {
        Deal deal = dealWith(episodeWithLiquidation("1", ANCHOR, "2950"));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));

        assertThat(codes(harness.validate(reducingOnlyAction(), context(deal)))).isEmpty();
    }

    /** Вход базовой сборки с названным уровнем фиксации прибыли. */
    private static CalculatedStrategyAction entryWithTakeProfit(String takeProfitPrice) {
        return withPrice(entryAction(), priceBuilder(ANCHOR, STOP.toPlainString())
                .takeProfitPrice(takeProfit(takeProfitPrice))
                .build());
    }

    /** Вход на пять контрактов: живое слагаемое вместе с ним укладывается в потолки. */
    private static CalculatedStrategyAction smallEntry(String stopPrice) {
        return entryAction("5", ANCHOR, stopPrice);
    }

    /** Контекст со сделкой, несущей эпизод единичного размера и названную цену ликвидации. */
    private static DealContext episodeContext(String liquidationPrice) {
        return context(dealWith(episodeWithLiquidation("1", ANCHOR, liquidationPrice)));
    }

    /** Сделка базовой сборки с названным эпизодом. */
    private static Deal dealWith(Position episode) {
        Deal deal = emptyDeal();
        deal.setPositions(List.of(episode));
        return deal;
    }
}
