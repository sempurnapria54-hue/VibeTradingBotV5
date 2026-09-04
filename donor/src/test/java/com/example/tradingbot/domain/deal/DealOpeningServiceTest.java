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
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.DealTrancheDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>Несущее для этого теста — ярлык тропы, биржевой момент создания и
 * <b>материализация траншей ПО ОБЪЯВЛЕНИЯМ детали</b>: по одному на
 * объявление, по {@code levelCount} на шаблон. Ярлык сервис ставит сам:
 * пришедший параметром, он мог бы разойтись с тропой, а на нём стои́т
 * признак «позиция по сделке наблюдалась». Момент создания у
 * восстановленной сделки несёт время ОТКРЫТИЯ наблюдённой позиции —
 * единственный операнд нижней границы окна линковки, потому что своей
 * входной заявки такая сделка не отправит никогда.
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

        Deal deal = service.openDeal(7L, detail(singleTranche()), StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, MOMENT).orElseThrow();

        assertEquals(Deal.EntryReason.STRATEGY, deal.getEntryReason());
        assertEquals(42L, deal.getStrategyDetailId());
        assertEquals(MarketPhase.Type.BULL_TREND, deal.getEntryMarketPhase());
        assertEquals(MOMENT, deal.getExternalCreatedAt());
        assertEquals(DealTranche.Status.PRECHECK, savedTranches.getFirst().getStatus());
        assertEquals(1, savedTranches.getFirst().getEpisodeSeq());
    }

    @Test
    @DisplayName("Одно объявление — один транш: уровня у нешаблонного нет, тип входа читается объявлением")
    void singleDeclarationMaterializesOneTranche() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        service.openDeal(7L, detail(singleTranche()), StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, MOMENT).orElseThrow();

        assertEquals(1, savedTranches.size());
        assertEquals(100L, savedTranches.getFirst().getStrategyTrancheId());
        assertNull(savedTranches.getFirst().getLevel(), "уровень несёт только шаблон");
        assertEquals(DealTranche.EntryStepType.ENTRY, savedTranches.getFirst().getEntryStepType());
    }

    @Test
    @DisplayName("Шаблон с levelCount = 3 материализует три транша с уровнями 0..2")
    void templateMaterializesLevelCountTranches() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        service.openDeal(7L, detail(gridTranche(3)), StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, MOMENT).orElseThrow();

        assertEquals(3, savedTranches.size());
        assertEquals(List.of(0, 1, 2), savedTranches.stream().map(DealTranche::getLevel).toList());
        assertTrue(savedTranches.stream().allMatch(tranche -> DealTranche.Status.PRECHECK.equals(tranche.getStatus())));
        assertTrue(savedTranches.stream()
                .allMatch(tranche -> DealTranche.EntryStepType.GRID_ENTRY.equals(tranche.getEntryStepType())));
    }

    @Test
    @DisplayName("Два объявления материализуются оба: сетка и одиночный вход в одной детали")
    void everyDeclarationIsMaterialized() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        service.openDeal(7L, detail(gridTranche(2), singleTranche()), StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, MOMENT).orElseThrow();

        assertEquals(3, savedTranches.size());
        assertEquals(2, savedTranches.stream()
                .filter(tranche -> Long.valueOf(101L).equals(tranche.getStrategyTrancheId())).count());
        assertEquals(1, savedTranches.stream()
                .filter(tranche -> Long.valueOf(100L).equals(tranche.getStrategyTrancheId())).count());
    }

    @Test
    @DisplayName("Восстановительная тропа: деталь пуста, ярлык RECOVERY, транш сразу в сопровождении без объявления")
    void recoveryPathLeavesDetailEmptyAndManagesTranche() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        Deal deal = service.recoverDeal(7L, StrategyTradeDirection.SHORT, MOMENT).orElseThrow();

        assertEquals(Deal.EntryReason.RECOVERY, deal.getEntryReason());
        assertNull(deal.getStrategyDetailId());
        assertNull(deal.getEntryMarketPhase());
        assertEquals(MOMENT, deal.getExternalCreatedAt());
        assertTrue(deal.positionObserved());
        assertEquals(DealTranche.Status.MANAGING, savedTranches.getFirst().getStatus());
        assertNull(savedTranches.getFirst().getStrategyTrancheId(), "объявления у восстановленного нет");
        assertNull(savedTranches.getFirst().getEntryStepType());
    }

    @Test
    @DisplayName("Инструмент уже объясняется активной сделкой: не заводится ни сделка, ни транш")
    void activeDealBlocksBothPaths() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);

        assertEquals(Optional.empty(), service.openDeal(7L, detail(singleTranche()),
                StrategyTradeDirection.LONG, MarketPhase.Type.BULL_TREND, MOMENT));
        assertEquals(Optional.empty(), service.recoverDeal(7L, StrategyTradeDirection.LONG, MOMENT));

        verify(dealDataService, never()).save(any());
        assertTrue(savedTranches.isEmpty());
    }

    private StrategyDetail detail(StrategyTranche... declarations) {
        StrategyDetail detail = new StrategyDetail();
        detail.setId(42L);
        detail.setTranches(List.of(declarations));
        return detail;
    }

    private StrategyTranche singleTranche() {
        return declaration(100L, "single", 1, null, StrategyStepType.ENTRY);
    }

    private StrategyTranche gridTranche(int levelCount) {
        return declaration(101L, "grid", levelCount, new BigDecimal("10"), StrategyStepType.GRID_ENTRY);
    }

    private StrategyTranche declaration(Long id, String key, int levelCount, BigDecimal levelStep,
                                        StrategyStepType entryType) {
        StrategyStep entryStep = new StrategyStep();
        entryStep.setStepType(entryType);
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(id);
        declaration.setKey(key);
        declaration.setLevelCount(levelCount);
        declaration.setLevelStep(levelStep);
        declaration.setStepsByStatus(Map.of(DealTranche.Status.PRECHECK, List.of(entryStep)));
        return declaration;
    }
}
