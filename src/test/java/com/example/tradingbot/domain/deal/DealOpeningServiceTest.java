package com.example.tradingbot.domain.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.DealTrancheDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает две тропы создания сделки с их домом
 * (docs/components/DealOpeningService.md).
 *
 * <p>Несущее для этого теста — ярлык тропы и биржевой момент создания.
 * Ярлык сервис ставит сам: пришедший параметром, он мог бы разойтись с
 * тропой, а на нём стои́т признак «позиция по сделке наблюдалась».
 * Момент создания у восстановленной сделки несёт время ОТКРЫТИЯ
 * наблюдённой позиции — единственный операнд нижней границы окна
 * линковки, потому что своей входной заявки такая сделка не отправит
 * никогда.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DealOpeningServiceTest {

    private static final OffsetDateTime MOMENT = OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private DealDataService dealDataService;
    @Mock
    private DealTrancheDataService dealTrancheDataService;

    private DealOpeningService service;
    private final List<DealTranche> savedTranches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DealOpeningService(dealDataService, dealTrancheDataService);
        when(dealDataService.save(any())).thenAnswer(invocation -> {
            Deal deal = invocation.getArgument(0);
            deal.setId(1L);
            return deal;
        });
        when(dealTrancheDataService.save(any())).thenAnswer(invocation -> {
            DealTranche tranche = invocation.getArgument(0);
            savedTranches.add(tranche);
            return tranche;
        });
    }

    @Test
    @DisplayName("Входная тропа: ярлык STRATEGY, фаза и биржевой момент ложатся, транш — в PRECHECK")
    void strategyPathWritesLabelPhaseAndMoment() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        Deal deal = service.openDeal(7L, 42L, StrategyTradeDirection.LONG, Deal.EntryStepType.ENTRY,
                MarketPhase.Type.BULL_TREND, MOMENT).orElseThrow();

        assertEquals(Deal.EntryReason.STRATEGY, deal.getEntryReason());
        assertEquals(42L, deal.getStrategyDetailId());
        assertEquals(MarketPhase.Type.BULL_TREND, deal.getEntryMarketPhase());
        assertEquals(MOMENT, deal.getExternalCreatedAt());
        assertEquals(DealTranche.Status.PRECHECK, savedTranches.getFirst().getStatus());
        assertEquals(1, savedTranches.getFirst().getEpisodeSeq());
    }

    @Test
    @DisplayName("Восстановительная тропа: деталь пуста, ярлык RECOVERY, транш сразу в сопровождении")
    void recoveryPathLeavesDetailEmptyAndManagesTranche() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        Deal deal = service.recoverDeal(7L, StrategyTradeDirection.SHORT, MOMENT).orElseThrow();

        assertEquals(Deal.EntryReason.RECOVERY, deal.getEntryReason());
        assertNull(deal.getStrategyDetailId());
        assertNull(deal.getEntryMarketPhase());
        assertEquals(MOMENT, deal.getExternalCreatedAt());
        assertTrue(deal.positionObserved());
        assertEquals(DealTranche.Status.MANAGING, savedTranches.getFirst().getStatus());
    }

    @Test
    @DisplayName("Инструмент уже объясняется активной сделкой: не заводится ни сделка, ни транш")
    void activeDealBlocksBothPaths() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);

        assertEquals(Optional.empty(), service.openDeal(7L, 42L, StrategyTradeDirection.LONG,
                Deal.EntryStepType.ENTRY, MarketPhase.Type.BULL_TREND, MOMENT));
        assertEquals(Optional.empty(), service.recoverDeal(7L, StrategyTradeDirection.LONG, MOMENT));

        verify(dealDataService, never()).save(any());
        assertTrue(savedTranches.isEmpty());
    }
}
