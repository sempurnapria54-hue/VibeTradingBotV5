package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketdata.domain.model.FeatureBinding;
import com.example.marketdata.domain.model.FeatureReadRequest;
import com.example.marketdata.domain.model.MarketFeatureBundle;
import com.example.marketdata.domain.service.IndicatorService;
import com.example.marketdata.domain.service.MarketDataExpirationChecker;
import com.example.marketdata.domain.service.MarketFeatureService;
import com.example.marketdata.domain.service.MarketPhaseService;
import com.example.marketdata.domain.service.MarketPriceDataService;
import com.example.marketdata.domain.service.MarketStructureService;
import com.example.marketdata.domain.service.phase.MarketPhaseResolver;
import com.example.marketdata.integration.ExchangeReadException;
import com.example.marketdata.persistence.service.IndicatorDataService;
import com.example.marketdata.persistence.service.MarketStructureDataService;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Чтение фич на момент решения: связка снимается одним проходом, а фаза
 * классифицируется на тех же входах.
 *
 * <p>Своего срока свежести у фазы нет: она наследует его от входов, и
 * устаревший вход в контекст не попадает — операнд оказывается
 * недоступен, и результат консервативный {@code UNKNOWN}
 * (docs/components/MarketPhaseResolver.md). Предыдущее значение при этом
 * свежестью НЕ гейтится: оно вторая половина сравнения, а не точка
 * решения.
 */
class MarketFeatureReadTest {

    private static final Long INSTRUMENT_ID = 1L;
    private static final Long CONFIG_ID = 7L;
    private static final String KEY = "atr";

    private final IndicatorDataService indicatorDataService = mock(IndicatorDataService.class);
    private final MarketStructureDataService structureDataService = mock(MarketStructureDataService.class);
    private final MarketPriceDataService priceDataService = mock(MarketPriceDataService.class);
    private final MarketDataExpirationChecker checker = new MarketDataExpirationChecker();

    private final MarketFeatureService featureService = new MarketFeatureService(
            new IndicatorService(indicatorDataService, checker),
            new MarketStructureService(structureDataService, checker),
            priceDataService,
            new MarketPhaseService(new MarketPhaseResolver(new StrategyConditionEvaluator())));

    /**
     * Предыдущее значение приезжает вместе с последним.
     *
     * <p>Ради этого чтение и заведено: на предыдущем значении стои́т весь
     * класс правил пересечения и объёмный фильтр, а поверхность отдавала
     * только последнее — контекст читателя получал бы пустой операнд, и
     * шаг стратегии не исполнялся бы МОЛЧА.
     */
    @Test
    void bundleCarriesPreviousValueAlongsideLatest() {
        IndicatorValue latest = atrAgedMinutes(1, "5");
        IndicatorValue previous = atrAgedMinutes(2, "4");
        when(indicatorDataService.findLatest(INSTRUMENT_ID, CONFIG_ID)).thenReturn(Optional.of(latest));
        when(indicatorDataService.findLatestTwo(INSTRUMENT_ID, CONFIG_ID)).thenReturn(List.of(latest, previous));

        MarketFeatureBundle bundle = featureService.readFeatures(instrument(), bindingsOnly(Duration.ofHours(1)));

        assertThat(bundle.getLatestIndicators()).containsEntry(KEY, latest);
        assertThat(bundle.getPreviousIndicators()).containsEntry(KEY, previous);
    }

    /**
     * Свежесть гейтит последнее значение и НЕ гейтит предыдущее.
     *
     * <p>Отбросить вторую половину сравнения, оставив первую, значило бы
     * сделать правило пересечения ложным по причине, которой у него нет:
     * предыдущее значение старше по построению.
     */
    @Test
    void freshnessGatesLatestOnly() {
        IndicatorValue latest = atrAgedMinutes(120, "5");
        IndicatorValue previous = atrAgedMinutes(121, "4");
        when(indicatorDataService.findLatest(INSTRUMENT_ID, CONFIG_ID)).thenReturn(Optional.of(latest));
        when(indicatorDataService.findLatestTwo(INSTRUMENT_ID, CONFIG_ID)).thenReturn(List.of(latest, previous));

        MarketFeatureBundle bundle = featureService.readFeatures(instrument(), bindingsOnly(Duration.ofMinutes(5)));

        assertThat(bundle.getLatestIndicators()).doesNotContainKey(KEY);
        assertThat(bundle.getPreviousIndicators()).containsEntry(KEY, previous);
    }

    /** Свежий вход — клауза истинна, фаза классифицирована. */
    @Test
    void freshInputClassifiesPhase() {
        givenAtrAgedMinutes(1);

        MarketPhase phase = featureService.readFeatures(instrument(), phaseRequest(Duration.ofHours(1)))
                .getMarketPhase();

        assertThat(phase).isNotNull();
        assertThat(phase.getType()).isEqualTo(MarketPhase.Type.BULL_TREND);
    }

    /**
     * Тот же вход, но старше толерантности читателя — в контекст он не
     * попадает, и результат {@code UNKNOWN}, а не «клауза ложна».
     */
    @Test
    void staleInputYieldsUnknown() {
        givenAtrAgedMinutes(120);

        MarketPhase phase = featureService.readFeatures(instrument(), phaseRequest(Duration.ofMinutes(5)))
                .getMarketPhase();

        assertThat(phase).isNotNull();
        assertThat(phase.getType()).isEqualTo(MarketPhase.Type.UNKNOWN);
    }

    /** Клауз не передано — классифицировать нечем, и это не UNKNOWN, а пустота. */
    @Test
    void absentRulesGiveNoPhase() {
        givenAtrAgedMinutes(1);

        MarketFeatureBundle bundle = featureService.readFeatures(instrument(), bindingsOnly(Duration.ofHours(1)));

        assertThat(bundle.getMarketPhase()).isNull();
    }

    /**
     * Цену никто не назвал — наружу не ходим.
     *
     * <p>Цена, в отличие от индикаторов и структур, берётся не из своего
     * хранилища, а чтением у площадки. Собирать её там, где её не
     * спрашивают ни клаузы, ни сам читатель, значит вешать на чтение
     * round-trip наружу и доступность площадки.
     */
    @Test
    void unrequestedPriceDoesNotReachTheExchange() {
        givenAtrAgedMinutes(1);

        featureService.readFeatures(instrument(), phaseRequest(Duration.ofHours(1)));

        verify(priceDataService, never()).getMarketPriceData(anyLong(), anyString());
    }

    /**
     * Читатель без клауз фазы вправе спросить цену явно.
     *
     * <p>Её читают условия шагов сделки и калькуляторы параметров
     * действия, а их предикатов market-data не получает: вывести
     * потребность ему неоткуда, и без явной величины цена не приехала бы
     * ни одному из них.
     */
    @Test
    void explicitlyRequestedPriceIsRead() {
        givenAtrAgedMinutes(1);
        MarketPriceData prices = new MarketPriceData();
        prices.setExternalLastPrice(new BigDecimal("42"));
        when(priceDataService.getMarketPriceData(anyLong(), anyString())).thenReturn(prices);

        FeatureReadRequest request = FeatureReadRequest.builder()
                .indicatorBindings(List.of())
                .structureBindings(List.of())
                .priceRequired(true)
                .build();

        assertThat(featureService.readFeatures(instrument(), request).getMarketPriceData()).isSameAs(prices);
    }

    /**
     * Недоступная цена даёт ПУСТОЙ операнд, а не отказ чтения.
     *
     * <p>Семантика входа одна для всех: отсутствующий в контекст не
     * попадает, и предикат на нём консервативно ложен, а фаза —
     * {@code UNKNOWN} (docs/components/MarketPhaseResolver.md). Для
     * индикаторов и структур это держалось само — их чтение отдаёт
     * пустоту; у цены отказ площадки уходил исключением наружу, и
     * потребитель получал отказ там, где по контракту ему полагалась
     * пустота.
     */
    @Test
    void unavailablePriceYieldsUnknownRatherThanFailure() {
        when(indicatorDataService.findLatestTwo(anyLong(), anyLong())).thenReturn(List.<IndicatorValue>of());
        when(priceDataService.getMarketPriceData(anyLong(), anyString()))
                .thenThrow(new ExchangeReadException("connector unreachable"));

        MarketFeatureBundle bundle = featureService.readFeatures(instrument(), priceClauseRequest());

        assertThat(bundle.getMarketPriceData()).isNull();
        assertThat(bundle.getMarketPhase().getType()).isEqualTo(MarketPhase.Type.UNKNOWN);
    }

    private void givenAtrAgedMinutes(int minutes) {
        when(indicatorDataService.findLatest(INSTRUMENT_ID, CONFIG_ID))
                .thenReturn(Optional.of(atrAgedMinutes(minutes, "5")));
        when(indicatorDataService.findLatestTwo(anyLong(), anyLong())).thenReturn(List.<IndicatorValue>of());
    }

    private IndicatorValue atrAgedMinutes(int minutes, String value) {
        AtrValue atr = new AtrValue();
        atr.setInstrumentId(INSTRUMENT_ID);
        atr.setIndicatorConfigId(CONFIG_ID);
        atr.setCandleTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes));
        atr.setAtr(new BigDecimal(value));
        return atr;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("BTC-USDT-SWAP");
        return instrument;
    }

    private FeatureReadRequest bindingsOnly(Duration tolerance) {
        return FeatureReadRequest.builder()
                .indicatorBindings(List.of(new FeatureBinding(KEY, CONFIG_ID, tolerance)))
                .structureBindings(List.of())
                .build();
    }

    private FeatureReadRequest phaseRequest(Duration tolerance) {
        return FeatureReadRequest.builder()
                .phaseRules(List.of(trendRule()))
                .indicatorBindings(List.of(new FeatureBinding(KEY, CONFIG_ID, tolerance)))
                .structureBindings(List.of())
                .build();
    }

    /** Клауза о цене: она и заставляет чтение идти к площадке. */
    private FeatureReadRequest priceClauseRequest() {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.PRICE);

        StrategyConditionOperand right = new StrategyConditionOperand();
        right.setSourceType(StrategyConditionSourceType.CONSTANT);
        right.setValueType(ConstantValueType.NUMBER);
        right.setValue("0");

        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.PRICE_COMPARE);
        rule.setOperator(StrategyConditionOperator.GT);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);

        StrategyCondition condition = new StrategyCondition();
        condition.setRules(List.of(rule));

        StrategyMarketPhaseRule phaseRule = new StrategyMarketPhaseRule();
        phaseRule.setType(MarketPhase.Type.BULL_TREND);
        phaseRule.setCondition(condition);

        return FeatureReadRequest.builder()
                .phaseRules(List.of(phaseRule))
                .indicatorBindings(List.of())
                .structureBindings(List.of())
                .build();
    }

    /** Клауза «ATR больше нуля» — истинна на всяком доступном значении. */
    private StrategyMarketPhaseRule trendRule() {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.INDICATOR);
        left.setIndicatorKey(KEY);

        StrategyConditionOperand right = new StrategyConditionOperand();
        right.setSourceType(StrategyConditionSourceType.CONSTANT);
        right.setValueType(ConstantValueType.NUMBER);
        right.setValue("0");

        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setOperator(StrategyConditionOperator.GT);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);

        StrategyCondition condition = new StrategyCondition();
        condition.setRules(List.of(rule));

        StrategyMarketPhaseRule phaseRule = new StrategyMarketPhaseRule();
        phaseRule.setType(MarketPhase.Type.BULL_TREND);
        phaseRule.setCondition(condition);
        return phaseRule;
    }
}
