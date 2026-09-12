package com.example.strategy.engine.calc;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Уровни действия — правки, которыми расчёт цены отличается от донорского
 * слоя, и охраны, ради которых они сделаны
 * (docs/components/PriceCalculator.md, docs/spec/stop-distance.json).
 *
 * <p><b>Состояние собирается настоящими полями</b> — объявлением
 * действия, снимком цен, живым эпизодом с наблюдённой средней, — а не
 * подменёнными предикатами (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»): признак живого риска эпизода поэтому вытекает из его статуса
 * и размера.
 */
class PriceLevelTest {

    private static final BigDecimal LAST_PRICE = new BigDecimal("3000");

    private final PriceCalculator calculator = new PriceCalculator();

    /**
     * <b>Марк-цена — отказ, а не подстановка последней.</b> Снапшот
     * рыночных цен её не несёт, и подстановка соседней цены сменила бы
     * ценовой домен молча. Донорский слой здесь проксировал последнюю —
     * это и есть правка порта.
     */
    @Test
    void markPriceSourceRefusesInsteadOfProxyingTheLastPrice() {
        StrategyOrderAction action = limitEntry(StrategyPriceSource.MARK_PRICE);

        assertThatThrownBy(() -> calculator.calculate(context(action, null)))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("PRICE_SOURCE_UNAVAILABLE");
    }

    /** Индексная цена — тот же отказ и по той же причине. */
    @Test
    void indexPriceSourceRefusesToo() {
        StrategyOrderAction action = limitEntry(StrategyPriceSource.INDEX_PRICE);

        assertThatThrownBy(() -> calculator.calculate(context(action, null)))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("PRICE_SOURCE_UNAVAILABLE");
    }

    /** Последняя цена источником объявлена и отдаётся снапшотом — она проходит. */
    @Test
    void lastPriceSourceIsResolvedFromTheSnapshot() {
        StrategyOrderAction action = limitEntry(StrategyPriceSource.LAST_PRICE);

        CalculatedPrice price = calculator.calculate(context(action, null));

        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2970");
        assertThat(price.getPriceMode()).isEqualTo(PriceMode.EXPLICIT);
    }

    /**
     * Безубыток решается ТОЧНО, а не линейным приближением: якорь ×
     * (1 + f)/(1 − f) для длинной позиции. При якоре 3000 и ставке 0.0005
     * это 3003.0015…, что по шагу 0.1 прочь от якоря даёт 3003.1;
     * приближение 3000 × (1 + 2f) дало бы 3003 ровно.
     */
    @Test
    void breakevenLevelIsSolvedExactlyNotApproximated() {
        StrategyAlgoOrderAction action = protectiveTransfer(StopLossCalculationType.BREAKEVEN, null);

        CalculatedPrice price = calculator.calculate(context(action, liveEpisode("3000")));

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("3003.1");
        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.BREAKEVEN_PRICE);
    }

    /**
     * Якорь уровня — ФАКТИЧЕСКАЯ средняя цена живого эпизода, а не
     * плановая цена ноги: на проскоке плановая смещала бы контроль в
     * разрешающую сторону.
     */
    @Test
    void levelAnchorIsTheObservedAverageOfTheLiveEpisode() {
        StrategyAlgoOrderAction action = protectiveTransfer(StopLossCalculationType.ENTRY_PRICE_PERCENT, "1");

        CalculatedPrice price = calculator.calculate(context(action, liveEpisode("2800")));

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2772");
    }

    /**
     * Живой эпизод есть, а средняя ещё не наблюдена — расчёт ОТКАЗЫВАЕТ:
     * откат к плановой цене был бы благоприятным умолчанием на
     * непредъявленном факте (docs/concept.md, П1 §4).
     */
    @Test
    void liveEpisodeWithoutAnObservedAverageRefusesInsteadOfFallingBack() {
        StrategyAlgoOrderAction action = protectiveTransfer(StopLossCalculationType.ENTRY_PRICE_PERCENT, "1");

        assertThatThrownBy(() -> calculator.calculate(context(action, liveEpisode(null))))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("ENTRY_ANCHOR_UNAVAILABLE");
    }

    /** Пока живого эпизода нет, якорь — плановая цена своей ноги. */
    @Test
    void withoutALiveEpisodeTheAnchorIsThePlannedLegPrice() {
        StrategyAlgoOrderAction action = protectiveTransfer(StopLossCalculationType.ENTRY_PRICE_PERCENT, "1");
        CalculationContext context = contextBuilder(action, null)
                .entryOrder(plannedLeg("2500"))
                .build();

        CalculatedPrice price = calculator.calculate(context);

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2475");
    }

    /**
     * Ставка не резолвится — безубыток отказывает, а не подставляет цену
     * входа: уровень, равный входу, оставляет сделке убыток ровно в размер
     * round-trip комиссии, и ошибка эта всегда против сделки.
     */
    @Test
    void breakevenWithoutAFeeRateRefusesInsteadOfUsingTheEntryPrice() {
        StrategyAlgoOrderAction action = protectiveTransfer(StopLossCalculationType.BREAKEVEN, null);
        CalculationContext context = contextBuilder(action, liveEpisode("3000"))
                .instrumentExternalRules(rules(null))
                .build();

        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(error -> ((CalculationException) error).getError().getCode())
                .isEqualTo("FEE_RATE_UNAVAILABLE");
    }

    /**
     * Защитный уровень округляется ПРОЧЬ ОТ ЯКОРЯ: стоп под якорем не
     * подтягивается ближе. Один процент от 2800 даёт 2772 ровно, поэтому
     * сдвиг проверяется на дробном якоре: 2777.77 × 0.99 = 2749.9923 → 2749.9.
     */
    @Test
    void protectiveLevelIsRoundedAwayFromTheAnchor() {
        StrategyAlgoOrderAction action = protectiveTransfer(StopLossCalculationType.ENTRY_PRICE_PERCENT, "1");

        CalculatedPrice price = calculator.calculate(context(action, liveEpisode("2777.77")));

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2749.9");
    }

    /** Встроенная защита входа считается от цены СВОЕЙ ноги, а не от рыночного ориентира. */
    @Test
    void attachedProtectionIsAnchoredAtItsOwnLegPrice() {
        StrategyOrderAction action = limitEntry(StrategyPriceSource.LAST_PRICE);
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setAttachedProtection(attachedStop("1"));

        CalculatedPrice price = calculator.calculate(context(action, null));

        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2970");
        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2940.3");
    }

    // --- сборка состояния -------------------------------------------------

    private static CalculationContext context(StrategyAction action, Position activePosition) {
        return contextBuilder(action, activePosition).build();
    }

    private static CalculationContext.CalculationContextBuilder contextBuilder(StrategyAction action,
                                                                               Position activePosition) {
        MarketPriceData priceData = new MarketPriceData();
        priceData.setExternalLastPrice(LAST_PRICE);
        priceData.setExternalBidPrice(new BigDecimal("2999"));
        priceData.setExternalAskPrice(new BigDecimal("3001"));
        return CalculationContext.builder()
                .action(action)
                .instrumentExternalRules(rules("0.0005"))
                .marketPriceData(priceData)
                .activePosition(activePosition)
                .strategyDirection(StrategyTradeDirection.LONG);
    }

    private static InstrumentExternalRules rules(String feeRate) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalTickSize("0.1");
        rules.setExternalContractValue("0.1");
        rules.setExternalLotSize("1");
        rules.setExternalMinSize("1");
        rules.setExternalTakerFeeRate(feeRate);
        return rules;
    }

    private static StrategyOrderAction limitEntry(StrategyPriceSource priceSource) {
        StrategyPricePlacement placement = new StrategyPricePlacement();
        placement.setBaseType(StrategyPriceBaseType.MARKET_PRICE);
        placement.setPriceSource(priceSource);
        placement.setOffsetSide(StrategyPriceOffsetSide.BELOW);
        placement.setPercents(new BigDecimal("1"));

        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(1L);
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY);
        action.setPlacement(placement);
        action.setAllocationPercents(new BigDecimal("100"));
        return action;
    }

    private static StrategyAttachedProtectionSettings attachedStop(String distancePercents) {
        StopLossSettings settings = new StopLossSettings();
        settings.setCalculationType(StopLossCalculationType.ENTRY_PRICE_PERCENT);
        settings.setDistancePercents(new BigDecimal(distancePercents));
        settings.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);

        StrategyAttachedProtectionSettings attached = new StrategyAttachedProtectionSettings();
        attached.setAttachedType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        attached.setStopLossSettings(settings);
        return attached;
    }

    private static StrategyAlgoOrderAction protectiveTransfer(StopLossCalculationType calculationType,
                                                              String distancePercents) {
        StopLossSettings settings = new StopLossSettings();
        settings.setCalculationType(calculationType);
        settings.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        if (nonNull(distancePercents)) {
            settings.setDistancePercents(new BigDecimal(distancePercents));
        }

        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(2L);
        action.setKey("protection");
        action.setActionType(StrategyActionType.REPLACE_ACTION);
        action.setTargetActionKey("entry");
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(settings);
        action.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        return action;
    }

    private static Position liveEpisode(String observedAverage) {
        Position position = new Position();
        position.setId(5L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal("10"));
        if (nonNull(observedAverage)) {
            position.setExternalAverageEntryPrice(new BigDecimal(observedAverage));
        }
        return position;
    }

    private static Order plannedLeg(String plannedPrice) {
        Order order = new Order();
        order.setId(9L);
        order.setPrice(new BigDecimal(plannedPrice));
        return order;
    }
}
