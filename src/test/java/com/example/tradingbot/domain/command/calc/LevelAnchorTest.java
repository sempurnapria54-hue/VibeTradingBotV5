package com.example.tradingbot.domain.command.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает якорь уровня с его домом (docs/spec/stop-distance.json,
 * величина {@code entryAnchor}).
 *
 * <p>Несущее: якорь есть ВЕТВЬ, а не coalesce. Живой эпизод с ещё не
 * наблюдённой средней ценой даёт ОТКАЗ ВЫЧИСЛЕНИЯ — откат к плановой цене
 * был бы благоприятным умолчанием на непредъявленном факте и смещал бы
 * контроль в разрешающую сторону тем сильнее, чем больше проскок.
 */
class LevelAnchorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    @DisplayName("Живой эпизод есть — якорь фактическая средняя цена, не плановая и не рыночная")
    void anchorIsObservedAverageWhenEpisodeLive() {
        CalculationContext context = context(livePosition(new BigDecimal("110")), plannedEntry(new BigDecimal("100")));

        CalculatedPrice price = calculator.calculate(context);

        // Стоп на 10 % ниже якоря: 110 * 0.9 = 99, а не 90 от плановой цены.
        assertEquals(new BigDecimal("99.0"), price.getStopLossPrice().getTriggerPrice());
    }

    @Test
    @DisplayName("Живого эпизода нет — якорь плановая цена своей ноги")
    void anchorIsPlannedPriceWithoutEpisode() {
        CalculationContext context = context(null, plannedEntry(new BigDecimal("100")));

        CalculatedPrice price = calculator.calculate(context);

        assertEquals(new BigDecimal("90.0"), price.getStopLossPrice().getTriggerPrice());
    }

    @Test
    @DisplayName("Налив входной заявки якоря не заменяет: пока эпизода нет, себестоимости не существует")
    void filledEntryDoesNotReplaceAnchor() {
        Order entry = plannedEntry(new BigDecimal("100"));
        entry.setAveragePrice(new BigDecimal("104"));

        CalculatedPrice price = calculator.calculate(context(null, entry));

        assertEquals(new BigDecimal("90.0"), price.getStopLossPrice().getTriggerPrice());
    }

    @Test
    @DisplayName("Эпизод есть, средняя не наблюдена — расчёт отказывает, а не откатывается к плановой")
    void unobservedAverageRefusesComputation() {
        CalculationContext context = context(livePosition(null), plannedEntry(new BigDecimal("100")));

        CalculationException failure = assertThrows(CalculationException.class,
                () -> calculator.calculate(context));

        assertEquals("ENTRY_ANCHOR_UNAVAILABLE", failure.getError().getCode());
    }

    @Test
    @DisplayName("Безубыток считается точной формой от якоря и ставки комиссии")
    void breakevenLevelFromAnchorAndFeeRate() {
        // LONG, якорь 100, ставка 0.0005: S = 100 * 1.0005 / 0.9995 = 100.1000...,
        // округление ПРОЧЬ ОТ ЯКОРЯ (вверх) — уровень не опускается ниже
        // истинного безубытка (docs/spec/stop-distance.json, breakevenLevel).
        CalculationContext context = breakevenContext("0.0005", new BigDecimal("100"));

        CalculatedPrice price = calculator.calculate(context);

        assertEquals(new BigDecimal("100.2"), price.getStopLossPrice().getTriggerPrice());
    }

    @Test
    @DisplayName("Ставка комиссии не резолвится — безубыток отказывает вычислением, а не берёт цену входа")
    void breakevenRefusesWithoutFeeRate() {
        CalculationContext context = breakevenContext(null, new BigDecimal("100"));

        CalculationException failure = assertThrows(CalculationException.class,
                () -> calculator.calculate(context));

        assertEquals("FEE_RATE_UNAVAILABLE", failure.getError().getCode());
    }

    /** Действие переноса уровня в безубыток на живом эпизоде с наблюдённой средней. */
    private CalculationContext breakevenContext(String takerFeeRate, BigDecimal averageEntryPrice) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        StopLossSettings settings = new StopLossSettings();
        settings.setCalculationType(StopLossCalculationType.BREAKEVEN);
        action.setStopLossSettings(settings);

        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalTickSize("0.1");
        rules.setExternalTakerFeeRate(takerFeeRate);

        MarketPriceData priceData = new MarketPriceData();
        priceData.setExternalLastPrice(new BigDecimal("500"));

        return CalculationContext.builder()
                .action(action)
                .strategyDirection(StrategyTradeDirection.LONG)
                .instrumentExternalRules(rules)
                .marketPriceData(priceData)
                .activePosition(livePosition(averageEntryPrice))
                .entryOrder(plannedEntry(new BigDecimal("100")))
                .build();
    }

    private Position livePosition(BigDecimal averageEntryPrice) {
        Position position = new Position();
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(BigDecimal.ONE);
        position.setExternalAverageEntryPrice(averageEntryPrice);
        return position;
    }

    private Order plannedEntry(BigDecimal price) {
        Order entry = new Order();
        entry.setPrice(price);
        return entry;
    }

    private CalculationContext context(Position position, Order entryOrder) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        StopLossSettings settings = new StopLossSettings();
        settings.setCalculationType(StopLossCalculationType.ENTRY_PRICE_PERCENT);
        settings.setDistancePercents(BigDecimal.TEN);
        action.setStopLossSettings(settings);

        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalTickSize("0.1");

        MarketPriceData priceData = new MarketPriceData();
        priceData.setExternalLastPrice(new BigDecimal("500"));

        return CalculationContext.builder()
                .action(action)
                .strategyDirection(StrategyTradeDirection.LONG)
                .instrumentExternalRules(rules)
                .marketPriceData(priceData)
                .activePosition(position)
                .entryOrder(entryOrder)
                .build();
    }
}
