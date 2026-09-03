package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.config.AnomalyJobProperties;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает гейт полноты прохода с его домом
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p>Несущее для этого теста — <b>что слепота счётна и имеет предел</b>:
 * неполный проход обязан оставить строку с первого раза, а серия неполных
 * — поднять МЯГКУЮ биржевую ступень. Жёсткая здесь снимала бы покрытый
 * риск по рынку из-за отказа канала.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyPassGateTest {

    private static final Long EXCHANGE_ID = 3L;

    @Mock
    private ExchangeDataService exchangeDataService;

    @Mock
    private AnomalyReportService reportService;

    @Mock
    private HoldService holdService;

    private final AnomalyJobProperties properties = new AnomalyJobProperties();

    private AnomalyPassGate passGate;

    @BeforeEach
    void setUp() {
        passGate = new AnomalyPassGate(exchangeDataService, reportService, holdService, properties);
    }

    @Test
    @DisplayName("Наблюдённый проход сбрасывает счёт слепоты и ступени не поднимает")
    void observedPassResetsCounter() {
        when(exchangeDataService.markPass(EXCHANGE_ID, true)).thenReturn(0);

        passGate.apply(true, exchange());

        verify(exchangeDataService).markPass(EXCHANGE_ID, true);
        verify(holdService, never()).raise(any(), any());
        verify(reportService, never()).journal(any(), any());
    }

    @Test
    @DisplayName("Первый ненаблюдённый проход оставляет строку и ступени НЕ поднимает")
    void firstBlindPassOnlyReports() {
        when(exchangeDataService.markPass(EXCHANGE_ID, false)).thenReturn(1);

        passGate.apply(false, exchange());

        verify(reportService).journal(any(), any());
        verify(holdService, never()).raise(any(), any());
    }

    @Test
    @DisplayName("Серия неполных проходов до предела поднимает МЯГКУЮ биржевую ступень")
    void blindLimitRaisesSoftExchangeRung() {
        when(exchangeDataService.markPass(EXCHANGE_ID, false)).thenReturn(properties.getBlindPassLimit());

        passGate.apply(false, exchange());

        HoldSignal expected = HoldSignal.exchangeSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE);
        verify(holdService).raise(eq(expected), any());
    }

    @Test
    @DisplayName("Ступень слепоты мягкая: принятый риск она не снимает")
    void blindnessRungDoesNotTearDownRisk() {
        assertEquals(Boolean.FALSE,
                HoldSignal.exchangeSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE).tearsDownRisk());
    }

    private Exchange exchange() {
        Exchange exchange = new Exchange();
        exchange.setId(EXCHANGE_ID);
        return exchange;
    }
}
