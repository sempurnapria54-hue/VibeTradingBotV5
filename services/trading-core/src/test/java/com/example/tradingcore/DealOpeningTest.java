package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.deal.DealOpeningService;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Создание сделки: две тропы, причина заведения и материализация траншей.
 *
 * <p><b>Что здесь проверяется по существу.</b> Причина заведения,
 * пришедшая параметром, могла бы разойтись с тропой — а на ней стои́т
 * предикат «позиция по сделке наблюдалась», и расхождение пропустило бы
 * сделку с наблюдённой позицией как сделку без неё. Восстановительная
 * тропа, получившая статусные ворота входной, оставила бы живой риск вне
 * модели. Шаблонное объявление, материализованное одним траншем,
 * потеряло бы объявленные уровни сетки молча.
 */
class DealOpeningTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long INSTRUMENT_ID = 3L;
    private static final Long DEAL_ID = 7L;
    private static final Long DECLARATION_ID = 31L;
    private static final Long GRID_DECLARATION_ID = 32L;
    private static final OffsetDateTime MOMENT =
            OffsetDateTime.of(2026, 9, 6, 10, 0, 0, 0, ZoneOffset.UTC);

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealTrancheDataService dealTrancheDataService = mock(DealTrancheDataService.class);

    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);

    private final DealOpeningService service = new DealOpeningService(dealDataService,
            dealTrancheDataService, outboxWriter);

    // --- входная тропа -----------------------------------------------------

    /**
     * Причину заведения ставит сам сервис значением своей тропы, а не
     * принимает параметром; счёт, момент создания, фаза входа и
     * закреплённая деталь едут той же транзакцией.
     */
    @Test
    void theStrategyPathStampsItsOwnEntryReason() {
        stubSave();

        service.openDeal(account(), instrument(), detail(declaration(DECLARATION_ID, null)),
                StrategyTradeDirection.LONG, MarketPhase.Type.BULL_TREND, MOMENT);

        Deal saved = savedDeal();
        assertThat(saved.getEntryReason()).isEqualTo(Deal.EntryReason.STRATEGY);
        assertThat(saved.getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(saved.getExchangeAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(saved.getDirection()).isEqualTo(StrategyTradeDirection.LONG);
        assertThat(saved.getEntryMarketPhase()).isEqualTo(MarketPhase.Type.BULL_TREND);
        assertThat(saved.getExternalCreatedAt()).isEqualTo(MOMENT);
        assertThat(saved.getStrategyDetailId()).isEqualTo(21L);
        assertThat(saved.getInternalId()).isNotBlank();
    }

    /**
     * Материализация эагерна и идёт ПО ОБЪЯВЛЕНИЯМ: по одному траншу на
     * простое объявление, по {@code levelCount} — на шаблон. Уровень несёт
     * только шаблон: у нешаблонного объявления смещать нечего.
     */
    @Test
    void tranchesAreMaterializedPerDeclarationAndPerLevel() {
        stubSave();
        StrategyDetail detail = detail(declaration(DECLARATION_ID, null),
                declaration(GRID_DECLARATION_ID, 3));

        service.openDeal(account(), instrument(), detail, StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, MOMENT);

        List<DealTranche> tranches = savedTranches();
        assertThat(tranches).hasSize(4);
        assertThat(tranches).allSatisfy(tranche -> {
            assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.PRECHECK);
            assertThat(tranche.getEpisodeSeq()).isEqualTo(1);
            assertThat(tranche.getDealId()).isEqualTo(DEAL_ID);
            assertThat(tranche.getEntryStepType()).isEqualTo(DealTranche.EntryStepType.ENTRY);
            assertThat(tranche.getInternalId()).isNotBlank();
        });
        assertThat(tranches.getFirst().getStrategyTrancheId()).isEqualTo(DECLARATION_ID);
        assertThat(tranches.getFirst().getLevel()).isNull();
        assertThat(tranches.stream()
                .filter(tranche -> GRID_DECLARATION_ID.equals(tranche.getStrategyTrancheId()))
                .map(DealTranche::getLevel))
                .containsExactly(0, 1, 2);
    }

    /**
     * Финальная защитная проверка: слот пары занят — сделка не пишется, и
     * ни одного транша не материализуется.
     */
    @Test
    void anOccupiedPairRefusesTheStrategyPath() {
        when(dealDataService.existsActiveOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(Boolean.TRUE);

        Optional<Deal> opened = service.openDeal(account(), instrument(),
                detail(declaration(DECLARATION_ID, null)), StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, MOMENT);

        assertThat(opened).isEmpty();
        verify(dealDataService, never()).save(any());
        verify(dealTrancheDataService, never()).save(any());
    }

    // --- восстановительная тропа -------------------------------------------

    /**
     * Восстановление: своя причина заведения, деталь НЕ закрепляется —
     * выбора входа не было, — а транш один, без объявления и сразу в
     * сопровождении: штатные рёбра входа ему недостижимы.
     */
    @Test
    void theRecoveryPathOpensAnUndeclaredTrancheInManaging() {
        stubSave();

        service.recoverDeal(account(), instrument(), StrategyTradeDirection.SHORT, MOMENT);

        Deal saved = savedDeal();
        assertThat(saved.getEntryReason()).isEqualTo(Deal.EntryReason.RECOVERY);
        assertThat(saved.getStrategyDetailId()).isNull();
        assertThat(saved.getEntryMarketPhase()).isNull();
        assertThat(saved.getExternalCreatedAt()).isEqualTo(MOMENT);
        List<DealTranche> tranches = savedTranches();
        assertThat(tranches).singleElement().satisfies(tranche -> {
            assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.MANAGING);
            assertThat(tranche.getStrategyTrancheId()).isNull();
            assertThat(tranche.getLevel()).isNull();
            assertThat(tranche.getEntryStepType()).isNull();
            assertThat(tranche.getEpisodeSeq()).isEqualTo(1);
        });
    }

    /**
     * Радиус защитной проверки восстановления — ПАРА: позицию, которую
     * объясняет сделка того же счёта, восстанавливать не надо, а сделка
     * соседнего счёта её не объясняет.
     */
    @Test
    void anExplainedPairRefusesTheRecoveryPath() {
        when(dealDataService.existsActiveOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(Boolean.TRUE);

        Optional<Deal> recovered = service.recoverDeal(account(), instrument(),
                StrategyTradeDirection.SHORT, MOMENT);

        assertThat(recovered).isEmpty();
        verify(dealDataService, never()).save(any());
    }

    /**
     * Эпизод позиции сервис не заводит ни на одной тропе: строку эпизода
     * материализует добыча состояния ближайшим проходом уже созданной
     * сделки.
     */
    @Test
    void neitherPathMaterializesThePositionEpisode() {
        stubSave();

        service.recoverDeal(account(), instrument(), StrategyTradeDirection.SHORT, MOMENT);

        assertThat(savedDeal().getPositions()).isNullOrEmpty();
    }

    // --- сборка ------------------------------------------------------------

    private void stubSave() {
        when(dealDataService.save(any())).thenAnswer(invocation -> {
            Deal deal = invocation.getArgument(0);
            deal.setId(DEAL_ID);
            return deal;
        });
        when(dealTrancheDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Deal savedDeal() {
        ArgumentCaptor<Deal> captor = ArgumentCaptor.forClass(Deal.class);
        verify(dealDataService).save(captor.capture());
        return captor.getValue();
    }

    private List<DealTranche> savedTranches() {
        ArgumentCaptor<DealTranche> captor = ArgumentCaptor.forClass(DealTranche.class);
        verify(dealTrancheDataService, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private ExchangeAccount account() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId("ea-0001");
        account.setTenantId("tn-0001");
        return account;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId("in-0001");
        return instrument;
    }

    private StrategyDetail detail(StrategyTranche... declarations) {
        StrategyDetail detail = new StrategyDetail();
        detail.setId(21L);
        detail.setMarketPhaseType(MarketPhase.Type.BULL_TREND);
        detail.setTranches(new ArrayList<>(List.of(declarations)));
        return detail;
    }

    /** Объявление с входным шагом; {@code levelCount} непуст только у шаблона. */
    private StrategyTranche declaration(Long id, Integer levelCount) {
        StrategyStep entryStep = new StrategyStep();
        entryStep.setId(50L + id);
        entryStep.setStepType(StrategyStepType.ENTRY);
        Map<DealTranche.Status, List<StrategyStep>> steps = new LinkedHashMap<>();
        steps.put(DealTranche.Status.PRECHECK, new ArrayList<>(List.of(entryStep)));
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(id);
        declaration.setKey("declaration-" + id);
        declaration.setLevelCount(levelCount);
        declaration.setStepsByStatus(steps);
        return declaration;
    }
}
