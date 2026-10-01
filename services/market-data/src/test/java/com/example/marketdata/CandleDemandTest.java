package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
 *
 * <p><b>Горизонт стоящей группы пишется точечной записью, а не группой
 * целиком</b> (docs/lifecycles/CandleGroup.md §«Возврат к `BACKFILL` по
 * углублённому требованию»): полная запись вернула бы итог шага цикла к
 * снимку, прочитанному требованием. Поэтому у стоящей группы наблюдается
 * {@code saveDeepenedHorizon}, а {@code save} не зовётся вовсе.
 */
class CandleDemandTest {

    private static final String INSTRUMENT_INTERNAL_ID = "inst-1";
    private static final Long INSTRUMENT_ID = 1L;
    private static final Long GROUP_ID = 10L;

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
        givenStanding(groupWithHorizon(0L));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 100L);

        verify(candleGroupDataService, never()).save(any());
        verify(candleGroupDataService, never()).saveDeepenedHorizon(any(), any(), any());
    }

    /**
     * Требование глубже стоящего расширяет горизонт и возвращает группу к
     * бэкфиллу: без возврата статуса готовая группа осталась бы ACTIVE и
     * заказанной глубины не догрузила бы никогда. Гард записи — статус и
     * горизонт, застанные требованием.
     */
    @Test
    void deeperRequirementExtendsHorizonAndReopensBackfill() {
        Long shallow = System.currentTimeMillis();
        CandleGroup standing = groupWithHorizon(shallow);
        standing.setStatus(CandleGroup.Status.ACTIVE);
        givenStanding(standing);
        givenDeepeningWritten();

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).saveDeepenedHorizon(saved.capture(), eq(CandleGroup.Status.ACTIVE),
                eq(shallow));
        assertThat(saved.getValue().getPlannedFirstUtcMillis()).isLessThan(shallow);
        assertThat(saved.getValue().getStatus()).isEqualTo(CandleGroup.Status.BACKFILL);
        verify(candleGroupDataService, never()).save(any());
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
        CandleGroup standing = groupWithHorizon(System.currentTimeMillis());
        standing.setStatus(status);
        givenStanding(standing);
        givenDeepeningWritten();

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).saveDeepenedHorizon(saved.capture(), eq(status), any());
        assertThat(saved.getValue().getStatus()).isEqualTo(CandleGroup.Status.BACKFILL);
    }

    /**
     * Терминальная группа требованием не оживляется: исчерпанные попытки
     * докачки не начинаются заново от чужой команды. Горизонт при этом
     * расширяется — он величина группы, а не её статуса.
     */
    @ParameterizedTest
    @EnumSource(value = CandleGroup.Status.class, names = {"ERROR", "DELETED"})
    void deeperRequirementDoesNotResurrectTerminalGroup(CandleGroup.Status status) {
        CandleGroup standing = groupWithHorizon(System.currentTimeMillis());
        standing.setStatus(status);
        givenStanding(standing);
        givenDeepeningWritten();

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).saveDeepenedHorizon(saved.capture(), eq(status), any());
        assertThat(saved.getValue().getStatus()).isEqualTo(status);
        assertThat(saved.getValue().getPlannedFirstUtcMillis()).isLessThan(System.currentTimeMillis());
    }

    /**
     * Шаг цикла, записавший итог между чтением и записью требования, роняет
     * гард; группа перечитывается, и решение принимается по тому, что шаг
     * записал: перешедшая в {@code ERROR} группа не оживает, а горизонт
     * расширяется под гардом уже нового статуса.
     */
    @Test
    void groupRewrittenByALoadingStepIsReReadAndDecidedAgain() {
        Long shallow = System.currentTimeMillis();
        CandleGroup read = groupWithHorizon(shallow);
        read.setStatus(CandleGroup.Status.REPAIR);
        givenStanding(read);
        CandleGroup rewritten = groupWithHorizon(shallow);
        rewritten.setStatus(CandleGroup.Status.ERROR);
        rewritten.setCount(42L);
        when(candleGroupDataService.getRequiredById(GROUP_ID)).thenReturn(rewritten);
        when(candleGroupDataService.saveDeepenedHorizon(any(), eq(CandleGroup.Status.REPAIR), any()))
                .thenReturn(false);
        when(candleGroupDataService.saveDeepenedHorizon(any(), eq(CandleGroup.Status.ERROR), any()))
                .thenReturn(true);

        CandleGroup answered = demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        verify(candleGroupDataService, times(2)).saveDeepenedHorizon(any(), any(), eq(shallow));
        assertThat(answered.getStatus()).isEqualTo(CandleGroup.Status.ERROR);
        assertThat(answered.getCount()).isEqualTo(42L);
        assertThat(answered.getPlannedFirstUtcMillis()).isLessThan(shallow);
        verify(candleGroupDataService, never()).save(any());
    }

    /**
     * Требование мельче собранного горизонт не сужает: собранное заказал
     * кто-то другой, и выбрасывать его нельзя.
     */
    @Test
    void shallowerRequirementDoesNotShrinkHorizon() {
        givenStanding(groupWithHorizon(0L));

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1L);

        verify(candleGroupDataService, never()).save(any());
        verify(candleGroupDataService, never()).saveDeepenedHorizon(any(), any(), any());
    }

    /**
     * Стоящая «вся история» (пустой горизонт) — глубже всякого названного:
     * требование с глубиной её не сужает (docs/lifecycles/CandleGroup.md
     * §«Глубина и покрытие (`BACKFILL`)»).
     */
    @Test
    void namedDepthDoesNotNarrowTheWholeHistory() {
        CandleGroup standing = groupWithHorizon(null);
        standing.setStatus(CandleGroup.Status.ACTIVE);
        givenStanding(standing);

        CandleGroup answered = demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, 1000L);

        assertThat(answered.getPlannedFirstUtcMillis()).isNull();
        assertThat(answered.getStatus()).isEqualTo(CandleGroup.Status.ACTIVE);
        verify(candleGroupDataService, never()).saveDeepenedHorizon(any(), any(), any());
    }

    /**
     * Требование без глубины — требование всей доступной истории, а не
     * отсутствие требования: названный горизонт оно углубляет до пустого и
     * возвращает группу к бэкфиллу.
     */
    @Test
    void requirementWithoutDepthDeepensANamedHorizonToTheWholeHistory() {
        Long shallow = System.currentTimeMillis();
        CandleGroup standing = groupWithHorizon(shallow);
        standing.setStatus(CandleGroup.Status.ACTIVE);
        givenStanding(standing);
        givenDeepeningWritten();

        demandService.requireCandles(INSTRUMENT_INTERNAL_ID, TimeFrame.ONE_HOUR, null);

        ArgumentCaptor<CandleGroup> saved = ArgumentCaptor.forClass(CandleGroup.class);
        verify(candleGroupDataService).saveDeepenedHorizon(saved.capture(), eq(CandleGroup.Status.ACTIVE),
                eq(shallow));
        assertThat(saved.getValue().getPlannedFirstUtcMillis()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(CandleGroup.Status.BACKFILL);
    }

    private void givenInstrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        when(instrumentDataService.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID)).thenReturn(instrument);
    }

    private void givenStanding(CandleGroup standing) {
        givenInstrument();
        when(candleGroupDataService.findByInstrumentIdAndTimeframe(INSTRUMENT_ID, TimeFrame.ONE_HOUR))
                .thenReturn(Optional.of(standing));
    }

    private void givenDeepeningWritten() {
        when(candleGroupDataService.saveDeepenedHorizon(any(), any(), any())).thenReturn(true);
    }

    private CandleGroup groupWithHorizon(Long horizon) {
        CandleGroup group = new CandleGroup();
        group.setId(GROUP_ID);
        group.setInstrumentId(INSTRUMENT_ID);
        group.setTimeframe(TimeFrame.ONE_HOUR);
        group.setStatus(CandleGroup.Status.CREATED);
        group.setPlannedFirstUtcMillis(horizon);
        group.setCount(0L);
        return group;
    }
}
