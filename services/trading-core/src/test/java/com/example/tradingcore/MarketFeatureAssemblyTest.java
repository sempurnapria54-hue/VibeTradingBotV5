package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.integration.MarketDataReadClient;
import com.example.tradingcore.integration.model.IndicatorValueResponse;
import com.example.tradingcore.integration.model.MarketFeatureBinding;
import com.example.tradingcore.integration.model.MarketFeatureBundleResponse;
import com.example.tradingcore.integration.model.MarketFeatureReadRequest;
import com.example.tradingcore.integration.model.MarketStructureResponse;
import com.example.tradingcore.mapping.MarketFeatureMapper;
import com.example.tradingcore.mapping.MarketFeatureMapperImpl;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Чтение фич момента у владельца данных и сборка контекста оценки.
 *
 * <p><b>Предмет теста — вторая половина сравнения.</b> Правила пересечения
 * и объёмный фильтр читают ПРЕДЫДУЩЕЕ значение индикатора; читатель, у
 * которого его нет, вычисляет такое правило на пустом операнде и получает
 * ложь — то есть объявленный автором шаг не исполняется <b>молча</b>, без
 * отказа и без записи в журнале. Поэтому проверяется не наличие поля, а
 * исход предиката на собранном контексте.
 */
class MarketFeatureAssemblyTest {

    private static final String INSTRUMENT_INTERNAL_ID = "in-0001";
    private static final String FAST = "ema-fast";
    private static final String SLOW = "ema-slow";
    private static final String STRUCTURE = "range";

    private final MarketDataReadClient readClient = mock(MarketDataReadClient.class);
    private final MarketFeatureMapper mapper = new MarketFeatureMapperImpl();
    private final MarketFeatureService service = new MarketFeatureService(readClient, mapper);
    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /**
     * Пересечение вверх истинно ровно тогда, когда обе половины на месте.
     *
     * <p>Значения подобраны так, что «сейчас» быстрая выше медленной, а
     * «прежде» была не выше: это и есть {@code CROSSED_ABOVE}. Тот же
     * контекст без предыдущих значений обязан дать ложь — иначе тест
     * прошёл бы и на поверхности, которая предыдущее не отдаёт.
     */
    @Test
    void crossoverNeedsBothHalvesOfTheComparison() {
        when(readClient.readFeatures(anyString(), any())).thenReturn(crossingBundle());

        MarketFeatures features = service.readForEvaluation(strategy(), detail(), instrument());
        ConditionEvaluationContext context = DealContext.builder()
                .deal(new Deal())
                .marketFeatures(features)
                .build()
                .conditionContext(null);

        assertThat(evaluator.evaluate(crossedAbove(), context)).isTrue();
        assertThat(evaluator.evaluate(crossedAbove(), withoutPreviousValues(context))).isFalse();
    }

    /**
     * Подтип значения восстанавливается по названному типу.
     *
     * <p>Плоская форма владельца полиморфизма не несёт, а грамматика
     * условий читает скаляр через подтип: значение, приехавшее без него,
     * было бы неотличимо от отсутствующего.
     */
    @Test
    void flatValueRegainsItsSubtype() {
        when(readClient.readFeatures(anyString(), any())).thenReturn(crossingBundle());

        IndicatorValue value = service.readForEvaluation(strategy(), detail(), instrument())
                .getLatestIndicators().get(FAST);

        assertThat(value).isInstanceOf(EmaValue.class);
        assertThat(((EmaValue) value).getEma()).isEqualByComparingTo("12");
    }

    /**
     * Событие пробоя собирается обратно во вложенную форму, и его
     * отсутствие остаётся отсутствием.
     *
     * <p>Признак «пробой есть» у структуры — наличие события. Собирать его
     * всегда значило бы сделать предикат подтверждённого пробоя истинным
     * везде, то есть открыть вход там, где его не было.
     */
    @Test
    void breakoutEventIsRebuiltAndItsAbsenceStays() {
        when(readClient.readFeatures(anyString(), any())).thenReturn(crossingBundle());

        assertThat(service.readForEvaluation(strategy(), detail(), instrument())
                .getStructures().get(STRUCTURE).hasConfirmedBreakout()).isFalse();

        when(readClient.readFeatures(anyString(), any())).thenReturn(bundleWithBreakout());

        assertThat(service.readForEvaluation(strategy(), detail(), instrument())
                .getStructures().get(STRUCTURE).hasConfirmedBreakout()).isTrue();
    }

    /**
     * Непривязанное объявление в запрос не попадает.
     *
     * <p>Идентичность вычисления выдаёт владелец на объявление
     * потребности; до неё читать нечем, и подставить сюда что-либо своё
     * значило бы заказать ЧУЖОЕ вычисление.
     */
    @Test
    void unboundDeclarationIsNotRequested() {
        when(readClient.readFeatures(anyString(), any())).thenReturn(crossingBundle());
        Strategy strategy = strategy();
        strategy.getIndicatorSettings().get(1).setComputationConfigInternalId(null);

        service.readForEvaluation(strategy, detail(), instrument());

        assertThat(requestedIndicatorKeys()).containsExactly(FAST);
    }

    /**
     * Объявление без срока свежести в запрос не попадает.
     *
     * <p>Пустой срок — не «бессрочно свежо», а отказ читать: объявление не
     * сказало, чему оно готово доверять, и подставить своё число значило
     * бы решить за автора в разрешающую сторону.
     */
    @Test
    void declarationWithoutToleranceIsNotRequested() {
        when(readClient.readFeatures(anyString(), any())).thenReturn(crossingBundle());
        Strategy strategy = strategy();
        strategy.getIndicatorSettings().get(1).setExpirationDuration(null);

        service.readForEvaluation(strategy, detail(), instrument());

        assertThat(requestedIndicatorKeys()).containsExactly(FAST);
    }

    /**
     * Цена спрашивается ровно тогда, когда её называет условие детали.
     *
     * <p>Цена — единственный вход, который берётся не из хранилища
     * владельца, а чтением у площадки: заказывать её каждому проходу
     * значило бы вешать на сопровождение сделки round-trip наружу и
     * доступность биржи там, где ни одно условие цены не спрашивает.
     */
    @Test
    void priceIsRequestedOnlyWhenAConditionNamesIt() {
        when(readClient.readFeatures(anyString(), any())).thenReturn(crossingBundle());

        service.readForEvaluation(strategy(), detail(), instrument());
        assertThat(capturedRequest().getPriceRequired()).isFalse();

        service.readForEvaluation(strategy(), detailReadingPrice(), instrument());
        assertThat(capturedRequest().getPriceRequired()).isTrue();
    }

    private List<String> requestedIndicatorKeys() {
        return capturedRequest().getIndicatorBindings().stream()
                .map(MarketFeatureBinding::getKey)
                .toList();
    }

    private MarketFeatureReadRequest capturedRequest() {
        ArgumentCaptor<MarketFeatureReadRequest> captor = ArgumentCaptor.forClass(MarketFeatureReadRequest.class);
        verify(readClient, atLeastOnce()).readFeatures(anyString(), captor.capture());
        return captor.getValue();
    }

    /** Контекст без предыдущих значений — то, что дала бы поверхность без второй половины. */
    private ConditionEvaluationContext withoutPreviousValues(ConditionEvaluationContext context) {
        return ConditionEvaluationContext.builder()
                .latestIndicators(context.getLatestIndicators())
                .previousIndicators(Map.of())
                .structures(context.getStructures())
                .price(context.getPrice())
                .evaluationTime(context.getEvaluationTime())
                .build();
    }

    private MarketFeatureBundleResponse crossingBundle() {
        MarketFeatureBundleResponse response = new MarketFeatureBundleResponse();
        response.setLatestIndicators(Map.of(FAST, ema("12"), SLOW, ema("10")));
        response.setPreviousIndicators(Map.of(FAST, ema("9"), SLOW, ema("10")));
        response.setStructures(Map.of(STRUCTURE, structure()));
        return response;
    }

    private MarketFeatureBundleResponse bundleWithBreakout() {
        MarketFeatureBundleResponse response = crossingBundle();
        MarketStructureResponse structure = structure();
        structure.setBreakoutBrokenLevelType(MarketPriceLevel.Type.RANGE_HIGH.name());
        structure.setBreakoutDirection("UP");
        structure.setBreakoutLevelPrice(new BigDecimal("100"));
        response.setStructures(Map.of(STRUCTURE, structure));
        return response;
    }

    private MarketStructureResponse structure() {
        MarketStructureResponse structure = new MarketStructureResponse();
        structure.setType("RANGE");
        return structure;
    }

    private IndicatorValueResponse ema(String value) {
        IndicatorValueResponse response = new IndicatorValueResponse();
        response.setIndicatorType(IndicatorValue.Type.EMA.name());
        response.setEma(new BigDecimal(value));
        return response;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        return instrument;
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy();
        strategy.setInternalId("st-0001");
        strategy.setIndicatorSettings(new ArrayList<>(List.of(
                indicatorSetting(FAST, "cfg-fast"), indicatorSetting(SLOW, "cfg-slow"))));
        strategy.setMarketStructureSettings(new ArrayList<>(List.of(structureSetting())));
        return strategy;
    }

    private StrategyIndicatorSetting indicatorSetting(String key, String identity) {
        StrategyIndicatorSetting setting = new StrategyIndicatorSetting();
        setting.setKey(key);
        setting.setIndicatorType(IndicatorValue.Type.EMA);
        setting.setComputationConfigInternalId(identity);
        setting.setExpirationDuration(Duration.ofMinutes(5));
        return setting;
    }

    private StrategyMarketStructureSetting structureSetting() {
        StrategyMarketStructureSetting setting = new StrategyMarketStructureSetting();
        setting.setKey(STRUCTURE);
        setting.setComputationConfigInternalId("cfg-structure");
        setting.setExpirationDuration(Duration.ofMinutes(15));
        return setting;
    }

    /** Деталь с одним шагом на пересечении — цены её условие не называет. */
    private StrategyDetail detail() {
        return detailWith(crossedAbove());
    }

    /** Деталь, чьё условие сравнивает цену с константой. */
    private StrategyDetail detailReadingPrice() {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.PRICE);

        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.PRICE_COMPARE);
        rule.setOperator(StrategyConditionOperator.GT);
        rule.setLeftOperand(left);
        rule.setRightOperand(constant("0"));

        StrategyCondition condition = new StrategyCondition();
        condition.setRules(List.of(rule));
        return detailWith(condition);
    }

    private StrategyDetail detailWith(StrategyCondition condition) {
        StrategyStep step = new StrategyStep();
        step.setCondition(condition);

        StrategyTranche tranche = new StrategyTranche();
        tranche.setStepsByStatus(Map.of(DealTranche.Status.PRECHECK, List.of(step)));

        StrategyDetail detail = new StrategyDetail();
        detail.setId(11L);
        detail.setTranches(List.of(tranche));
        return detail;
    }

    /** Условие «быстрая пересекла медленную снизу вверх». */
    private StrategyCondition crossedAbove() {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.CROSSOVER);
        rule.setOperator(StrategyConditionOperator.CROSSED_ABOVE);
        rule.setLeftOperand(indicatorOperand(FAST));
        rule.setRightOperand(indicatorOperand(SLOW));

        StrategyCondition condition = new StrategyCondition();
        condition.setRules(List.of(rule));
        return condition;
    }

    private StrategyConditionOperand indicatorOperand(String key) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.INDICATOR);
        operand.setIndicatorKey(key);
        return operand;
    }

    private StrategyConditionOperand constant(String value) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.CONSTANT);
        operand.setValueType(ConstantValueType.NUMBER);
        operand.setValue(value);
        return operand;
    }
}
