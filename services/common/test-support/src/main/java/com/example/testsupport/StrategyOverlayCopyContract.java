package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.ObvParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Кейсы навеса дерева стратегии: группы `U4`, `U5`, `U6` и `U10.1`, `U10.2`
 * документа `.claude/tests/cases/jsonb-overlay-roundtrip.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии.</b> `StrategyJsonConverter` живёт двумя экземплярами —
 * {@code trading-core} и {@code strategies}, — а сличить их на одном
 * classpath нечем: деревья сервисов друг от друга не зависят. Форма пробы
 * поэтому общая, а порты ниже подставляют СВОЙ конвертер и СВОЮ строку
 * владельца (§«U10 — Тождество копий формы»).
 *
 * <p><b>Базовая сборка — маппер сборки бина</b> ({@link JsonbOverlayProbe});
 * свежий маппер здесь незаконен, и довод назван у сборки.
 *
 * <p><b>Выход сверяется рекурсивно по полям.</b> Оператора тождества у форм
 * навеса нет ни одного — наследуемый {@code equals} сравнивает ссылки, — а
 * сравнение строк слепо к полю, потерянному на записи: его нет в обеих
 * строках (§«Новая ось формы: выход кейса — ТОЖДЕСТВО пары»).
 */
public abstract class StrategyOverlayCopyContract extends JsonbOverlayProbe {

    /** Имена, под которыми тег подтипа мог бы поехать в payload. */
    private static final List<String> SUBTYPE_TAG_NAMES =
            List.of("@class", "@type", "type", "indicatorType", "paramsType");

    // --- порты к своей копии конвертера ---------------------------------

    protected abstract String writeIndicatorParams(IndicatorParams params);

    /** Чтение по строке-владельцу: тип индикатора — её колонка, тело — её навес. */
    protected abstract IndicatorParams readIndicatorParams(String indicatorType, String paramsJson);

    /** Чтение при отсутствующей строке-владельце. */
    protected abstract IndicatorParams readIndicatorParamsWithoutOwner();

    protected abstract String writeMarketStructureParams(MarketStructureParams params);

    protected abstract MarketStructureParams readMarketStructureParams(String json);

    protected abstract String writePhaseRules(List<StrategyMarketPhaseRule> rules);

    protected abstract List<StrategyMarketPhaseRule> readPhaseRules(String json);

    protected abstract String writeCondition(StrategyCondition condition);

    protected abstract StrategyCondition readCondition(String json);

    protected abstract String writeExpiredSetting(StrategyMarketDataExpiredSetting setting);

    protected abstract StrategyMarketDataExpiredSetting readExpiredSetting(String json);

    protected abstract String writePlacement(StrategyPricePlacement placement);

    protected abstract StrategyPricePlacement readPlacement(String json);

    protected abstract String writeAttachedProtection(StrategyAttachedProtectionSettings settings);

    protected abstract StrategyAttachedProtectionSettings readAttachedProtection(String json);

    protected abstract String writeStopLossSettings(StopLossSettings settings);

    protected abstract StopLossSettings readStopLossSettings(String json);

    protected abstract String writeTrailingSettings(TrailingSettings settings);

    protected abstract TrailingSettings readTrailingSettings(String json);

    /**
     * Та же копия, собранная на ЧУЖОМ маппере: кейс `U5.3` мерит, что
     * политику включения конструктор ставит безусловно.
     */
    protected abstract String writePlacementOn(ObjectMapper source, StrategyPricePlacement placement);

    // --- U4: дискриминатор у владельца и закрытый перечень подтипов ------

    @Test
    @DisplayName("U4.1 — тега подтипа в payload нет, подтип берётся у владельца")
    protected void u4_1_subtypeTagLivesOnTheOwnerAndNotInThePayload() {
        AtrParams params = atrParams();

        String json = writeIndicatorParams(params);

        assertThat(keysOf(json)).containsExactlyInAnyOrder("timeframe", "warmup", "period");
        assertThat(keysOf(json)).doesNotContainAnyElementsOf(SUBTYPE_TAG_NAMES);
        assertThat(readIndicatorParams("ATR", json))
                .isInstanceOf(AtrParams.class)
                .usingRecursiveComparison().isEqualTo(params);
    }

    @Test
    @DisplayName("U4.2 — сериализация идёт по рантайм-типу, а не по объявленному")
    protected void u4_2_serializationFollowsTheRuntimeTypeNotTheDeclaredOne() {
        IndicatorParams declaredAsBase = atrParams();

        assertThat(keysOf(writeIndicatorParams(declaredAsBase))).contains("period");
    }

    @Test
    @DisplayName("U4.3 — восемь подтипов, каждый при своём типе владельца; перечень закрыт")
    protected void u4_3_eightSubtypesEachUnderItsOwnOwnerType() {
        assertRoundTrip("ATR", atrParams(), AtrParams.class);
        assertRoundTrip("EMA", emaParams(), EmaParams.class);
        assertRoundTrip("RSI", rsiParams(), RsiParams.class);
        assertRoundTrip("MACD", macdParams(), MacdParams.class);
        assertRoundTrip("STOCHASTIC", stochasticParams(), StochasticParams.class);
        assertRoundTrip("BOLLINGER_BANDS", bollingerParams(), BollingerBandsParams.class);
        assertRoundTrip("OBV", obvParams(), ObvParams.class);
        assertRoundTrip("EFFICIENCY_RATIO", efficiencyRatioParams(), EfficiencyRatioParams.class);
        assertThat(IndicatorValue.Type.values())
                .as("перечень подтипов закрыт: выбор класса не имеет ветви умолчания, "
                        + "и девятое значение уронило бы компиляцию")
                .hasSize(8);
    }

    @Test
    @DisplayName("U4.4 — имя типа вне перечня отказывает ЧУЖИМ классом, а не своим")
    protected void u4_4_unknownOwnerTypeFailsWithTheAlienExceptionClass() {
        assertThatThrownBy(() -> readIndicatorParams("SUPERTREND", writeIndicatorParams(atrParams())))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("U4.5 — три формы пустоты строки-владельца дают пустоту без отказа")
    protected void u4_5_threeShapesOfAnEmptyOwnerYieldEmptiness() {
        assertThat(readIndicatorParamsWithoutOwner()).isNull();
        assertThat(readIndicatorParams("ATR", null)).isNull();
        assertThat(readIndicatorParams(null, writeIndicatorParams(atrParams()))).isNull();
    }

    @Test
    @DisplayName("U4.6 — тело чужого подтипа при своей колонке читается МОЛЧА")
    protected void u4_6_aBodyOfAnotherSubtypeUnderItsOwnColumnIsReadSilently() {
        String alienBody = writeIndicatorParams(macdParams());

        IndicatorParams read = readIndicatorParams("ATR", alienBody);

        assertThat(read).isInstanceOf(AtrParams.class);
        assertThat(read.getTimeframe()).isEqualTo(macdParams().getTimeframe());
        assertThat(read.getWarmup()).isEqualTo(macdParams().getWarmup());
        assertThat(((AtrParams) read).getPeriod())
                .as("поля, не совпавшие по имени, отброшены без отказа и без лога")
                .isNull();
    }

    @Test
    @DisplayName("U4.7 — тег подтипа в теле отбрасывается как неизвестное поле")
    protected void u4_7_aSubtypeTagInsideTheBodyIsDropped() {
        String withTag = "{\"@type\":\"ATR\",\"timeframe\":\"ONE_HOUR\",\"warmup\":50,\"period\":14}";

        assertThat(readIndicatorParams("ATR", withTag))
                .isInstanceOf(AtrParams.class)
                .usingRecursiveComparison().isEqualTo(atrParams());
    }

    // --- U5: конфигурация маппера навеса ---------------------------------

    @Test
    @DisplayName("U5.1 — пустое поле в строку не пишется и возвращается пустым")
    protected void u5_1_anEmptyFieldIsNotWrittenAndComesBackEmpty() {
        AtrParams params = atrParams();
        params.setPeriod(null);

        String json = writeIndicatorParams(params);

        assertThat(keysOf(json)).containsExactlyInAnyOrder("timeframe", "warmup");
        assertThat(readIndicatorParams("ATR", json))
                .usingRecursiveComparison().isEqualTo(params);
    }

    @Test
    @DisplayName("U5.2 — значение из пустых полей и пустое значение различимы")
    protected void u5_2_anAllEmptyValueIsNotTheSameAsAnAbsentValue() {
        String json = writeIndicatorParams(new AtrParams());

        assertThat(json).isEqualTo("{}");
        assertThat(readIndicatorParams("ATR", json))
                .isNotNull()
                .usingRecursiveComparison().isEqualTo(new AtrParams());
    }

    @Test
    @DisplayName("U5.3 — политика включения запинена конвертером, а не источником")
    protected void u5_3_theInclusionPolicyIsPinnedByTheConverterNotBySource() {
        ObjectMapper alwaysIncluding = beanAssemblyMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        StrategyPricePlacement placement = placement();
        placement.setStructureKey(null);

        assertThat(keysOf(writePlacementOn(alwaysIncluding, placement)))
                .doesNotContain("structureKey");
    }

    // --- U6: дерево условия, клаузы фазы, вложенные настройки ------------

    @Test
    @DisplayName("U6.1 — тождество дерева условия, включая порядок правил")
    protected void u6_1_theConditionTreeSurvivesWholeIncludingRuleOrder() {
        StrategyCondition condition = condition();

        StrategyCondition read = readCondition(writeCondition(condition));

        assertThat(read).usingRecursiveComparison().isEqualTo(condition);
        assertThat(read.getRules()).extracting(StrategyConditionRule::getLevel)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("U6.2 — пустой перечень правил остаётся пустым перечнем")
    protected void u6_2_anEmptyRuleListStaysAnEmptyList() {
        StrategyCondition condition = new StrategyCondition(List.of());

        StrategyCondition read = readCondition(writeCondition(condition));

        assertThat(read.getRules()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U6.3 — клаузы фазы восстанавливаются элементами объявленного типа")
    protected void u6_3_phaseClausesComeBackAsElementsOfTheDeclaredType() {
        List<StrategyMarketPhaseRule> rules = phaseRules();

        List<StrategyMarketPhaseRule> read = readPhaseRules(writePhaseRules(rules));

        assertThat(read).hasOnlyElementsOfType(StrategyMarketPhaseRule.class);
        assertThat(read).usingRecursiveComparison().isEqualTo(rules);
        assertThat(read).extracting(StrategyMarketPhaseRule::getType)
                .containsExactly(MarketPhase.Type.BULL_TREND, MarketPhase.Type.RANGE,
                        MarketPhase.Type.BEAR_TREND);
    }

    @Test
    @DisplayName("U6.4 — четыре формы действия читаются каждая своим классом")
    protected void u6_4_fourActionShapesAreNotConfusedWithEachOther() {
        StrategyPricePlacement placement = placement();
        StrategyAttachedProtectionSettings attached = attachedProtection();
        StopLossSettings stopLoss = stopLossSettings();
        TrailingSettings trailing = trailingSettings();

        assertThat(readPlacement(writePlacement(placement)))
                .usingRecursiveComparison().isEqualTo(placement);
        assertThat(readAttachedProtection(writeAttachedProtection(attached)))
                .usingRecursiveComparison().isEqualTo(attached);
        assertThat(readStopLossSettings(writeStopLossSettings(stopLoss)))
                .usingRecursiveComparison().isEqualTo(stopLoss);
        assertThat(readTrailingSettings(writeTrailingSettings(trailing)))
                .usingRecursiveComparison().isEqualTo(trailing);
    }

    @Test
    @DisplayName("U6.5 — источник операнда вне перечня отказывает своим классом с причиной")
    protected void u6_5_anUnknownOperandSourceFailsWithTheOwnClassAndKeepsTheCause() {
        String json = "{\"rules\":[{\"leftOperand\":{\"sourceType\":\"ORACLE\"}}]}";

        assertThatThrownBy(() -> readCondition(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deserialization")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U6.6 — пустой объект даёт ПУСТОТУ перечня, а не пустой перечень")
    protected void u6_6_anEmptyObjectYieldsAnAbsentListNotAnEmptyOne() {
        StrategyCondition read = readCondition("{}");

        assertThat(read).isNotNull();
        assertThat(read.getRules()).isNull();
    }

    @Test
    @DisplayName("U6.7 — оба рода реакции на устаревание уезжают именами значений")
    protected void u6_7_bothExpirationReactionsTravelAsEnumNames() {
        StrategyMarketDataExpiredSetting setting = new StrategyMarketDataExpiredSetting(
                MarketDataExpiredAction.WAIT, MarketDataExpiredAction.KILL_SWITCH);

        String json = writeExpiredSetting(setting);

        assertThat(json).contains("\"WAIT\"", "\"KILL_SWITCH\"");
        assertThat(readExpiredSetting(json)).usingRecursiveComparison().isEqualTo(setting);
    }

    @Test
    @DisplayName("U6.8 — параметры структуры рынка: своя пара методов, иерархии нет")
    protected void u6_8_marketStructureParamsAreNotASubtypeAndCarryNoTag() {
        MarketStructureParams params = marketStructureParams();

        String json = writeMarketStructureParams(params);

        assertThat(keysOf(json))
                .containsExactlyInAnyOrder("lookbackBars", "minTouches", "minRangeWidthPercents");
        assertThat(keysOf(json)).doesNotContainAnyElementsOf(SUBTYPE_TAG_NAMES);
        assertThat(readMarketStructureParams(json))
                .usingRecursiveComparison().isEqualTo(params);
    }

    @Test
    @DisplayName("U6.9 — пустота зеркальна и у пары структуры рынка")
    protected void u6_9_emptinessIsMirroredForTheMarketStructurePairToo() {
        assertThat(writeMarketStructureParams(null)).isNull();
        assertThat(readMarketStructureParams(null)).isNull();
    }

    // --- U10: тождество копий --------------------------------------------

    @Test
    @DisplayName("U10.1 — строка навеса пары совпадает с объявленной один раз")
    protected void u10_1_bothCopiesWriteTheStringDeclaredOnce() {
        AtrParams withEmptyField = atrParams();
        withEmptyField.setPeriod(null);

        assertThat(writeIndicatorParams(atrParams()))
                .isEqualTo("{\"timeframe\":\"ONE_HOUR\",\"warmup\":50,\"period\":14}");
        assertThat(writeIndicatorParams(withEmptyField))
                .isEqualTo("{\"timeframe\":\"ONE_HOUR\",\"warmup\":50}");
        assertThat(writeCondition(condition())).isEqualTo(CONDITION_JSON);
        assertThat(writeMarketStructureParams(marketStructureParams()))
                .isEqualTo("{\"lookbackBars\":120,\"minTouches\":3,\"minRangeWidthPercents\":0.5}");
    }

    @Test
    @DisplayName("U10.2 — обе копии отказывают одним классом на одном входе")
    protected void u10_2_bothCopiesFailWithTheSameClassOnTheSameInput() {
        assertThatThrownBy(() -> readIndicatorParams("SUPERTREND", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPERTREND");
    }

    // --- материал кейсов ---------------------------------------------------

    /** Строка `U10.1`: объявлена один раз и сверяется каждой копией. */
    private static final String CONDITION_JSON =
            "{\"rules\":["
                    + "{\"level\":1,\"ruleType\":\"INDICATOR_COMPARE\",\"timeframe\":\"ONE_HOUR\","
                    + "\"operator\":\"GT\","
                    + "\"leftOperand\":{\"sourceType\":\"INDICATOR\",\"indicatorKey\":\"ema_fast\","
                    + "\"indicatorComponent\":\"MIDDLE_BAND\"},"
                    + "\"rightOperand\":{\"sourceType\":\"PRICE\",\"priceSource\":\"LAST_PRICE\"}},"
                    + "{\"level\":2,\"ruleType\":\"PRICE_COMPARE\",\"timeframe\":\"FIVE_MINUTES\","
                    + "\"operator\":\"LT\","
                    + "\"leftOperand\":{\"sourceType\":\"MARKET_STRUCTURE\",\"structureKey\":\"range_main\"},"
                    + "\"rightOperand\":{\"sourceType\":\"PRICE\",\"valueType\":\"NUMBER\",\"value\":\"42\"}}"
                    + "]}";

    private void assertRoundTrip(String ownerType, IndicatorParams params,
                                 Class<? extends IndicatorParams> expected) {
        IndicatorParams read = readIndicatorParams(ownerType, writeIndicatorParams(params));

        assertThat(read).isInstanceOf(expected);
        assertThat(read).usingRecursiveComparison().isEqualTo(params);
    }

    private static AtrParams atrParams() {
        AtrParams params = new AtrParams(14);
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(50);
        return params;
    }

    private static EmaParams emaParams() {
        EmaParams params = new EmaParams(21);
        params.setTimeframe(TimeFrame.FIVE_MINUTES);
        params.setWarmup(60);
        return params;
    }

    private static RsiParams rsiParams() {
        RsiParams params = new RsiParams(14);
        params.setTimeframe(TimeFrame.FIFTEEN_MINUTES);
        params.setWarmup(30);
        return params;
    }

    private static MacdParams macdParams() {
        MacdParams params = new MacdParams(12, 26, 9);
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(50);
        return params;
    }

    private static StochasticParams stochasticParams() {
        StochasticParams params = new StochasticParams(14, 3, 3);
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(40);
        return params;
    }

    private static BollingerBandsParams bollingerParams() {
        BollingerBandsParams params = new BollingerBandsParams(20, new BigDecimal("2.0"));
        params.setTimeframe(TimeFrame.FOUR_HOURS);
        params.setWarmup(45);
        return params;
    }

    private static ObvParams obvParams() {
        ObvParams params = new ObvParams(Boolean.TRUE);
        params.setTimeframe(TimeFrame.ONE_DAY);
        params.setWarmup(10);
        return params;
    }

    private static EfficiencyRatioParams efficiencyRatioParams() {
        EfficiencyRatioParams params = new EfficiencyRatioParams(10);
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(20);
        return params;
    }

    private static MarketStructureParams marketStructureParams() {
        MarketStructureParams params = new MarketStructureParams();
        params.setLookbackBars(120);
        params.setMinTouches(3);
        params.setMinRangeWidthPercents(new BigDecimal("0.5"));
        return params;
    }

    private static StrategyCondition condition() {
        StrategyConditionOperand indicator = new StrategyConditionOperand();
        indicator.setSourceType(StrategyConditionSourceType.INDICATOR);
        indicator.setIndicatorKey("ema_fast");
        indicator.setIndicatorComponent(IndicatorComponent.MIDDLE_BAND);

        StrategyConditionOperand price = new StrategyConditionOperand();
        price.setSourceType(StrategyConditionSourceType.PRICE);
        price.setPriceSource(StrategyPriceSource.LAST_PRICE);

        StrategyConditionRule first = new StrategyConditionRule();
        first.setLevel(1);
        first.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        first.setTimeframe(TimeFrame.ONE_HOUR);
        first.setOperator(StrategyConditionOperator.GT);
        first.setLeftOperand(indicator);
        first.setRightOperand(price);

        StrategyConditionOperand structure = new StrategyConditionOperand();
        structure.setSourceType(StrategyConditionSourceType.MARKET_STRUCTURE);
        structure.setStructureKey("range_main");

        StrategyConditionOperand constant = new StrategyConditionOperand();
        constant.setSourceType(StrategyConditionSourceType.PRICE);
        constant.setValueType(ConstantValueType.NUMBER);
        constant.setValue("42");

        StrategyConditionRule second = new StrategyConditionRule();
        second.setLevel(2);
        second.setRuleType(StrategyConditionRuleType.PRICE_COMPARE);
        second.setTimeframe(TimeFrame.FIVE_MINUTES);
        second.setOperator(StrategyConditionOperator.LT);
        second.setLeftOperand(structure);
        second.setRightOperand(constant);

        return new StrategyCondition(List.of(first, second));
    }

    private static List<StrategyMarketPhaseRule> phaseRules() {
        return List.of(
                new StrategyMarketPhaseRule(MarketPhase.Type.BULL_TREND, condition()),
                new StrategyMarketPhaseRule(MarketPhase.Type.RANGE, new StrategyCondition(List.of())),
                new StrategyMarketPhaseRule(MarketPhase.Type.BEAR_TREND, condition()));
    }

    private static StrategyPricePlacement placement() {
        StrategyPricePlacement placement = new StrategyPricePlacement();
        placement.setBaseType(StrategyPriceBaseType.RANGE_LOW);
        placement.setPriceSource(StrategyPriceSource.LAST_PRICE);
        placement.setStructureKey("range_main");
        placement.setOffsetSide(StrategyPriceOffsetSide.ABOVE);
        placement.setPercents(new BigDecimal("0.25"));
        return placement;
    }

    private static StrategyAttachedProtectionSettings attachedProtection() {
        return new StrategyAttachedProtectionSettings(
                AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS, stopLossSettings());
    }

    private static StopLossSettings stopLossSettings() {
        return new StopLossSettings(StopLossCalculationType.ATR_PERCENT, new BigDecimal("1.5"),
                AlgoOrder.TriggerPriceType.MARK, "atr_main", "range_main");
    }

    private static TrailingSettings trailingSettings() {
        return new TrailingSettings(new BigDecimal("2.0"), new BigDecimal("0.4"),
                new BigDecimal("0.1"));
    }

}
