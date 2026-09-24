package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_EXTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.deals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.KillSwitchProperties;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.safety.KillSwitchExecutor;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Снятие риска радиуса вне графа сделок — группа `U15` документа
 * `.claude/tests/cases/trading-core-safety.md` (дом —
 * docs/components/KillSwitchExecutor.md §«Риск вне графа сделок»).
 *
 * <p><b>Базовая сборка:</b> исполнитель снятия риска; счёт резолвится по
 * ключу; клиент площадки подменён и отдаёт позиции счёта очередью ответов
 * — по одному на чтение; проекция инструментов знает валюту расчёта
 * инструментов контура; предел попыток — два.
 */
class KillSwitchOutsideDealsTest {

    private static final String FOREIGN_INSTRUMENT = "BTC-USDT-SWAP";
    private static final String OUTSIDE_CONTOUR = "DOGE-USDT-SWAP";
    private static final String SETTLE_CURRENCY = "USDT";

    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instruments = mock(InstrumentDataService.class);
    private final KillSwitchProperties properties = new KillSwitchProperties();

    private KillSwitchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KillSwitchExecutor(exchange, mock(ServiceCommandExecutor.class),
                mock(DealContextService.class), accounts, instruments, properties);
        properties.setMaxTeardownAttempts(2);
        when(accounts.getRequiredById(ACCOUNT_ID)).thenReturn(account());
        when(instruments.findExternalIdsByIds(any())).thenReturn(new HashSet<>());
        when(instruments.findSettlementCurrency(any(), anyString())).thenReturn(Optional.of(SETTLE_CURRENCY));
        when(instruments.findSettlementCurrency(any(), eq(OUTSIDE_CONTOUR)))
                .thenReturn(Optional.empty());
    }

    /** Позиция без сделки закрывается, и радиус подтверждает следующее чтение. */
    @Test
    @DisplayName("U15.1 — живая позиция на инструменте без сделки: закрыта с валютой проекции, радиус подтверждён")
    void u15_1_aPositionWithoutADealIsClosedAndTheScopeConfirmed() {
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID))
                .thenReturn(List.of(live(FOREIGN_INSTRUMENT)), List.of());

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, null, deals())).isTrue();

        verify(exchange, times(1)).closePosition(ACCOUNT_INTERNAL_ID, FOREIGN_INSTRUMENT, SETTLE_CURRENCY);
    }

    /** Позицию сделки снимает ход сделки: мимо её ног она здесь не закрывается. */
    @Test
    @DisplayName("U15.2 — живая позиция на инструменте нетерминальной сделки: не закрыта здесь, радиус не подтверждён")
    void u15_2_aDealPositionIsNotClosedHereAndLeavesTheScopeUnconfirmed() {
        List<Deal> population = deals(deal(71L));
        when(instruments.findExternalIdsByIds(any())).thenReturn(new HashSet<>(Set.of(INSTRUMENT_EXTERNAL_ID)));
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID)).thenReturn(List.of(live(INSTRUMENT_EXTERNAL_ID)));

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, null, population)).isFalse();

        verify(exchange, never()).closePosition(anyString(), anyString(), any());
    }

    /** Валюты расчёта у инструмента вне контура нет — закрытие уходит без неё. */
    @Test
    @DisplayName("U15.3 — позиция на инструменте вне контура: закрыта без валюты расчёта, радиус подтверждён")
    void u15_3_aPositionOutsideTheContourIsClosedWithoutASettlementCurrency() {
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID))
                .thenReturn(List.of(live(OUTSIDE_CONTOUR)), List.of());

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, null, deals())).isTrue();

        verify(exchange, times(1)).closePosition(ACCOUNT_INTERNAL_ID, OUTSIDE_CONTOUR, null);
    }

    /** Не добытые позиции подтверждением не считаются. */
    @Test
    @DisplayName("U15.4 — чтение позиций бросает: радиус не подтверждён")
    void u15_4_unreadPositionsAreNotAConfirmation() {
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID)).thenThrow(new IllegalStateException("connector is down"));

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, null, deals())).isFalse();
    }

    /** Радиус пары сужает срез своим инструментом. */
    @Test
    @DisplayName("U15.5 — радиус пары, живая позиция на другом инструменте: предметом не является, радиус подтверждён")
    void u15_5_thePairScopeIgnoresOtherInstruments() {
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID)).thenReturn(List.of(live(FOREIGN_INSTRUMENT)));

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, INSTRUMENT_EXTERNAL_ID, deals())).isTrue();

        verify(exchange, never()).closePosition(anyString(), anyString(), any());
    }

    /** Строка закрытой позиции живой не считается. */
    @Test
    @DisplayName("U15.6 — строка с нулевым размером: живой не считается, радиус подтверждён без закрытия")
    void u15_6_aZeroSizeRowIsNotALivePosition() {
        Position closed = live(FOREIGN_INSTRUMENT);
        closed.setExternalSize(BigDecimal.ZERO);
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID)).thenReturn(List.of(closed));

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, null, deals())).isTrue();

        verify(exchange, never()).closePosition(anyString(), anyString(), any());
    }

    /** Попытки ограничены пределом; отказ закрытия ход не срывает. */
    @Test
    @DisplayName("U15.7 — закрытие бросает на каждой попытке: закрытий ровно по пределу, радиус не подтверждён")
    void u15_7_aFailingCloseIsRetriedUpToTheLimit() {
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID)).thenReturn(List.of(live(FOREIGN_INSTRUMENT)));
        when(exchange.closePosition(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("exchange rejected"));

        assertThat(executor.closePositionsOutsideDeals(ACCOUNT_ID, null, deals())).isFalse();

        verify(exchange, times(2)).closePosition(ACCOUNT_INTERNAL_ID, FOREIGN_INSTRUMENT, SETTLE_CURRENCY);
    }

    /** Популяция радиуса отдаётся проекции ключами инструментов своих сделок. */
    @Test
    @DisplayName("U15.8 — инструменты сделок популяции резолвятся проекцией по их ключам")
    void u15_8_thePopulationInstrumentsAreResolvedByTheirKeys() {
        Deal deal = deal(72L);
        deal.setInstrumentId(INSTRUMENT_ID);
        when(exchange.getPositions(ACCOUNT_INTERNAL_ID)).thenReturn(List.of());

        executor.closePositionsOutsideDeals(ACCOUNT_ID, null, deals(deal));

        verify(instruments).findExternalIdsByIds(Set.of(INSTRUMENT_ID));
    }

    private static Position live(String externalInstrumentId) {
        Position position = new Position();
        position.setExternalInstrumentId(externalInstrumentId);
        position.setExternalSize(BigDecimal.ONE);
        return position;
    }
}
