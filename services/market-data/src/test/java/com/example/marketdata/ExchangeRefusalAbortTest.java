package com.example.marketdata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketdata.config.CandleLoadingProperties;
import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.config.InstrumentSyncProperties;
import com.example.marketdata.domain.jobs.CandleJob;
import com.example.marketdata.domain.jobs.InstrumentSyncJob;
import com.example.marketdata.domain.jobs.JobExecutionGuard;
import com.example.marketdata.domain.service.CandleLoader;
import com.example.marketdata.domain.service.InstrumentCatalogService;
import com.example.marketdata.integration.ExchangeAccessException;
import com.example.marketdata.integration.ExchangeReadException;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Отказ доступа или лимита ПРЕКРАЩАЕТ проход, а рядовой отказ чтения —
 * нет.
 *
 * <p>Разведение несущее и стои́т не на вкусе: лимит у площадки один на
 * все чтения сервиса, и проход, который под исчерпанным лимитом
 * продолжает обход, тратит бюджет ещё и того сбора, который догнать
 * нельзя (docs/processes/snapshot-collection.md §«Отказ на проходе»).
 * Ради этого разведения и заведён {@link ExchangeAccessException};
 * проверяется, что каждый ходящий к площадке проход его действительно
 * различает, а не гасит вместе с рядовым отказом.
 */
class ExchangeRefusalAbortTest {

    private static final Long FIRST_GROUP = 10L;
    private static final Long SECOND_GROUP = 11L;

    private final CandleGroupDataService candleGroupDataService = mock(CandleGroupDataService.class);
    private final CandleLoader candleLoader = mock(CandleLoader.class);
    private final InstrumentCatalogService catalogService = mock(InstrumentCatalogService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final JobExecutionGuard executionGuard = new JobExecutionGuard();

    private final CandleJob candleJob = new CandleJob(candleGroupDataService, candleLoader,
            catalogService, new CandleLoadingProperties(), executionGuard);

    /**
     * Лимит на первой группе — вторая в этом тике к площадке не идёт.
     *
     * <p>Пересчёт готовности после прерванного обхода остаётся: он ходит
     * только в свою базу и отражает статусы, которые обход уже успел
     * сдвинуть.
     */
    @Test
    void candleTickStopsOnAccessRefusal() {
        givenWorkingGroups(group(FIRST_GROUP), group(SECOND_GROUP));
        doRefuse(new ExchangeAccessException("limit", null));

        candleJob.tick();

        verify(candleLoader, times(1)).advance(any());
    }

    /** Рядовой отказ чтения стоит одну группу: обход продолжается. */
    @Test
    void candleTickSurvivesOrdinaryReadFailure() {
        givenWorkingGroups(group(FIRST_GROUP), group(SECOND_GROUP));
        doRefuse(new ExchangeReadException("boom"));

        candleJob.tick();

        verify(candleLoader, times(2)).advance(any());
    }

    /** Лимит на листинге — вторая половина тика синка не идёт вовсе. */
    @Test
    void instrumentSyncTickStopsOnAccessRefusal() {
        ConnectorProperties connectorProperties = new ConnectorProperties();
        connectorProperties.setExchangeCode("OKX");
        connectorProperties.setInstrumentTypes(List.of("SWAP"));
        InstrumentSyncJob syncJob = new InstrumentSyncJob(catalogService, instrumentDataService,
                new InstrumentSyncProperties(), connectorProperties, executionGuard);
        when(catalogService.synchronizeListing(anyString()))
                .thenThrow(new ExchangeAccessException("limit", null));

        syncJob.tick();

        verify(instrumentDataService, never()).findListedAfter(anyString(), any(), any(), any());
    }

    private void givenWorkingGroups(CandleGroup... groups) {
        when(candleGroupDataService.findByStatusIn(any())).thenReturn(List.of(groups));
    }

    private void doRefuse(RuntimeException failure) {
        doThrow(failure).doNothing().when(candleLoader).advance(any());
    }

    private CandleGroup group(Long id) {
        CandleGroup group = new CandleGroup();
        group.setId(id);
        group.setTimeframe(TimeFrame.ONE_HOUR);
        group.setStatus(CandleGroup.Status.BACKFILL);
        group.setCount(0L);
        return group;
    }
}
