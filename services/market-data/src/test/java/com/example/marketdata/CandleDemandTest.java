package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketdata.domain.service.MarketDataDemandService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/**
 * Требование потребителя заводит единицу сбора и двигает горизонт.
 *
 * <p>Проверяются ровно те клеймы, на которых стои́т механизм: повтор
 * того же не заводит второй единицы, требование глубже расширяет
 * горизонт, требование мельче собранного его не сужает
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»).
 */
class CandleDemandTest {

    private static final String INSTRUMENT_INTERNAL_ID = "inst-1";
    private static final Long INSTRUMENT_ID = 1L;

    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final CandleGroupDataService candleGroupDataService = mock(CandleGroupDataService.class);
    private final ComputationConfigDataService configDataService = mock(ComputationConfigDataService.class);
    private final MarketDataDemandService demandService = new MarketDataDemandService(
            instrumentDataService, candleGroupDataService, configDataService);

    /** Первое требование заводит единицу сбора с заказанным горизонтом. */
    @Test
    void firstRequirementCreatesGroup() {
        givenInstrument();
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.empty());
        when(candleGroupDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CandleGroup created = demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 100L);

        assertThat(created.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(created.getTimeframe()).isEqualTo(TimeFrame.ONE_HOUR);
        assertThat(created.getStatus()).isEqualTo(CandleGroup.Status.CREATED);
        assertThat(created.getPlannedFirstUtcMillis()).isNotNull();
    }

    /**
     * Повтор того же требования не заводит второй единицы: требование
     * того же на то же — та же единица сбора.
     */
    @Test
    void repeatedRequirementDoesNotCreateSecondGroup() {
        givenInstrument();
        CandleGroup standing = groupWithHorizon(0L);
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.of(standing));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 100L);

        verify(candleGroupDataService, never()).save(any());
    }

    /**
     * Требование глубже стоящего расширяет горизонт и возвращает группу к
     * бэкфиллу: без возврата статуса готовая группа осталась бы ACTIVE и
     * заказанной глубины не догрузила бы никогда.
     */
    @Test
    void deeperRequirementExtendsHorizonAndReopensBackfill() {
        givenInstrument();
        CandleGroup standing = groupWithHorizon(System.currentTimeMillis());
        standing.setStatus(CandleGroup.Status.ACTIVE);
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.of(standing));
        when(candleGroupDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).save(saved.capture());
        assertThat(saved.getValue().getPlannedFirstUtcMillis()).isLessThan(System.currentTimeMillis());
        assertThat(saved.getValue().getStatus()).isEqualTo(CandleGroup.Status.BACKFILL);
    }

    /**
     * Группа, застигнутая посреди цикла, тоже возвращается к бэкфиллу.
     *
     * <p>Это НЕ повтор предыдущего случая: там группа была {@code ACTIVE},
     * и возврат держался на проверке готовности. Докачка хвоста уводит
     * группу из {@code ACTIVE} на каждом новом закрытом баре, и требование,
     * пришедшее в это окно, дошло бы по циклу до {@code ACTIVE} с
     * непокрытым горизонтом — то есть потерялось бы молча.
     */
    @ParameterizedTest
    @EnumSource(value = CandleGroup.Status.class,
            names = {"CREATED", "SYNC", "CHECK", "REPAIR", "ACTIVE"})
    void deeperRequirementReopensBackfillFromAnyLiveStatus(CandleGroup.Status status) {
        givenInstrument();
        CandleGroup standing = groupWithHorizon(System.currentTimeMillis());
        standing.setStatus(status);
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.of(standing));
        when(candleGroupDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CandleGroup.Status.BACKFILL);
    }

    /**
     * Терминальная группа требованием не оживляется: исчерпанные попытки
     * докачки не начинаются заново от чужой команды.
     */
    @ParameterizedTest
    @EnumSource(value = CandleGroup.Status.class, names = {"ERROR", "DELETED"})
    void deeperRequirementDoesNotResurrectTerminalGroup(CandleGroup.Status status) {
        givenInstrument();
        CandleGroup standing = groupWithHorizon(System.currentTimeMillis());
        standing.setStatus(status);
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.of(standing));
        when(candleGroupDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(status);
        assertThat(saved.getValue().getPlannedFirstUtcMillis()).isLessThan(System.currentTimeMillis());
    }

    /**
     * Требование мельче собранного горизонт не сужает: собранное заказал
     * кто-то другой, и выбрасывать его нельзя.
     */
    @Test
    void shallowerRequirementDoesNotShrinkHorizon() {
        givenInstrument();
        CandleGroup standing = groupWithHorizon(0L);
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.of(standing));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1L);

        verify(candleGroupDataService, never()).save(any());
    }

    private void givenInstrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        when(instrumentDataService.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID)).thenReturn(instrument);
    }

    private CandleGroup groupWithHorizon(Long horizon) {
        CandleGroup group = new CandleGroup();
        group.setId(10L);
        group.setInstrumentId(INSTRUMENT_ID);
        group.setTimeframe(TimeFrame.ONE_HOUR);
        group.setStatus(CandleGroup.Status.CREATED);
        group.setPlannedFirstUtcMillis(horizon);
        group.setCount(0L);
        return group;
    }
}
