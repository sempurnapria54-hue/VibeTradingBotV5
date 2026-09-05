package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketdata.domain.model.FeatureBinding;
import com.example.marketdata.domain.model.MarketPhaseRequest;
import com.example.marketdata.domain.service.IndicatorService;
import com.example.marketdata.domain.service.MarketDataExpirationChecker;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Фаза резолвится по клаузам ПОТРЕБИТЕЛЯ на данных market-data.
 *
 * <p>Своего срока свежести у фазы нет: она наследует его от входов, и
 * устаревший вход в контекст не попадает — операнд оказывается
 * недоступен, и результат консервативный {@code UNKNOWN}
 * (docs/components/MarketPhaseResolver.md). Это и проверяется: те же
 * клаузы на том же значении дают разный исход при разной толерантности.
 */
class MarketPhaseResolutionTest {

    private static final Long INSTRUMENT_ID = 1L;
    private static final Long CONFIG_ID = 7L;
    private static final String KEY = "atr";

    private final IndicatorDataService indicatorDataService = mock(IndicatorDataService.class);
    private final MarketStructureDataService structureDataService = mock(MarketStructureDataService.class);
    private final MarketPriceDataService priceDataService = mock(MarketPriceDataService.class);
    private final MarketDataExpirationChecker checker = new MarketDataExpirationChecker();

    private final MarketPhaseService phaseService = new MarketPhaseService(
            new IndicatorService(indicatorDataService, checker),
            new MarketStructureService(structureDataService, checker),
            priceDataService,
            new MarketPhaseResolver(new StrategyConditionEvaluator()));

    /** Свежий вход — клауза истинна, фаза классифицирована. */
    @Test
    void freshInputClassifiesPhase() {
        givenAtrAgedMinutes(1);

        Optional<MarketPhase> phase = phaseService.getCurrentPhase(instrument(), request(Duration.ofHours(1)));

        assertThat(phase).isPresent();
        assertThat(phase.get().getType()).isEqualTo(MarketPhase.Type.BULL_TREND);
    }

    /**
     * Тот же вход, но старше толерантности читателя — в контекст он не
     * попадает, и результат {@code UNKNOWN}, а не «клауза ложна».
     */
    @Test
    void staleInputYieldsUnknown() {
        givenAtrAgedMinutes(120);

        Optional<MarketPhase> phase = phaseService.getCurrentPhase(instrument(), request(Duration.ofMinutes(5)));

        assertThat(phase).isPresent();
        assertThat(phase.get().getType()).isEqualTo(MarketPhase.Type.UNKNOWN);
    }

    /** Клауз не передано — классифицировать нечем, и это не UNKNOWN, а пустота. */
    @Test
    void absentRulesGiveEmptyResult() {
        MarketPhaseRequest empty = MarketPhaseRequest.builder()
                .phaseRules(List.of())
                .indicatorBindings(List.of())
                .structureBindings(List.of())
                .build();

        assertThat(phaseService.getCurrentPhase(instrument(), empty)).isEmpty();
    }

    /**
     * Клаузы без {@code PRICE}-операнда наружу не ходят.
     *
     * <p>Цена, в отличие от индикаторов и структур, берётся не из своего
     * хранилища, а чтением у площадки. Собирать её для клауз, которые её
     * не называют, значит вешать на классификацию фазы round-trip наружу
     * и доступность площадки там, где ни того, ни другого не нужно.
     */
    @Test
    void clausesWithoutPriceOperandDoNotReachTheExchange() {
        givenAtrAgedMinutes(1);

        phaseService.getCurrentPhase(instrument(), request(Duration.ofHours(1)));

        verify(priceDataService, never()).getMarketPriceData(anyLong(), anyString());
    }

    /**
     * Недоступная цена даёт ПУСТОЙ операнд, а не отказ запроса.
     *
     * <p>Семантика фазы одна для всех входов: отсутствующий вход в
     * контекст не попадает, и результат — консервативный {@code UNKNOWN}
     * (docs/components/MarketPhaseResolver.md). Для индикаторов и структур
     * это держалось само — их чтение отдаёт пустоту; у цены отказ
     * площадки уходил исключением наружу, и потребитель получал отказ там,
     * где по контракту ему полагался {@code UNKNOWN}.
     */
    @Test
    void unavailablePriceYieldsUnknownRatherThanFailure() {
        when(indicatorDataService.findLatestTwo(anyLong(), anyLong())).thenReturn(List.<IndicatorValue>of());
        when(priceDataService.getMarketPriceData(anyLong(), anyString()))
                .thenThrow(new ExchangeReadException("connector unreachable"));

        Optional<MarketPhase> phase = phaseService.getCurrentPhase(instrument(), priceRequest());

        assertThat(phase).isPresent();
        assertThat(phase.get().getType()).isEqualTo(MarketPhase.Type.UNKNOWN);
    }

    private void givenAtrAgedMinutes(int minutes) {
        AtrValue value = new AtrValue();
        value.setInstrumentId(INSTRUMENT_ID);
        value.setIndicatorConfigId(CONFIG_ID);
        value.setCandleTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes));
        value.setAtr(new BigDecimal("5"));
        when(indicatorDataService.findLatest(INSTRUMENT_ID, CONFIG_ID)).thenReturn(Optional.of(value));
        when(indicatorDataService.findLatestTwo(anyLong(), anyLong())).thenReturn(List.<IndicatorValue>of());
        when(priceDataService.getMarketPriceData(anyLong(), anyString())).thenReturn(null);
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("BTC-USDT-SWAP");
        return instrument;
    }

    private MarketPhaseRequest request(Duration tolerance) {
        return MarketPhaseRequest.builder()
                .phaseRules(List.of(trendRule()))
                .indicatorBindings(List.of(new FeatureBinding(KEY, CONFIG_ID, tolerance)))
                .structureBindings(List.of())
                .build();
    }

    /** Клауза о цене: она и заставляет сервис идти к площадке. */
    private MarketPhaseRequest priceRequest() {
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

        return MarketPhaseRequest.builder()
                .phaseRules(List.of(phaseRule))
                .indicatorBindings(List.of())
                .structureBindings(List.of())
                .build();
    }

    /** ATR больше нуля — трендовая фаза; на пустом операнде клауза ложна. */
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
