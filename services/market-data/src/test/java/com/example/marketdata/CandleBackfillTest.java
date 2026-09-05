package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.marketdata.config.CandleLoadingProperties;
import com.example.marketdata.domain.service.CandleLoader;
import com.example.marketdata.integration.ExchangeReadClient;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Бэкфилл идёт до ЗАКАЗАННОГО горизонта группы.
 *
 * <p>Горизонт принадлежит группе, а не инструменту: глубину называет
 * требование потребителя вместе с таймфреймом
 * (docs/processes/candle-loading.md §«Кто заводит группу»). Тест держит
 * два условия остановки — достигнутый горизонт и конец истории площадки —
 * и одно условие продолжения: горизонт не назван.
 */
class CandleBackfillTest {

    private static final Long GROUP_ID = 10L;
    private static final Long INSTRUMENT_ID = 1L;
    private static final long HOUR = 3_600_000L;

    private final ExchangeReadClient readClient = mock(ExchangeReadClient.class);
    private final CandleDataService candleDataService = mock(CandleDataService.class);
    private final CandleGroupDataService candleGroupDataService = mock(CandleGroupDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final CandleLoadingProperties properties = new CandleLoadingProperties();

    private final CandleLoader loader = new CandleLoader(
            readClient, candleDataService, candleGroupDataService, instrumentDataService, properties);

    CandleBackfillTest() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("BTC-USDT-SWAP");
        when(instrumentDataService.getRequiredById(INSTRUMENT_ID)).thenReturn(instrument);
        when(candleGroupDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Пустой ответ площадки — история кончилась, бэкфилл завершён. */
    @Test
    void emptyPageEndsBackfill() {
        givenLoaded(null, null, 0L);
        when(readClient.getHistoryCandles(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        loader.advance(backfillGroup(0L));

        assertThat(savedStatus()).isEqualTo(CandleGroup.Status.CHECK);
    }

    /** Нижняя граница дошла до заказанного горизонта — бэкфилл завершён. */
    @Test
    void reachedHorizonEndsBackfill() {
        givenLoaded(1_000L, 2_000L, 2L);
        when(readClient.getHistoryCandles(anyString(), any(), any(), anyInt())).thenReturn(List.of(candle(1_000L)));

        loader.advance(backfillGroup(5_000L));

        assertThat(savedStatus()).isEqualTo(CandleGroup.Status.CHECK);
    }

    /**
     * Горизонт не назван — бэкфилл продолжается: требование без глубины
     * означает «вся доступная история», а не «истории не нужно».
     */
    @Test
    void absentHorizonKeepsBackfilling() {
        givenLoaded(1_000L, 2_000L, 2L);
        when(readClient.getHistoryCandles(anyString(), any(), any(), anyInt())).thenReturn(List.of(candle(1_000L)));

        loader.advance(backfillGroup(null));

        assertThat(savedStatus()).isEqualTo(CandleGroup.Status.BACKFILL);
    }

    /** Плотный ряд на проверке уходит в готовность, дырявый — в докачку. */
    @Test
    void checkRoutesByDensity() {
        givenLoaded(0L, HOUR, 2L);
        loader.advance(checkGroup());
        assertThat(savedStatus()).isEqualTo(CandleGroup.Status.ACTIVE);

        givenLoaded(0L, 2 * HOUR, 2L);
        loader.advance(checkGroup());
        assertThat(savedStatus()).isEqualTo(CandleGroup.Status.REPAIR);
    }

    private void givenLoaded(Long first, Long last, Long count) {
        when(candleDataService.count(GROUP_ID)).thenReturn(count);
        when(candleDataService.findMinOpenTimestamp(GROUP_ID)).thenReturn(first);
        when(candleDataService.findMaxOpenTimestamp(GROUP_ID)).thenReturn(last);
        when(candleDataService.saveCandles(anyLong(), any())).thenReturn(0);
    }

    private CandleGroup.Status savedStatus() {
        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        org.mockito.Mockito.verify(candleGroupDataService, org.mockito.Mockito.atLeastOnce())
                .save(saved.capture());
        return saved.getValue().getStatus();
    }

    private CandleGroup backfillGroup(Long horizon) {
        CandleGroup group = group();
        group.setStatus(CandleGroup.Status.BACKFILL);
        group.setPlannedFirstUtcMillis(horizon);
        return group;
    }

    private CandleGroup checkGroup() {
        CandleGroup group = group();
        group.setStatus(CandleGroup.Status.CHECK);
        return group;
    }

    private CandleGroup group() {
        CandleGroup group = new CandleGroup();
        group.setId(GROUP_ID);
        group.setInstrumentId(INSTRUMENT_ID);
        group.setTimeframe(TimeFrame.ONE_HOUR);
        group.setCount(0L);
        return group;
    }

    private Candle candle(Long openTimestamp) {
        Candle candle = new Candle();
        candle.setOpenTimestamp(openTimestamp);
        candle.setOpen(BigDecimal.ONE);
        candle.setHigh(BigDecimal.ONE);
        candle.setLow(BigDecimal.ONE);
        candle.setClose(BigDecimal.ONE);
        return candle;
    }
}
