package com.example.tradingbot.domain.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.safety.HoldClearanceGate;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает снятие биржевых ступеней с их домом
 * (docs/rules/exchange-hold.md §«Снятие — вручную и только в `HOLD`»,
 * docs/rules/manual-halt.md §«Снятие: что во что переходит и при каком
 * предусловии»).
 *
 * <p>Несущее для этого теста — <b>двухходовость снятия</b>: условий снятия
 * два, и лестница проверяет их по одному. Первый ход закрывает машинно
 * проверяемое («риска не осталось») и ведёт в мягкую ступень, а не в
 * рабочее состояние; второй — суждение держателя, и энфорсера у него нет.
 * До этого захода снятие вело сразу в {@code ACTIVE}, то есть первый же
 * вызов возвращал торговлю.
 *
 * <p><b>Отдельно проверяется, что гейт живого риска на второй ход НЕ
 * распространён.</b> Мягкая ступень принятый риск не снимала — живые
 * сделки под ней ведутся в полном объёме, — поэтому предусловие жёсткой
 * ступени сделало бы её неснимаемой ровно в её штатном состоянии.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeServiceTest {

    private static final String INTERNAL_ID = "EX-1";
    private static final Long EXCHANGE_ID = 5L;

    @Mock
    private ExchangeDataService exchangeDataService;

    @Mock
    private HoldClearanceGate holdClearanceGate;

    @InjectMocks
    private ExchangeService exchangeService;

    @Test
    @DisplayName("Заведение биржи ставит счётчики нулём: у обязательных колонок назван писатель")
    void createNamesWriterForMandatoryCounters() {
        Exchange created = new Exchange();
        when(exchangeDataService.save(created)).thenReturn(created);

        exchangeService.create(created);

        assertEquals(Exchange.Status.CREATED, created.getStatus());
        assertEquals(Integer.valueOf(0), created.getConsecutiveLossCount());
        assertEquals(Integer.valueOf(0), created.getBlindPassCount());
    }

    @Test
    @DisplayName("Снятие сворачивания ведёт в МЯГКУЮ ступень, а не в рабочее состояние")
    void unblockTradeLandsOnSoftRung() {
        given(Exchange.Status.TRADE_BLOCKED);
        when(holdClearanceGate.riskClearedOnExchange(EXCHANGE_ID)).thenReturn(true);
        when(exchangeDataService.unblockTrade(EXCHANGE_ID)).thenReturn(true);

        Exchange result = exchangeService.unblockTrade(INTERNAL_ID);

        assertEquals(Exchange.Status.HOLD, result.getStatus());
        verify(exchangeDataService).unblockTrade(EXCHANGE_ID);
        verify(exchangeDataService, never()).clearHold(anyLong());
    }

    @Test
    @DisplayName("Живой риск не доказан снятым — первый ход отвергается до записи статуса")
    void unblockTradeRefusedWhileRiskLives() {
        given(Exchange.Status.TRADE_BLOCKED);
        when(holdClearanceGate.riskClearedOnExchange(EXCHANGE_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> exchangeService.unblockTrade(INTERNAL_ID));

        verify(exchangeDataService, never()).unblockTrade(anyLong());
    }

    @Test
    @DisplayName("Второй ход возвращает биржу в работу и гейт живого риска НЕ спрашивает")
    void clearHoldDoesNotConsultRiskGate() {
        given(Exchange.Status.HOLD);
        when(exchangeDataService.clearHold(EXCHANGE_ID)).thenReturn(true);

        Exchange result = exchangeService.clearHold(INTERNAL_ID);

        assertEquals(Exchange.Status.ACTIVE, result.getStatus());
        verify(exchangeDataService).clearHold(EXCHANGE_ID);
        // Несущая проверка: предусловие ЖЁСТКОЙ ступени на мягкую не
        // распространяется — иначе мягкая ступень при живой сделке
        // (её штатное состояние) не снималась бы никогда.
        verifyNoInteractions(holdClearanceGate);
    }

    @Test
    @DisplayName("Прыжка через ступень нет: под сворачиванием второй ход не применяется")
    void clearHoldRefusedUnderHardRung() {
        given(Exchange.Status.TRADE_BLOCKED);
        when(exchangeDataService.clearHold(EXCHANGE_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> exchangeService.clearHold(INTERNAL_ID));
    }

    @Test
    @DisplayName("Мягкая ступень гасит новые входы наравне с жёсткой")
    void bothRungsBlockEntry() {
        assertEquals(Boolean.TRUE, exchangeWith(Exchange.Status.HOLD).blocksEntry());
        assertEquals(Boolean.TRUE, exchangeWith(Exchange.Status.TRADE_BLOCKED).blocksEntry());
        assertEquals(Boolean.FALSE, exchangeWith(Exchange.Status.ACTIVE).blocksEntry());
    }

    private void given(Exchange.Status status) {
        when(exchangeDataService.getRequiredByInternalId(INTERNAL_ID)).thenReturn(exchangeWith(status));
    }

    private Exchange exchangeWith(Exchange.Status status) {
        Exchange exchange = new Exchange();
        exchange.setId(EXCHANGE_ID);
        exchange.setInternalId(INTERNAL_ID);
        exchange.setStatus(status);
        return exchange;
    }
}
