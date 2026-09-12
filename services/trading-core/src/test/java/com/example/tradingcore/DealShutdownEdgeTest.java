package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealShutdownEdgeException;
import com.example.tradingcore.domain.deal.DealStatusEdgeService;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.mapping.CoreEventMessageMapper;
import com.example.tradingcore.mapping.CoreEventMessageMapperImpl;
import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.OutboxDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Факт остановки сделки пишет то же звено, что применяет ребро, и той же
 * транзакцией (docs/architecture/contracts.md §«У каждого класса события
 * назван писатель, и он же писатель решения»).
 *
 * <p><b>Предмет проверки — на каком ребре событие есть, а на каком его
 * нет.</b> Класс заведён ради клейма таймлайна: «почему сделка перестала
 * вестись штатно сейчас». Событие на ребре, которого не было, объявило бы
 * фактом несостоявшийся ход; отсутствие события на ребре присвоения
 * оставило бы вопрос без ответа, а другого носителя у него нет
 * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
 *
 * <p><b>Транзакционность прогоном не мерится</b>, и это названо: границу
 * держит объявление метода, а её исполнение — предмет каркаса. Мерится то,
 * что писатель события и писатель решения — одно звено.
 */
class DealShutdownEdgeTest {

    /** Маппер domain → message: формы событий строит граница, а не домен. */
    private static final CoreEventMessageMapper EVENT_MESSAGES = new CoreEventMessageMapperImpl();

    private static final String TENANT = "tn-0001";
    private static final String ACCOUNT_INTERNAL_ID = "ea-0001";
    private static final String INSTRUMENT_INTERNAL_ID = "in-0001";
    private static final String STRATEGY_INTERNAL_ID = "st-0001";
    private static final String DEAL_INTERNAL_ID = "dl-0001";
    private static final Long ACCOUNT_ID = 4L;
    private static final Long INSTRUMENT_ID = 5L;
    private static final Long DEAL_ID = 1L;
    private static final Long DETAIL_ID = 31L;

    private final OutboxDataService outboxDataService = mock(OutboxDataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CoreEventWriter coreEventWriter = new CoreEventWriter(outboxDataService, objectMapper, EVENT_MESSAGES);

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instruments = mock(InstrumentDataService.class);
    private final StrategyDataService strategies = mock(StrategyDataService.class);

    private final DealStatusEdgeService service = new DealStatusEdgeService(dealDataService, accounts,
            instruments, strategies, new ActorProvider(), coreEventWriter);

    /**
     * Ребро прохода, которым присвоена причина остановки, публикует факт;
     * радиус едет компонентами ВЕРХНЕГО уровня — читатель журнала на
     * глубину не ходит.
     */
    @Test
    @DisplayName("Ребро прохода с присвоенной причиной публикует факт остановки")
    void thePassEdgeThatAssignsAReasonPublishesTheFact() throws Exception {
        when(dealDataService.applyStatusEdge(any(), any())).thenReturn(true);
        Deal deal = deal(Deal.Status.EXIT_PENDING, Deal.ShutdownReason.STRATEGY_DELETED);

        service.applyPassEdge(context(deal), Deal.Status.ACTIVE, Deal.ShutdownReason.STRATEGY_DELETED);

        JsonNode content = contentOfWrittenRow();
        assertThat(content.path("dealInternalId").textValue()).isEqualTo(DEAL_INTERNAL_ID);
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT_INTERNAL_ID);
        assertThat(content.path("strategyInternalId").textValue()).isEqualTo(STRATEGY_INTERNAL_ID);
        assertThat(content.path("shutdownReason").textValue())
                .as("причина и есть то, ради чего класс заведён: другого носителя у неё нет")
                .isEqualTo(Deal.ShutdownReason.STRATEGY_DELETED.name());
        assertThat(content.path("status").textValue()).isEqualTo(Deal.Status.EXIT_PENDING.name());
        assertThat(writtenRow().getEventType())
                .isEqualTo(CoreEventType.DEAL_SHUTDOWN_INITIATED.name());
    }

    /**
     * Штатное ребро факта остановки не производит: причина им не
     * присваивалась, и объявлять остановку там, где её не было, значило бы
     * дать сделке лишнюю строку таймлайна.
     */
    @Test
    @DisplayName("Штатное ребро факта остановки не производит")
    void anOrdinaryPassEdgePublishesNothing() {
        when(dealDataService.applyStatusEdge(any(), any())).thenReturn(true);
        Deal deal = deal(Deal.Status.EXIT_PENDING, null);

        service.applyPassEdge(context(deal), Deal.Status.ACTIVE, null);

        verify(outboxDataService, never()).save(any());
    }

    /**
     * Не применившееся ребро события не производит: строка ушла из-под
     * прохода, и факта не было.
     */
    @Test
    @DisplayName("Не применившееся ребро факта не производит")
    void anEdgeThatDidNotApplyPublishesNothing() {
        when(dealDataService.applyStatusEdge(any(), any())).thenReturn(false);
        Deal deal = deal(Deal.Status.EXIT_PENDING, Deal.ShutdownReason.MARKET_DATA_EXPIRED);

        Boolean applied = service.applyPassEdge(context(deal), Deal.Status.ACTIVE,
                Deal.ShutdownReason.MARKET_DATA_EXPIRED);

        assertThat(applied).isFalse();
        verify(outboxDataService, never()).save(any());
    }

    /**
     * Ребро энфорсмента жёсткой ступени сводит модель и публикует факт;
     * идентичности радиуса резолвятся проекциями — контекста прохода на
     * этом шаге ещё нет.
     */
    @Test
    @DisplayName("Энфорсмент жёсткой ступени сводит модель и публикует факт")
    void theHardRungEnforcementSweepsTheModelAndPublishesTheFact() throws Exception {
        when(dealDataService.enforceHardRung(DEAL_ID, Deal.ShutdownReason.EXCHANGE_HOLD)).thenReturn(true);
        when(accounts.getRequiredTenantInternalIdById(ACCOUNT_ID)).thenReturn(TENANT);
        when(accounts.getRequiredInternalIdById(ACCOUNT_ID)).thenReturn(ACCOUNT_INTERNAL_ID);
        when(instruments.getRequiredInternalIdById(INSTRUMENT_ID)).thenReturn(INSTRUMENT_INTERNAL_ID);
        when(strategies.getRequiredStrategyInternalIdByDetailId(DETAIL_ID))
                .thenReturn(STRATEGY_INTERNAL_ID);
        Deal deal = deal(Deal.Status.ACTIVE, null);

        Boolean applied = service.enforceHardRung(deal, Deal.ShutdownReason.EXCHANGE_HOLD);

        assertThat(applied).isTrue();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.ERROR);
        assertThat(deal.getShutdownReason()).isEqualTo(Deal.ShutdownReason.EXCHANGE_HOLD);
        JsonNode content = contentOfWrittenRow();
        assertThat(content.path("status").textValue())
                .as("содержимое читает уже сведённое состояние, а не собирает второе его описание")
                .isEqualTo(Deal.Status.ERROR.name());
        assertThat(content.path("shutdownReason").textValue())
                .isEqualTo(Deal.ShutdownReason.EXCHANGE_HOLD.name());
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT_INTERNAL_ID);
        assertThat(writtenRow().getTenantId())
                .as("тенант — ключ партиции: без него порядок событий внутри тенанта не гарантирован")
                .isEqualTo(TENANT);
    }

    /** Не применившийся энфорсмент ни модели не двигает, ни факта не пишет. */
    @Test
    @DisplayName("Не применившийся энфорсмент модели не двигает")
    void anEnforcementThatDidNotApplyChangesNothing() {
        when(dealDataService.enforceHardRung(any(), any())).thenReturn(false);
        Deal deal = deal(Deal.Status.ACTIVE, null);

        Boolean applied = service.enforceHardRung(deal, Deal.ShutdownReason.RISK_POLICY);

        assertThat(applied).isFalse();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(deal.getShutdownReason()).isNull();
        verify(outboxDataService, never()).save(any());
    }

    /**
     * У сделки без закреплённой детали идентичность определения ПУСТА, а
     * резолв не зовётся вовсе: восстановленная сделка заведена вокруг
     * живого риска, и определения у неё не было.
     */
    @Test
    @DisplayName("У восстановленной сделки идентичность определения пуста")
    void theRecoveredDealCarriesAnEmptyStrategyIdentity() throws Exception {
        when(dealDataService.enforceHardRung(any(), any())).thenReturn(true);
        when(accounts.getRequiredTenantInternalIdById(ACCOUNT_ID)).thenReturn(TENANT);
        when(accounts.getRequiredInternalIdById(ACCOUNT_ID)).thenReturn(ACCOUNT_INTERNAL_ID);
        when(instruments.getRequiredInternalIdById(INSTRUMENT_ID)).thenReturn(INSTRUMENT_INTERNAL_ID);
        Deal deal = deal(Deal.Status.ACTIVE, null);
        deal.setStrategyDetailId(null);

        service.enforceHardRung(deal, Deal.ShutdownReason.RISK_POLICY);

        assertThat(contentOfWrittenRow().path("strategyInternalId").isNull()).isTrue();
        verify(strategies, never()).getRequiredStrategyInternalIdByDetailId(any());
    }

    /**
     * <b>Отказ ребра, присваивавшего причину, помечается своим типом.</b>
     * Он и есть операнд единственного решения вызывающего: перехваченная
     * в {@code ERROR} сделка причины больше не получит НИКОГДА — рёбра
     * присвоения применяются только из {@code ACTIVE} и
     * {@code EXIT_PENDING}, — а откат вернул её в состояние, из которого
     * ход повторим. <b>Локус здесь первый</b> ({@code enforceHardRung}), и
     * повторимость на нём держится на нетронутом состоянии: шаг стои́т
     * первым в проходе. Довод второго локуса другой — он назван у
     * {@code DealShutdownEdgeException}.
     *
     * <p>Отказ здесь задан на резолве идентичности: он лежит ВНУТРИ той же
     * транзакции, что и уже применённое ребро, — то есть ровно тот случай,
     * когда откат уносит записанную причину.
     */
    @Test
    @DisplayName("Отказ внутри ребра энфорсмента помечается типом остановки")
    void aFailureInsideTheEnforcementEdgeIsMarked() {
        when(dealDataService.enforceHardRung(any(), any())).thenReturn(true);
        when(accounts.getRequiredTenantInternalIdById(ACCOUNT_ID))
                .thenThrow(new IllegalStateException("tenant identity is not resolvable"));
        Deal deal = deal(Deal.Status.ACTIVE, null);

        assertThatThrownBy(() -> service.enforceHardRung(deal, Deal.ShutdownReason.EXCHANGE_HOLD))
                .as("непомеченный отказ увёл бы сделку в ERROR без причины, и повтора у неё не было бы")
                .isInstanceOf(DealShutdownEdgeException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** Тот же отказ на ребре прохода, присвоившем причину, помечается так же. */
    @Test
    @DisplayName("Отказ публикации на ребре прохода с причиной помечается типом остановки")
    void aFailureOnAReasonAssigningPassEdgeIsMarked() {
        when(dealDataService.applyStatusEdge(any(), any())).thenReturn(true);
        when(outboxDataService.save(any())).thenThrow(new IllegalStateException("outbox is unavailable"));
        Deal deal = deal(Deal.Status.EXIT_PENDING, Deal.ShutdownReason.STRATEGY_DELETED);

        assertThatThrownBy(() -> service.applyPassEdge(context(deal), Deal.Status.ACTIVE,
                Deal.ShutdownReason.STRATEGY_DELETED))
                .isInstanceOf(DealShutdownEdgeException.class);
    }

    /**
     * <b>Штатное ребро своим типом НЕ помечается</b>, и это не оговорка:
     * причины на нём не присваивалось, терять нечего, а помеченный отказ
     * снял бы с такой сделки общий перехват — то есть оставил бы её без
     * durable-пометки об ошибке вовсе.
     */
    @Test
    @DisplayName("Отказ штатного ребра уходит как есть, без пометки остановки")
    void aFailureOnAnOrdinaryEdgeTravelsUnmarked() {
        when(dealDataService.applyStatusEdge(any(), any()))
                .thenThrow(new IllegalStateException("the row is gone"));
        Deal deal = deal(Deal.Status.EXIT_PENDING, null);

        assertThatThrownBy(() -> service.applyPassEdge(context(deal), Deal.Status.ACTIVE, null))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DealShutdownEdgeException.class);
    }

    // --- сборка -----------------------------------------------------------

    private OutboxEntity writtenRow() {
        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxDataService).save(captor.capture());
        return captor.getValue();
    }

    private JsonNode contentOfWrittenRow() throws Exception {
        return objectMapper.readTree(writtenRow().getPayload());
    }

    private static Deal deal(Deal.Status status, Deal.ShutdownReason reason) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setInternalId(DEAL_INTERNAL_ID);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(INSTRUMENT_ID);
        deal.setStrategyDetailId(DETAIL_ID);
        deal.setStatus(status);
        deal.setShutdownReason(reason);
        return deal;
    }

    private static DealContext context(Deal deal) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setTenantId(TENANT);
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        Strategy definition = new Strategy();
        definition.setInternalId(STRATEGY_INTERNAL_ID);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .strategy(definition)
                .actionStates(new ArrayList<>())
                .cashFlows(new ArrayList<>())
                .graphComplete(true)
                .build();
    }
}
