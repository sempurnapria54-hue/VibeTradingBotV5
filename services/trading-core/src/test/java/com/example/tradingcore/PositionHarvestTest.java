package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.RefreshPositionExecutor;
import com.example.tradingcore.domain.command.resolve.PositionStatusResolver;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.mapping.PositionMapper;
import com.example.tradingcore.mapping.PositionMapperImpl;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Добыча состояния позиции: ветвление первой ноги, наполнение второй и
 * ось эпизода.
 *
 * <p><b>Предмет — различимость эпизодов.</b> Идентичность эпизода держит
 * ПАРА «биржевой идентификатор, биржевое время создания»: одного
 * идентификатора мало, источник переиспользует его у переоткрытой позиции.
 * Ошибка здесь направлена в разрешающую сторону — переоткрытый эпизод
 * читался бы прежним, вторая нога по нему не срабатывала бы вовсе, а
 * результат сделки считался бы по чужим числам.
 *
 * <p>Состояние моделей тест собирает настоящими полями и даёт предикатам
 * посчитаться самим (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»).
 */
class PositionHarvestTest {

    private static final Long DEAL_ID = 5L;
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";
    private static final OffsetDateTime OPENED_AT = OffsetDateTime.parse("2026-09-01T10:00:00Z");
    private static final OffsetDateTime REOPENED_AT = OffsetDateTime.parse("2026-09-01T12:00:00Z");

    private final PositionDataService positionDataService = mock(PositionDataService.class);
    private final OrderDataService orderDataService = mock(OrderDataService.class);
    private final DealActionStateDataService actionStateDataService = mock(DealActionStateDataService.class);
    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final DealRiskNumbersService riskNumbersService = mock(DealRiskNumbersService.class);
    private final DealDataService dealDataService = mock(DealDataService.class);
    private final PositionMapper positionMapper = new PositionMapperImpl();

    private final RefreshPositionExecutor executor = new RefreshPositionExecutor(positionDataService,
            orderDataService, actionStateDataService, exchange, positionMapper, new PositionStatusResolver(),
            riskNumbersService, dealDataService);

    /**
     * Тот же эпизод: пара совпала — внешние поля обновляются на месте,
     * история закрытых позиций не запрашивается вовсе.
     */
    @Test
    void sameEpisodeIsUpdatedInPlaceAndStopsTheTraversal() {
        Position live = episode(11L, "pos-1", OPENED_AT, Position.Status.ACTIVE, "2");
        Deal deal = deal(live);
        givenGraph(deal, List.of(live));
        when(exchange.getPosition(ACCOUNT, INSTRUMENT))
                .thenReturn(fetched("pos-1", OPENED_AT, "3"));

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(live.getExternalSize()).isEqualByComparingTo("3");
        assertThat(live.getStatus()).isEqualTo(Position.Status.ACTIVE);
        verify(exchange, never()).getPositionCloseRecords(any(), any(), any());
    }

    /**
     * Переиспользованный идентификатор при другом времени создания — это
     * ДРУГОЙ эпизод: прежняя строка закрывается, новая заводится. Совпади
     * различитель на одном идентификаторе — строка обновилась бы на месте,
     * и закрытый эпизод исчез бы из истории сделки.
     */
    @Test
    void reusedIdentifierWithAnotherCreationTimeOpensANewEpisode() {
        Position live = episode(11L, "pos-1", OPENED_AT, Position.Status.ACTIVE, "2");
        Deal deal = deal(live);
        givenGraph(deal, List.of(live));
        when(exchange.getPosition(ACCOUNT, INSTRUMENT))
                .thenReturn(fetched("pos-1", REOPENED_AT, "5"));

        executor.execute(command(), row(), context(deal));

        assertThat(live.getStatus()).isEqualTo(Position.Status.CLOSED);
        assertThat(live.getCloseReason()).isEqualTo(Position.CloseReason.EXTERNAL_CLOSE);
        assertThat(savedEpisodes()).anyMatch(saved -> "pos-1".equals(saved.getExternalId())
                && REOPENED_AT.equals(saved.getExternalCreatedAt())
                && Position.Status.ACTIVE.equals(saved.getStatus()));
    }

    /**
     * Позиции нет, живой строки нет, строк эпизода нет ни одной, но позиция
     * НАБЛЮДАЛАСЬ — заводится строка закрытого эпизода. Пропуск этой ветви
     * оставил бы граф неполным навсегда, и сделка висела бы активной,
     * занимая слот пары «счёт × инструмент».
     */
    @Test
    void observedButUnmaterializedEpisodeGetsAClosedRow() {
        Deal deal = deal();
        deal.setTranches(List.of(trancheWithFill()));
        givenGraph(deal, List.of());

        executor.execute(command(), row(), context(deal));

        assertThat(savedEpisodes()).anyMatch(saved -> Position.Status.CLOSED.equals(saved.getStatus())
                && saved.getExternalSize().signum() == 0);
    }

    /**
     * Второй дискриминатор той же ветви: позиция не наблюдалась — строка не
     * заводится. Без него фантомная строка появлялась бы у всякой сделки
     * между отправкой входной ноги и её филлом.
     */
    @Test
    void unobservedPositionMaterializesNothing() {
        Deal deal = deal();
        givenGraph(deal, List.of());

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        verify(positionDataService, never()).save(any());
        verify(exchange, never()).getPositionCloseRecords(any(), any(), any());
    }

    /**
     * Нога 2: запись закрытия ложится на СВОЮ строку по паре, порог
     * доказанного покрытия двигается вперёд, звено завершается.
     */
    @Test
    void closeRecordFillsItsOwnEpisodeAndAdvancesTheProvenThreshold() {
        Position closed = episode(11L, "pos-1", OPENED_AT, Position.Status.CLOSED, "0");
        Deal deal = deal(closed);
        deal.setBillsWindowBegin(OPENED_AT);
        givenGraph(deal, List.of(closed));
        when(exchange.getPosition(ACCOUNT, INSTRUMENT)).thenReturn(null);
        when(exchange.getPositionCloseRecords(ACCOUNT, INSTRUMENT, OPENED_AT))
                .thenReturn(List.of(closeRecord("pos-1", OPENED_AT, "17.5")));

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal));

        assertThat(closed.getExternalRealizedProfit()).isEqualByComparingTo("17.5");
        verify(dealDataService).advanceCoverageProvenThrough(eq(DEAL_ID), any());
        assertThat(result.getSuccess()).isTrue();
    }

    /**
     * Записи закрытия нет — факт не добыт: звено НЕ завершается и
     * повторяется по бюджету строки. Терминала команда при этом не выносит:
     * статус эпизода уже определён первой ногой.
     */
    @Test
    void missingCloseRecordLeavesTheLinkUnfinishedWithoutATerminal() {
        Position closed = episode(11L, "pos-1", OPENED_AT, Position.Status.CLOSED, "0");
        Deal deal = deal(closed);
        deal.setBillsWindowBegin(OPENED_AT);
        givenGraph(deal, List.of(closed));
        when(exchange.getPosition(ACCOUNT, INSTRUMENT)).thenReturn(null);
        when(exchange.getPositionCloseRecords(ACCOUNT, INSTRUMENT, OPENED_AT)).thenReturn(List.of());

        DealActionState row = row();
        ServiceCommandExecutionResult result = executor.execute(command(), row, context(deal));

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isNull();
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.SUBMITTED);
        assertThat(closed.getStatus()).isEqualTo(Position.Status.CLOSED);
    }

    /**
     * Ось эпизода — write-once и по НАЛИТЫМ ногам: без неё ноги закрытых
     * эпизодов неотличимы от ног текущего, и взятое считалось бы по всей
     * истории сделки.
     */
    @Test
    void episodeAxisIsAssignedOnceToFilledLegs() {
        Position live = episode(11L, "pos-1", OPENED_AT, Position.Status.ACTIVE, "2");
        Order filled = leg(1L, "1", null);
        Order untouched = leg(2L, "0", null);
        Order alreadyOnAnEpisode = leg(3L, "1", 99L);
        Deal deal = deal(live);
        deal.setTranches(List.of(tranche(filled, untouched, alreadyOnAnEpisode)));
        givenGraph(deal, List.of(live));
        when(exchange.getPosition(ACCOUNT, INSTRUMENT)).thenReturn(fetched("pos-1", OPENED_AT, "2"));

        executor.execute(command(), row(), context(deal));

        assertThat(filled.getPositionId()).isEqualTo(11L);
        assertThat(untouched.getPositionId()).isNull();
        assertThat(alreadyOnAnEpisode.getPositionId()).isEqualTo(99L);
    }

    /**
     * Неполный граф: числа риска не пишутся и звено не завершается — на
     * неполном графе они вышли бы заниженными, то есть ослабили бы
     * кумулятивный потолок.
     */
    @Test
    void incompleteGraphStopsTheLinkWithoutWritingNumbers() {
        Position live = episode(11L, "pos-1", OPENED_AT, Position.Status.ACTIVE, "2");
        Deal deal = deal(live);
        givenGraph(deal, List.of(live));
        when(exchange.getPosition(ACCOUNT, INSTRUMENT)).thenReturn(fetched("pos-1", OPENED_AT, "2"));
        DealContext context = context(deal);
        when(riskNumbersService.recompute(context)).thenReturn(false);

        DealActionState row = row();
        ServiceCommandExecutionResult result = executor.execute(command(), row, context);

        assertThat(result.getSuccess()).isFalse();
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.SUBMITTED);
    }

    private void givenGraph(Deal deal, List<Position> episodes) {
        when(positionDataService.findEpisodes(DEAL_ID)).thenReturn(new ArrayList<>(episodes));
        when(positionDataService.save(any())).thenAnswer(call -> call.getArgument(0));
        when(orderDataService.save(any())).thenAnswer(call -> call.getArgument(0));
        when(riskNumbersService.recompute(any())).thenReturn(true);
    }

    private List<Position> savedEpisodes() {
        ArgumentCaptor<Position> saved = ArgumentCaptor.forClass(Position.class);
        verify(positionDataService, atLeastOnce()).save(saved.capture());
        return saved.getAllValues();
    }

    private static Position episode(Long id, String externalId, OffsetDateTime createdAt, Position.Status status,
                                    String size) {
        Position episode = new Position();
        episode.setId(id);
        episode.setDealId(DEAL_ID);
        episode.setExternalId(externalId);
        episode.setExternalCreatedAt(createdAt);
        episode.setStatus(status);
        episode.setExternalSize(new BigDecimal(size));
        return episode;
    }

    private static Position fetched(String externalId, OffsetDateTime createdAt, String size) {
        Position position = new Position();
        position.setExternalId(externalId);
        position.setExternalCreatedAt(createdAt);
        position.setExternalSize(new BigDecimal(size));
        position.setDirection(Position.Direction.LONG);
        return position;
    }

    private static Position closeRecord(String externalId, OffsetDateTime createdAt, String realized) {
        Position record = new Position();
        record.setExternalId(externalId);
        record.setExternalCreatedAt(createdAt);
        record.setExternalModifiedAt(createdAt.plusHours(1));
        record.setExternalRealizedProfit(new BigDecimal(realized));
        record.setExternalResultCurrency("USDT");
        return record;
    }

    private static Order leg(Long id, String fill, Long positionId) {
        Order order = new Order();
        order.setId(id);
        order.setDealId(DEAL_ID);
        order.setType(Order.Type.ENTRY);
        order.setStatus(Order.Status.COMPLETED);
        order.setAccumulatedFillSize(new BigDecimal(fill));
        order.setPositionId(positionId);
        return order;
    }

    private static DealTranche tranche(Order... orders) {
        DealTranche tranche = new DealTranche();
        tranche.setId(7L);
        tranche.setDealId(DEAL_ID);
        tranche.setOrders(List.of(orders));
        return tranche;
    }

    private static DealTranche trancheWithFill() {
        DealTranche tranche = tranche(leg(1L, "1", null));
        tranche.setEntryFilled(new BigDecimal("1"));
        return tranche;
    }

    private static Deal deal(Position... episodes) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setPositions(new ArrayList<>(List.of(episodes)));
        deal.setExternalCreatedAt(OPENED_AT);
        return deal;
    }

    private static DealContext context(Deal deal) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(3L);
        account.setInternalId(ACCOUNT);
        Instrument instrument = new Instrument();
        instrument.setId(9L);
        instrument.setExternalId(INSTRUMENT);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .graphComplete(true)
                .build();
    }

    private static ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_POSITION_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(42L)
                .build();
    }

    private static DealActionState row() {
        DealActionState row = new DealActionState();
        row.setId(42L);
        row.setDealId(DEAL_ID);
        row.setStatus(DealActionStateStatus.SUBMITTED);
        return row;
    }
}
