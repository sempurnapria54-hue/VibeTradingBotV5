package com.example.tradingbot.domain.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.config.AnomalyJobProperties;
import com.example.tradingbot.domain.deal.DealOpeningService;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает детектор «активная позиция без сделки» с его реакцией —
 * восстановительной тропой создателя сделки
 * (docs/components/AnomalyJob.md, docs/components/DealOpeningService.md).
 *
 * <p>Несущее: <b>заведение сделки И ЕСТЬ реакция на эту аномалию</b>.
 * Без вызова значение {@code EntryReason.RECOVERY} не производит никто,
 * а найденный вне приложения живой риск остаётся вне модели — невидимым
 * всем механизмам, считающим по сделке. Прежде тропа была написана и
 * покрыта тестом, но в продуктовом коде её никто не звал.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyJobRecoveryTest {

    private static final String EXTERNAL_ID = "ETH-USDT-SWAP";
    private static final OffsetDateTime OPENED_AT =
            OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private InstrumentDataService instrumentDataService;
    @Mock
    private DealDataService dealDataService;
    @Mock
    private IntegrationService integrationService;
    @Mock
    private DealOpeningService dealOpeningService;

    private AnomalyJob job;

    @BeforeEach
    void setUp() {
        AnomalyJobProperties properties = new AnomalyJobProperties();
        job = new AnomalyJob(properties, new JobExecutionGuard(), instrumentDataService, dealDataService,
                integrationService, dealOpeningService);
        when(instrumentDataService.findByStatus(any())).thenReturn(List.of(instrument()));
    }

    @Test
    @DisplayName("Живая позиция без активной сделки заводит сделку восстановительной тропой")
    void unexplainedPositionTriggersRecovery() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);
        when(integrationService.getPositions()).thenReturn(List.of(snapshot(Position.Direction.LONG,
                BigDecimal.valueOf(3))));

        job.tick();

        verify(dealOpeningService).recoverDeal(eq(7L), eq(StrategyTradeDirection.LONG), eq(OPENED_AT));
    }

    @Test
    @DisplayName("Позицию, которую объясняет активная сделка, восстанавливать не надо")
    void explainedPositionIsNotRecovered() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);
        when(integrationService.getPositions()).thenReturn(List.of(snapshot(Position.Direction.LONG,
                BigDecimal.valueOf(3))));

        job.tick();

        verify(dealOpeningService, never()).recoverDeal(any(), any(), any());
    }

    @Test
    @DisplayName("Позиции на бирже нет — восстанавливать нечего")
    void absentPositionIsNotRecovered() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);
        when(integrationService.getPositions()).thenReturn(List.of());

        job.tick();

        verify(dealOpeningService, never()).recoverDeal(any(), any(), any());
    }

    @Test
    @DisplayName("Нулевой размер живым риском не считается")
    void zeroSizedPositionIsNotRecovered() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);
        when(integrationService.getPositions()).thenReturn(List.of(snapshot(Position.Direction.LONG,
                BigDecimal.ZERO)));

        job.tick();

        verify(dealOpeningService, never()).recoverDeal(any(), any(), any());
    }

    @Test
    @DisplayName("Неопределённое направление сделку не заводит: подставлять сторону нечем")
    void undeterminedDirectionIsNotRecovered() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);
        when(integrationService.getPositions()).thenReturn(List.of(snapshot(null, BigDecimal.valueOf(3))));

        job.tick();

        verify(dealOpeningService, never()).recoverDeal(any(), any(), any());
    }

    @Test
    @DisplayName("Выключенная джоба не ходит на биржу ни одним тиком")
    void disabledJobDoesNothing() {
        AnomalyJobProperties disabled = new AnomalyJobProperties();
        disabled.setEnabled(Boolean.FALSE);
        AnomalyJob offJob = new AnomalyJob(disabled, new JobExecutionGuard(), instrumentDataService,
                dealDataService, integrationService, dealOpeningService);

        offJob.tick();

        verify(integrationService, never()).getPositions();
    }

    @Test
    @DisplayName("Срез позиций читается ОДНИМ запросом на тик, сколько бы ни было инструментов")
    void positionsAreReadInOneRequestPerTick() {
        when(instrumentDataService.findByStatus(any()))
                .thenReturn(List.of(instrument(), instrument(), instrument()));
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);
        when(integrationService.getPositions()).thenReturn(List.of());

        job.tick();

        verify(integrationService, times(1)).getPositions();
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setExternalId(EXTERNAL_ID);
        instrument.setStatus(Instrument.Status.ACTIVE);
        return instrument;
    }

    private PositionExternalSnapshot snapshot(Position.Direction direction, BigDecimal size) {
        return PositionExternalSnapshot.builder()
                .externalId("pos-1")
                .externalInstrumentId(EXTERNAL_ID)
                .direction(direction)
                .externalSize(size)
                .externalCreatedAt(OPENED_AT)
                .build();
    }
}
