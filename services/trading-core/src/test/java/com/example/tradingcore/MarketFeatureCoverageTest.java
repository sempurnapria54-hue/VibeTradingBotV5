package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingcore.domain.market.MarketFeatures;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Гейт покрытия операндов шага снятой раскладкой — по ОБЕИМ половинам
 * сравнения (docs/rules/market-data-freshness.md, носитель гейта у
 * читателя).
 *
 * <p><b>Предмет — правило, читающее прошлое.</b> Шаг с пересечением либо
 * объёмным фильтром, у которого последние значения есть, а предыдущих нет,
 * до оценки не доходит: иначе он оказывался бы ложным из-за половины,
 * которую гейт не мерил, и «условие не выполнено» было бы неотличимо от
 * «операнда не было».
 *
 * <p>Фичи и условие собираются настоящими полями; предикат считается сам
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class MarketFeatureCoverageTest {

    private static final String FAST = "fast";
    private static final String SLOW = "slow";
    private static final String VOLUME = "obv";

    /** Обе половины пересечения есть — шаг покрыт. */
    @Test
    void aCrossoverWithBothHalvesIsCovered() {
        MarketFeatures features = features(Map.of(FAST, ema("11"), SLOW, ema("10")),
                Map.of(FAST, ema("9"), SLOW, ema("10")));

        assertThat(features.covers(crossover())).isTrue();
    }

    /** У одного операнда пересечения нет предыдущего значения — шаг не покрыт. */
    @Test
    void aCrossoverMissingOnePreviousValueIsNotCovered() {
        MarketFeatures features = features(Map.of(FAST, ema("11"), SLOW, ema("10")),
                Map.of(FAST, ema("9")));

        assertThat(features.covers(crossover())).isFalse();
    }

    /** Предыдущих значений нет вовсе — то, что даёт владелец на первой свече ряда. */
    @Test
    void aCrossoverWithoutAnyPreviousValuesIsNotCovered() {
        MarketFeatures features = MarketFeatures.builder()
                .latestIndicators(Map.of(FAST, ema("11"), SLOW, ema("10")))
                .structures(Map.of())
                .build();

        assertThat(features.covers(crossover())).isFalse();
    }

    /** Объёмный фильтр читает прошлое левого операнда — без него шаг не покрыт. */
    @Test
    void aVolumeFilterWithoutItsPreviousValueIsNotCovered() {
        MarketFeatures features = features(Map.of(VOLUME, obv("200")), Map.of());

        assertThat(features.covers(volumeFilter())).isFalse();
        assertThat(features(Map.of(VOLUME, obv("200")), Map.of(VOLUME, obv("150")))
                .covers(volumeFilter())).isTrue();
    }

    /**
     * Сравнение с текущим значением прошлого не читает: пустая раскладка
     * предыдущих его покрытия не снимает — иначе гейт отказывал бы шагам,
     * которым вторая половина не нужна.
     */
    @Test
    void aPlainCompareDoesNotAskForThePreviousHalf() {
        MarketFeatures features = features(Map.of(FAST, ema("11")), Map.of());

        assertThat(features.covers(compare())).isTrue();
    }

    /**
     * Пересечение цены с индикатором читает и прошлое цены — по ключу
     * индикатора-пары. Его нет — шаг не покрыт, хотя цена момента и обе
     * половины индикатора на месте; есть — покрыт.
     */
    @Test
    void aPriceCrossoverNeedsThePastPriceUnderTheIndicatorPairKey() {
        MarketPriceData prices = new MarketPriceData();
        prices.setExternalLastPrice(new BigDecimal("11"));
        MarketFeatures.MarketFeaturesBuilder withoutPastPrice = MarketFeatures.builder()
                .latestIndicators(Map.of(SLOW, ema("10")))
                .previousIndicators(Map.of(SLOW, ema("10")))
                .structures(Map.of())
                .marketPriceData(prices);

        assertThat(withoutPastPrice.build().covers(priceCrossover())).isFalse();
        assertThat(withoutPastPrice.previousPrices(Map.of(FAST, new BigDecimal("9"))).build()
                .covers(priceCrossover())).isFalse();
        assertThat(withoutPastPrice.previousPrices(Map.of(SLOW, new BigDecimal("9"))).build()
                .covers(priceCrossover())).isTrue();
    }

    private static MarketFeatures features(Map<String, IndicatorValue> latest,
                                           Map<String, IndicatorValue> previous) {
        return MarketFeatures.builder()
                .latestIndicators(latest)
                .previousIndicators(previous)
                .structures(Map.of())
                .build();
    }

    private static StrategyCondition crossover() {
        return condition(rule(StrategyConditionRuleType.CROSSOVER, StrategyConditionOperator.CROSSED_ABOVE,
                indicator(FAST), indicator(SLOW)));
    }

    private static StrategyCondition priceCrossover() {
        StrategyConditionOperand price = new StrategyConditionOperand();
        price.setSourceType(StrategyConditionSourceType.PRICE);
        return condition(rule(StrategyConditionRuleType.CROSSOVER, StrategyConditionOperator.CROSSED_ABOVE,
                price, indicator(SLOW)));
    }

    private static StrategyCondition volumeFilter() {
        return condition(rule(StrategyConditionRuleType.VOLUME_FILTER_PASSED, null, indicator(VOLUME), null));
    }

    private static StrategyCondition compare() {
        StrategyConditionOperand constant = new StrategyConditionOperand();
        constant.setSourceType(StrategyConditionSourceType.CONSTANT);
        constant.setValueType(ConstantValueType.NUMBER);
        constant.setValue("0");
        return condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE, StrategyConditionOperator.GT,
                indicator(FAST), constant));
    }

    private static StrategyCondition condition(StrategyConditionRule rule) {
        return new StrategyCondition(new ArrayList<>(List.of(rule)));
    }

    private static StrategyConditionRule rule(StrategyConditionRuleType type, StrategyConditionOperator operator,
                                              StrategyConditionOperand left, StrategyConditionOperand right) {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(type);
        rule.setOperator(operator);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return rule;
    }

    private static StrategyConditionOperand indicator(String key) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.INDICATOR);
        operand.setIndicatorKey(key);
        return operand;
    }

    private static EmaValue ema(String value) {
        EmaValue ema = new EmaValue();
        ema.setEma(new BigDecimal(value));
        return ema;
    }

    private static ObvValue obv(String value) {
        ObvValue obv = new ObvValue();
        obv.setObv(new BigDecimal(value));
        return obv;
    }
}
