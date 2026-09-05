package com.example.tradingbot.domain.command.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.resolve.PositionStatusResolver;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionCloseResultExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
 * Связывает обход двуногой команды добычи позиции с его домом
 * (docs/components/RefreshPositionExecutor.md §«Обход внутри одной
 * команды»).
 *
 * <p>Несущее для этого теста — ДВА дискриминатора четвёртой ветви и
 * различение эпизодов ПАРОЙ. Без признака наблюдения ветвь заводила бы
 * фантомную строку у всякой сделки между отправкой входной заявки и её
 * филлом; без «строк эпизода нет ни одной» — ещё одну строку каждым
 * проходом после закрытия эпизода; без пары переоткрытие под
 * переиспользованным идентификатором читалось бы как тот же эпизод, и
 * нога 2 по нему не срабатывала бы вовсе.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshPositionExecutorTest {

    private static final String INSTRUMENT = "ETH-USDT-SWAP";
    private static final OffsetDateTime FIRST = OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SECOND = OffsetDateTime.of(2026, 9, 1, 11, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLOSED_AT = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private PositionDataService positionDataService;
    @Mock
    private DealDataService dealDataService;

    @Mock
    private OrderDataService orderDataService;
    @Mock
    private DealActionStateDataService dealActionStateDataService;
    @Mock
    private IntegrationService integrationService;
    @Mock
    private PositionMapper positionMapper;
    @Mock
    private PositionStatusResolver positionStatusResolver;

    private RefreshPositionExecutor executor;
    private final List<Position> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executor = new RefreshPositionExecutor(positionDataService, dealDataService, orderDataService,
                dealActionStateDataService, integrationService, positionMapper, positionStatusResolver);
        when(positionDataService.save(any())).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            saved.add(position);
            return position;
        });
        when(positionStatusResolver.resolve(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == null
                        ? StatusResolveResult.of(Position.Status.CLOSED, Position.CloseReason.EXTERNAL_CLOSE)
                        : StatusResolveResult.of(Position.Status.ACTIVE, null));
        when(positionDataService.findEpisodes(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Тот же эпизод: строка обновляется на месте, вторая нога не запускается")
    void sameEpisodeUpdatesInPlace() {
        Position live = episode("pos-1", FIRST, Position.Status.ACTIVE);
        Deal deal = deal(List.of(live), Deal.EntryReason.STRATEGY);
        when(integrationService.getPosition(INSTRUMENT)).thenReturn(snapshot("pos-1", FIRST));
        DealActionState actionState = new DealActionState();

        assertTrue(executor.execute(null, actionState, context(deal)).getSuccess());

        assertEquals(List.of(live), saved);
        verify(integrationService, never()).getPositionCloseRecords(anyString(), any());
        assertEquals(DealActionStateStatus.COMPLETED, actionState.getStatus());
    }

    @Test
    @DisplayName("Переоткрытие под тем же идентификатором: живая строка закрывается, заводится новая")
    void reusedIdentifierWithNewCreationTimeOpensSecondEpisode() {
        Position live = episode("pos-1", FIRST, Position.Status.ACTIVE);
        Deal deal = deal(List.of(live), Deal.EntryReason.STRATEGY);
        when(integrationService.getPosition(INSTRUMENT)).thenReturn(snapshot("pos-1", SECOND));

        executor.execute(null, new DealActionState(), context(deal));

        assertEquals(2, saved.size());
        assertEquals(Position.Status.CLOSED, saved.getFirst().getStatus());
        assertEquals(Position.CloseReason.EXTERNAL_CLOSE, saved.getFirst().getCloseReason());
        assertEquals(Position.Status.ACTIVE, saved.get(1).getStatus());
    }

    @Test
    @DisplayName("Позиции нет, живая строка есть: строка закрывается, обход идёт на вторую ногу")
    void missingSnapshotClosesLiveRow() {
        Position live = episode("pos-1", FIRST, Position.Status.ACTIVE);
        Deal deal = deal(List.of(live), Deal.EntryReason.STRATEGY);
        when(integrationService.getPosition(INSTRUMENT)).thenReturn(null);
        when(positionDataService.findEpisodes(anyLong())).thenReturn(List.of(live));
        when(integrationService.getPositionCloseRecords(anyString(), any())).thenReturn(List.of());

        executor.execute(null, new DealActionState(), context(deal));

        assertEquals(Position.Status.CLOSED, saved.getFirst().getStatus());
        verify(integrationService, times(1)).getPositionCloseRecords(anyString(), any());
    }

    @Test
    @DisplayName("Позиция НЕ наблюдалась: фантомная строка не заводится, обход останавливается")
    void unobservedPositionDoesNotMaterializeRow() {
        Deal deal = deal(List.of(), Deal.EntryReason.STRATEGY);
        DealActionState actionState = new DealActionState();

        assertTrue(executor.execute(null, actionState, context(deal)).getSuccess());

        assertTrue(saved.isEmpty());
        verify(integrationService, never()).getPositionCloseRecords(anyString(), any());
        assertEquals(DealActionStateStatus.COMPLETED, actionState.getStatus());
    }

    @Test
    @DisplayName("Позиция наблюдалась, строк эпизода нет: заводится ЗАКРЫТАЯ строка без положения закрытия")
    void observedWithoutRowsMaterializesClosedStub() {
        Deal deal = deal(List.of(), Deal.EntryReason.RECOVERY);

        executor.execute(null, new DealActionState(), context(deal));

        Position stub = saved.getFirst();
        assertEquals(Position.Status.CLOSED, stub.getStatus());
        assertEquals(BigDecimal.ZERO, stub.getExternalSize());
        assertNull(stub.getExternalRealizedProfit());
    }

    @Test
    @DisplayName("Эпизод уже материализован: вторая строка каждым проходом не заводится")
    void materializedEpisodeIsNotDuplicated() {
        Position closed = episode("pos-1", FIRST, Position.Status.CLOSED);
        closed.setExternalRealizedProfit(BigDecimal.valueOf(-3));
        Deal deal = deal(List.of(closed), Deal.EntryReason.RECOVERY);
        when(positionDataService.findEpisodes(anyLong())).thenReturn(List.of(closed));
        DealActionState actionState = new DealActionState();

        executor.execute(null, actionState, context(deal));

        assertTrue(saved.isEmpty());
        assertEquals(DealActionStateStatus.COMPLETED, actionState.getStatus());
    }

    @Test
    @DisplayName("Записи закрытия нет: звено НЕ завершается, терминала команда не выносит")
    void missingCloseRecordLeavesActionOpen() {
        Position awaiting = episode("pos-1", FIRST, Position.Status.CLOSED);
        Deal deal = deal(List.of(awaiting), Deal.EntryReason.STRATEGY);
        when(positionDataService.findEpisodes(anyLong())).thenReturn(List.of(awaiting));
        when(integrationService.getPositionCloseRecords(anyString(), any())).thenReturn(List.of());
        DealActionState actionState = new DealActionState();

        assertTrue(executor.execute(null, actionState, context(deal)).getSuccess());

        assertNull(actionState.getStatus());
        verify(dealDataService, never()).advanceCoverageProvenThrough(anyLong(), any());
    }

    @Test
    @DisplayName("Запись найдена: строка наполняется и порог доказанного покрытия двигается")
    void foundCloseRecordFillsRowAndAdvancesCoverage() {
        Position awaiting = episode("pos-1", FIRST, Position.Status.CLOSED);
        Deal deal = deal(List.of(awaiting), Deal.EntryReason.STRATEGY);
        when(positionDataService.findEpisodes(anyLong()))
                .thenReturn(List.of(awaiting))
                .thenReturn(List.of(filled(awaiting)));
        when(integrationService.getPositionCloseRecords(anyString(), any()))
                .thenReturn(List.of(closeRecord("pos-1", FIRST)));
        DealActionState actionState = new DealActionState();

        executor.execute(null, actionState, context(deal));

        verify(positionMapper).updateFromCloseSnapshot(any(), any());
        verify(dealDataService).advanceCoverageProvenThrough(1L, CLOSED_AT);
        assertEquals(DealActionStateStatus.COMPLETED, actionState.getStatus());
    }

    @Test
    @DisplayName("Окно не адресуемо: писатель момента создания не отработал — записи не запрашиваются")
    void unresolvedWindowSkipsHistoryRequest() {
        Position awaiting = episode("pos-1", FIRST, Position.Status.CLOSED);
        Deal deal = deal(List.of(awaiting), Deal.EntryReason.STRATEGY);
        deal.setExternalCreatedAt(null);
        when(positionDataService.findEpisodes(anyLong())).thenReturn(List.of(awaiting));

        executor.execute(null, new DealActionState(), context(deal));

        verify(integrationService, never()).getPositionCloseRecords(anyString(), any());
    }

    private Position filled(Position source) {
        Position copy = episode(source.getExternalId(), source.getExternalCreatedAt(), Position.Status.CLOSED);
        copy.setExternalRealizedProfit(BigDecimal.valueOf(-3));
        return copy;
    }

    private Deal deal(List<Position> episodes, Deal.EntryReason entryReason) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setEntryReason(entryReason);
        deal.setExternalCreatedAt(FIRST);
        deal.setPositions(episodes);
        DealTranche tranche = new DealTranche();
        tranche.setEntryFilled(BigDecimal.ZERO);
        deal.setTranches(List.of(tranche));
        return deal;
    }

    private DealContext context(Deal deal) {
        Instrument instrument = new Instrument();
        instrument.setExternalId(INSTRUMENT);
        return DealContext.builder().deal(deal).instrument(instrument).build();
    }

    private Position episode(String externalId, OffsetDateTime createdAt, Position.Status status) {
        Position episode = new Position();
        episode.setDealId(1L);
        episode.setExternalId(externalId);
        episode.setExternalCreatedAt(createdAt);
        episode.setStatus(status);
        return episode;
    }

    private PositionExternalSnapshot snapshot(String externalId, OffsetDateTime createdAt) {
        return PositionExternalSnapshot.builder()
                .externalId(externalId)
                .externalCreatedAt(createdAt)
                .externalSize(BigDecimal.ONE)
                .direction(Position.Direction.LONG)
                .build();
    }

    private PositionCloseResultExternalSnapshot closeRecord(String posId, OffsetDateTime createdAt) {
        return PositionCloseResultExternalSnapshot.builder()
                .externalPosId(posId)
                .externalCreatedAt(createdAt)
                .externalModifiedAt(CLOSED_AT)
                .externalRealizedPnl(BigDecimal.valueOf(-3))
                .direction(Position.Direction.LONG)
                .build();
    }
}
