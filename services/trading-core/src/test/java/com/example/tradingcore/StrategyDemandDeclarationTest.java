package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingcore.integration.MarketDataDemandClient;
import com.example.tradingcore.integration.PeerServiceUnavailableException;
import com.example.tradingcore.integration.model.ComputationConfigResponse;
import com.example.tradingcore.domain.service.StrategyDemandService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Объявление потребности копии определения владельцу рыночных данных.
 *
 * <p><b>Что здесь охраняется.</b> Без идентичности вычисления ядро не
 * составляет ни одного чтения фич, поэтому проверяется не «вызов
 * сделан», а три свойства, каждое из которых молча ломает торговлю:
 * порядок (структура адресует входы идентичностями, значит индикаторы
 * первыми), однократность (копия неизменяема, второй идентичности у
 * объявления быть не может) и отказ от подстановки (неразрешимый вход не
 * подменяется пустым, иначе заказано будет ДРУГОЕ вычисление).
 */
class StrategyDemandDeclarationTest {

    private static final Long STRATEGY_ID = 4L;
    private static final Long INSTRUMENT_ID = 9L;
    private static final String INSTRUMENT_INTERNAL_ID = "i-1";
    private static final String ATR_IDENTITY = "cfg-atr";
    private static final String RSI_IDENTITY = "cfg-rsi";
    private static final String STRUCTURE_IDENTITY = "cfg-structure";

    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final MarketDataDemandClient demandClient = mock(MarketDataDemandClient.class);

    private final StrategyDemandService service = new StrategyDemandService(strategyDataService,
            instrumentDataService, demandClient, new ObjectMapper());

    /**
     * Индикаторы требуются раньше структуры: её входы адресуются
     * идентичностями, и в обратном порядке требование структуры было бы
     * несоставимо. Ряды свечей идут первыми — без ряда своего таймфрейма
     * идентичность не считается ни по одному инструменту.
     */
    @Test
    void seriesComeFirstAndIndicatorsPrecedeStructures() {
        stubPending(strategyWithAtrBackedStructure());
        when(demandClient.requireIndicator(anyString(), any(), any())).thenReturn(identity(ATR_IDENTITY));
        when(demandClient.requireMarketStructure(any(), any(), any(), any()))
                .thenReturn(identity(STRUCTURE_IDENTITY));

        service.declarePendingDemands();

        InOrder order = inOrder(demandClient);
        order.verify(demandClient).requireCandles(eq(INSTRUMENT_INTERNAL_ID), eq(TimeFrame.ONE_HOUR), any());
        order.verify(demandClient).requireIndicator(eq(IndicatorValue.Type.ATR.name()), eq(TimeFrame.ONE_HOUR), any());
        order.verify(demandClient)
                .requireMarketStructure(eq(TimeFrame.ONE_HOUR), any(), isNull(), eq(ATR_IDENTITY));
    }

    /** Выданная идентичность ложится на объявление write-once — точечным запросом. */
    @Test
    void grantedIdentitiesAreBoundToTheirDeclarations() {
        stubPending(strategyWithAtrBackedStructure());
        when(demandClient.requireIndicator(anyString(), any(), any())).thenReturn(identity(ATR_IDENTITY));
        when(demandClient.requireMarketStructure(any(), any(), any(), any()))
                .thenReturn(identity(STRUCTURE_IDENTITY));

        service.declarePendingDemands();

        verify(strategyDataService).bindIndicatorComputation(1L, ATR_IDENTITY);
        verify(strategyDataService).bindMarketStructureComputation(2L, STRUCTURE_IDENTITY);
    }

    /**
     * Привязанное объявление не требуется повторно, но в раскладку входов
     * попадает: структура, привязывающаяся этим проходом, обязана назвать
     * идентичность индикатора, привязанного прошлым.
     */
    @Test
    void alreadyBoundIndicatorIsNotRequiredAgainButStillResolvesStructureInput() {
        Strategy strategy = strategyWithAtrBackedStructure();
        strategy.getIndicatorSettings().get(0).setComputationConfigInternalId(ATR_IDENTITY);
        stubPending(strategy);
        when(demandClient.requireMarketStructure(any(), any(), any(), any()))
                .thenReturn(identity(STRUCTURE_IDENTITY));

        service.declarePendingDemands();

        verify(demandClient, never()).requireIndicator(anyString(), any(), any());
        verify(strategyDataService, never()).bindIndicatorComputation(anyLong(), anyString());
        verify(demandClient).requireMarketStructure(eq(TimeFrame.ONE_HOUR), any(), isNull(), eq(ATR_IDENTITY));
    }

    /**
     * Объявленный вход, которого в каталоге стратегии нет, останавливает
     * требование этой структуры. Подстановка пустого входа заказала бы
     * ДРУГОЕ вычисление: у владельца пустая ссылка означает «вход не
     * объявлен», и резолвер откатился бы на внутренний прокси.
     */
    @Test
    void unresolvedStructureInputStopsItsRequirementInsteadOfDroppingTheInput() {
        Strategy strategy = strategyWithAtrBackedStructure();
        strategy.getMarketStructureSettings().get(0).setAtrKey("atr-that-nobody-declared");
        stubPending(strategy);
        when(demandClient.requireIndicator(anyString(), any(), any())).thenReturn(identity(ATR_IDENTITY));

        service.declarePendingDemands();

        verify(demandClient, never()).requireMarketStructure(any(), any(), any(), any());
        verify(strategyDataService, never()).bindMarketStructureComputation(anyLong(), anyString());
    }

    /**
     * Требования одного таймфрейма сводятся бо́льшей глубиной, а
     * неназванная глубина побеждает названную: она и есть «вся доступная
     * история», то есть самое глубокое требование.
     */
    @Test
    void undeclaredDepthWinsOverDeclaredOnTheSameTimeframe() {
        Strategy strategy = strategyWithAtrBackedStructure();
        StrategyIndicatorSetting withoutWarmup = indicatorSetting(3L, "rsi", IndicatorValue.Type.RSI,
                rsiParams(null));
        strategy.getIndicatorSettings().add(withoutWarmup);
        stubPending(strategy);
        when(demandClient.requireIndicator(anyString(), any(), any())).thenReturn(identity(RSI_IDENTITY));
        when(demandClient.requireMarketStructure(any(), any(), any(), any()))
                .thenReturn(identity(STRUCTURE_IDENTITY));

        service.declarePendingDemands();

        verify(demandClient, times(1)).requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, null);
    }

    /**
     * Недоступность владельца прекращает проход целиком: следующие копии
     * дадут тот же отказ. Проход при этом не падает — тик повторит.
     */
    @Test
    void ownerUnavailabilityStopsThePassWithoutFailingIt() {
        when(strategyDataService.findIdsWithUnboundComputation()).thenReturn(List.of(STRATEGY_ID, 5L));
        when(strategyDataService.findWithSettings(STRATEGY_ID))
                .thenReturn(Optional.of(strategyWithAtrBackedStructure()));
        when(instrumentDataService.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID)).thenReturn(instrument());
        when(demandClient.requireIndicator(anyString(), any(), any()))
                .thenThrow(new PeerServiceUnavailableException("market-data is down", null));

        Integer declared = service.declarePendingDemands();

        assertThat(declared).isEqualTo(0);
        verify(strategyDataService, never()).findWithSettings(5L);
    }

    private void stubPending(Strategy strategy) {
        when(strategyDataService.findIdsWithUnboundComputation()).thenReturn(List.of(STRATEGY_ID));
        when(strategyDataService.findWithSettings(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(instrumentDataService.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID)).thenReturn(instrument());
    }

    /** Копия с одним ATR-индикатором и структурой, объявившей его своим входом. */
    private Strategy strategyWithAtrBackedStructure() {
        Strategy strategy = new Strategy();
        strategy.setId(STRATEGY_ID);
        strategy.setInternalId("st-1");
        strategy.setInstrumentInternalId(INSTRUMENT_INTERNAL_ID);
        AtrParams atrParams = new AtrParams();
        atrParams.setTimeframe(TimeFrame.ONE_HOUR);
        atrParams.setWarmup(50);
        strategy.setIndicatorSettings(new ArrayList<>(List.of(
                indicatorSetting(1L, "atr", IndicatorValue.Type.ATR, atrParams))));
        strategy.setMarketStructureSettings(new ArrayList<>(List.of(structureSetting())));
        return strategy;
    }

    private StrategyIndicatorSetting indicatorSetting(Long id, String key, IndicatorValue.Type type,
                                                      IndicatorParams params) {
        StrategyIndicatorSetting setting = new StrategyIndicatorSetting();
        setting.setId(id);
        setting.setKey(key);
        setting.setIndicatorType(type);
        setting.setParams(params);
        return setting;
    }

    private RsiParams rsiParams(Integer warmup) {
        RsiParams params = new RsiParams();
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(warmup);
        return params;
    }

    private StrategyMarketStructureSetting structureSetting() {
        StrategyMarketStructureSetting setting = new StrategyMarketStructureSetting();
        setting.setId(2L);
        setting.setKey("structure");
        setting.setTimeframe(TimeFrame.ONE_HOUR);
        setting.setAtrKey("atr");
        MarketStructureParams params = new MarketStructureParams();
        params.setLookbackBars(120);
        params.setSwingLookbackBars(30);
        setting.setParams(params);
        return setting;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        return instrument;
    }

    private ComputationConfigResponse identity(String internalId) {
        ComputationConfigResponse response = new ComputationConfigResponse();
        response.setInternalId(internalId);
        return response;
    }
}
