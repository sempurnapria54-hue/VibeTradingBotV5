package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.domain.service.InstrumentCatalogService;
import com.example.marketdata.integration.ExchangeReadClient;
import com.example.marketdata.mapping.InstrumentMapper;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Готовность инструмента считается по его группам, и групп может не быть.
 *
 * <p>Это следствие смены механизма: группу заводит требование, а не
 * онбординг (docs/processes/candle-loading.md §«Кто заводит группу»).
 * Инструмент без единой заказанной группы остаётся в {@code SYNC} —
 * спецификация известна, собирать нечего; объявить его {@code ACTIVE}
 * значило бы сказать «история покрыта», когда истории нет вовсе.
 */
class InstrumentReadinessTest {

    private static final Long INSTRUMENT_ID = 1L;

    private final ExchangeReadClient readClient = mock(ExchangeReadClient.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final InstrumentExternalRulesDataService rulesDataService =
            mock(InstrumentExternalRulesDataService.class);
    private final CandleGroupDataService candleGroupDataService = mock(CandleGroupDataService.class);
    private final InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
    private final ConnectorProperties connectorProperties = new ConnectorProperties();

    private final InstrumentCatalogService catalogService = new InstrumentCatalogService(
            readClient, instrumentDataService, rulesDataService, candleGroupDataService,
            instrumentMapper, connectorProperties);

    /** Групп нет — инструмент остаётся в SYNC: собирать по нему никто не просил. */
    @Test
    void instrumentWithoutGroupsStaysSynced() {
        givenInstrument(Instrument.Status.CANDLES_LOADING);
        when(candleGroupDataService.findByInstrumentId(INSTRUMENT_ID)).thenReturn(List.of());
        when(instrumentDataService.saveSpecification(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(catalogService.evaluateReadiness(INSTRUMENT_ID).getStatus())
                .isEqualTo(Instrument.Status.SYNC);
    }

    /** Хотя бы одна группа не готова — инструмент в загрузке. */
    @Test
    void unreadyGroupKeepsInstrumentLoading() {
        givenInstrument(Instrument.Status.ACTIVE);
        when(candleGroupDataService.findByInstrumentId(INSTRUMENT_ID))
                .thenReturn(List.of(group(CandleGroup.Status.ACTIVE), group(CandleGroup.Status.BACKFILL)));
        when(instrumentDataService.saveSpecification(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(catalogService.evaluateReadiness(INSTRUMENT_ID).getStatus())
                .isEqualTo(Instrument.Status.CANDLES_LOADING);
    }

    /** Все группы готовы — инструмент готов. */
    @Test
    void allGroupsReadyMakeInstrumentActive() {
        givenInstrument(Instrument.Status.CANDLES_LOADING);
        when(candleGroupDataService.findByInstrumentId(INSTRUMENT_ID))
                .thenReturn(List.of(group(CandleGroup.Status.ACTIVE)));
        when(instrumentDataService.saveSpecification(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(catalogService.evaluateReadiness(INSTRUMENT_ID).getStatus())
                .isEqualTo(Instrument.Status.ACTIVE);
    }

    private void givenInstrument(Instrument.Status status) {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setStatus(status);
        when(instrumentDataService.getRequiredById(INSTRUMENT_ID)).thenReturn(instrument);
    }

    private CandleGroup group(CandleGroup.Status status) {
        CandleGroup group = new CandleGroup();
        group.setInstrumentId(INSTRUMENT_ID);
        group.setTimeframe(TimeFrame.ONE_HOUR);
        group.setStatus(status);
        return group;
    }
}
