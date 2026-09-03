package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.util.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает мягкую ступень инструмента с её домом
 * (docs/rules/instrument-hold.md §Правило, §Enforcement; журнальная тропа
 * отчёта — docs/lifecycles/AnomalyReport.md §«Две тропы обработки»).
 *
 * <p>Несущее для этого теста — <b>что мягкая форма вообще существует</b>:
 * до этого захода реактивный контур был по определению CRITICAL, значение
 * {@code ENTRY_BLOCKED} писателя не имело, и три объявленных триггера
 * мягкой ступени поднять реакцию не могли ничем. Проверяются обе стороны
 * разведения: мягкая риска не рвёт, жёсткая идёт прежней тропой целиком.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldServiceTest {

    private static final Long INSTRUMENT_ID = 7L;

    private static final Long EXCHANGE_ID = 2L;

    @Mock
    private InstrumentDataService instrumentDataService;

    @Mock
    private ExchangeDataService exchangeDataService;

    @Mock
    private AnomalyReportService anomalyReportService;

    @Mock
    private SafetyHoldCoordinator safetyHoldCoordinator;

    @InjectMocks
    private HoldService holdService;

    @Test
    @DisplayName("Мягкая ступень запрещает входы и пишет журнальный отчёт, риска не снимая")
    void softRungBlocksEntryWithoutTeardown() {
        when(instrumentDataService.blockEntry(INSTRUMENT_ID)).thenReturn(true);
        HoldSignal signal = HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_PROTECTION_FATE_UNKNOWN);

        holdService.raise(signal, context());

        verify(instrumentDataService).blockEntry(INSTRUMENT_ID);
        verify(anomalyReportService).journal(any(), eqSignal(signal));
        verify(instrumentDataService, never()).blockTrade(anyLong());
        verify(safetyHoldCoordinator, never()).react(any(), any());
    }

    @Test
    @DisplayName("Стоящая ступень поглощает повтор: отчёт вторым проходом не множится")
    void standingRungAbsorbsRepeat() {
        when(instrumentDataService.blockEntry(INSTRUMENT_ID)).thenReturn(false);

        holdService.raise(HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_PROTECTION_FATE_UNKNOWN), context());

        verify(anomalyReportService, never()).journal(any(), any());
    }

    @Test
    @DisplayName("Жёсткая ступень уходит координатору целиком: состав её реакции здесь не повторяется")
    void hardRungGoesToCoordinator() {
        HoldSignal signal = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);

        holdService.raise(signal, context());

        verify(safetyHoldCoordinator).react(eqSignal(signal), any());
        verify(instrumentDataService, never()).blockEntry(anyLong());
        verify(anomalyReportService, never()).journal(any(), any());
    }

    @Test
    @DisplayName("Сигнала нет — реакции нет: пустой вход не считается основанием")
    void emptySignalRaisesNothing() {
        holdService.raise(null, context());

        verify(instrumentDataService, never()).blockEntry(anyLong());
        verify(safetyHoldCoordinator, never()).react(any(), any());
    }

    @Test
    @DisplayName("Мягкая БИРЖЕВАЯ ступень поднимается и пишет отчёт — инструмент при этом не трогается")
    void softExchangeRungIsRaised() {
        when(exchangeDataService.blockEntry(EXCHANGE_ID)).thenReturn(true);
        HoldSignal signal = HoldSignal.exchangeSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE);

        holdService.raise(signal, context());

        verify(exchangeDataService).blockEntry(EXCHANGE_ID);
        verify(anomalyReportService).journal(any(), eqSignal(signal));
        verify(instrumentDataService, never()).blockEntry(anyLong());
        verify(safetyHoldCoordinator, never()).react(any(), any());
    }

    @Test
    @DisplayName("Стоящая биржевая ступень поглощает повтор: отчёт вторым проходом не множится")
    void standingExchangeRungAbsorbsRepeat() {
        when(exchangeDataService.blockEntry(EXCHANGE_ID)).thenReturn(false);

        holdService.raise(HoldSignal.exchangeSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE), context());

        verify(anomalyReportService, never()).journal(any(), any());
    }

    @Test
    @DisplayName("Ступень несёт сам сигнал: жёсткая рвёт принятый риск, мягкая — нет")
    void rungCarriesTheAxis() {
        assertEquals(Boolean.TRUE, HoldSignal.instrument("X").tearsDownRisk());
        assertEquals(Boolean.TRUE, HoldSignal.exchange("X").tearsDownRisk());
        assertEquals(Boolean.FALSE, HoldSignal.instrumentSoft("X").tearsDownRisk());
        assertEquals(HoldRung.SOFT, HoldSignal.instrumentSoft("X").getRung());
    }

    private HoldSignal eqSignal(HoldSignal signal) {
        return org.mockito.ArgumentMatchers.eq(signal);
    }

    private DealContext context() {
        Deal deal = new Deal();
        deal.setId(1L);
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("BTC-USDT-SWAP");
        Exchange exchange = new Exchange();
        exchange.setId(2L);
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .exchange(exchange)
                .build();
    }
}
