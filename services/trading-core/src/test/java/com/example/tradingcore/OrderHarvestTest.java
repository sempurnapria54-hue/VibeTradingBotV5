package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.RefreshAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.RefreshOrderExecutor;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.exception.ExternalNotFoundException;
import com.example.tradingcore.exception.ExternalStatusException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.mapping.AlgoOrderMapper;
import com.example.tradingcore.mapping.AlgoOrderMapperImpl;
import com.example.tradingcore.mapping.OrderMapper;
import com.example.tradingcore.mapping.OrderMapperImpl;
import com.example.tradingcore.mapping.RuntimeJsonConverter;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Добыча состояния заявок — обычной и условной.
 *
 * <p><b>Предмет — исчерпание цикла и его исход.</b> Пустой ответ ОДНОГО
 * источника основанием для терминала не является: цикл обходится целиком,
 * и только его исчерпание даёт «не найдена после добычи». Обратная ошибка
 * терминализовала бы живую заявку по одному пустому ответу.
 *
 * <p><b>Второй предмет — что сущность помечена ДО броска.</b> Реакция на
 * контролируемый отказ биржевая, и поднимает её проход; помечай мы
 * сущность после броска — ступень поднялась бы по факту, которого в базе
 * нет.
 *
 * <p>Доменные модели собираются настоящими полями: предикаты считаются
 * сами (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class OrderHarvestTest {

    private static final Long DEAL_ID = 5L;
    private static final Long TRANCHE_ID = 7L;
    private static final Long ORDER_ID = 21L;
    private static final Long ALGO_ID = 31L;
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";
    private static final String ORDER_CLIENT_ID = "ord-0001";
    private static final String ALGO_CLIENT_ID = "alg-0001";
    private static final String PROTECTION_CLIENT_ID = "prt-0001";

    private final OrderDataService orderDataService = mock(OrderDataService.class);
    private final AlgoOrderDataService algoOrderDataService = mock(AlgoOrderDataService.class);
    private final DealActionStateDataService actionStateDataService = mock(DealActionStateDataService.class);
    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final DealRiskNumbersService riskNumbersService = mock(DealRiskNumbersService.class);
    private final OrderMapper orderMapper = new OrderMapperImpl();
    private final AlgoOrderMapper algoOrderMapper = new AlgoOrderMapperImpl(
            new RuntimeJsonConverter(new ObjectMapper()));

    private final RefreshOrderExecutor orderExecutor = new RefreshOrderExecutor(orderDataService,
            actionStateDataService, exchange, orderMapper, new AttachedAlgoOrderStateResolver(),
            riskNumbersService);

    private final RefreshAlgoOrderExecutor algoExecutor = new RefreshAlgoOrderExecutor(algoOrderDataService,
            actionStateDataService, exchange, algoOrderMapper, riskNumbersService);

    /**
     * Цикл обрывается на первом нашедшем источнике: заявка отдана точечным
     * чтением — ни ожидающие, ни история не запрашиваются.
     */
    @Test
    void firstSourceThatFindsTheOrderStopsTheCycle() {
        Order order = order(Order.Status.ACTIVE, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.PARTIALLY_COMPLETED, "0.4"));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(Order.Status.PARTIALLY_COMPLETED);
        assertThat(order.getAccumulatedFillSize()).isEqualByComparingTo("0.4");
        verify(exchange, never()).getPendingOrders(any(), any());
        verify(exchange, never()).getOrderHistory(any(), any());
    }

    /**
     * Пустой ответ ПЕРВОГО источника терминала не даёт: заявка находится
     * историей, и статус применяется её фактом.
     */
    @Test
    void anEmptyFirstSourceIsNotGroundsForATerminal() {
        Order order = order(Order.Status.ACTIVE, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingOrders(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getOrderHistory(ACCOUNT, INSTRUMENT))
                .thenReturn(List.of(fetchedOrder(Order.Status.COMPLETED, "1")));

        orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(order.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.FILLED);
    }

    /**
     * Цикл исчерпан: сущность помечена причиной «не найдена после добычи»
     * И брошено контролируемое исключение — реакцию поднимает проход.
     */
    @Test
    void exhaustedCycleMarksTheOrderAndThrows() {
        Order order = order(Order.Status.ACTIVE, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingOrders(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getOrderHistory(ACCOUNT, INSTRUMENT)).thenReturn(List.of());

        assertThatThrownBy(() -> orderExecutor.execute(orderCommand(), row(), context(deal)))
                .isInstanceOf(ExternalNotFoundException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.ERROR);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.MISSING_AFTER_REFRESH);
        verify(orderDataService).save(order);
    }

    /**
     * Исчерпанный цикл у НЕОТПРАВЛЕННОЙ ноги пропавшей её не делает: на
     * площадке её не было, и терминал — «не дошла до площадки» без броска,
     * то есть без биржевой ступени; встроенная защита уходит с ней, числа
     * риска пересчитаны, исполнение доведено.
     */
    @Test
    void exhaustedCycleWithdrawsAnUnsentOrderWithoutRaising() {
        Order order = order(Order.Status.CREATED, null);
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setInternalId(PROTECTION_CLIENT_ID);
        protection.setStatus(AttachedAlgoOrder.Status.CREATED);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(protection)));
        Deal deal = dealWith(order);
        DealActionState row = row();
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingOrders(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getOrderHistory(ACCOUNT, INSTRUMENT)).thenReturn(List.of());

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row, context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getHoldSignals()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.NOT_PLACED);
        assertThat(protection.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(protection.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
        verify(riskNumbersService).recompute(any());
    }

    /**
     * Отказ резолва статуса приезжает броском ЧТЕНИЯ (словарь площадки
     * живёт у коннектора): сущность получает причину своей категории и
     * исключение уходит дальше нетронутым.
     */
    @Test
    void aRefusedStatusMarksTheOrderAndPropagates() {
        Order order = order(Order.Status.ACTIVE, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenThrow(new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, "wat"));

        assertThatThrownBy(() -> orderExecutor.execute(orderCommand(), row(), context(deal)))
                .isInstanceOf(ExternalStatusException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.ERROR);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /**
     * Причина отмены берётся из НАШЕГО намерения: оно стои́т на сущности с
     * момента отправки снятия и наблюдением не перетирается.
     */
    @Test
    void ourStandingCancelIntentSurvivesTheObservedCancellation() {
        Order order = order(Order.Status.ACTIVE, Order.CloseReason.REPLACED_BY_STRATEGY);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.CANCELED, "0"));

        orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.REPLACED_BY_STRATEGY);
    }

    /**
     * Отмена без нашего намерения причину всё равно получает: пустота
     * сделала бы отмену, инициированную биржей, непроходимой.
     */
    @Test
    void anExchangeInitiatedCancellationStillGetsAReason() {
        Order order = order(Order.Status.ACTIVE, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.CANCELED, "0"));

        orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.UNKNOWN);
    }

    /**
     * Второй цикл на ЖИВОМ родителе не запускается: защита ещё в его теле,
     * материализовать её нечему, и запрос был бы платой ни за что.
     */
    @Test
    void theProtectionSearchCycleDoesNotRunForALiveParent() {
        Order order = order(Order.Status.ACTIVE, null);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(protection(AttachedAlgoOrder.Status.PENDING))));
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.ACTIVE, "0"));

        orderExecutor.execute(orderCommand(), row(), context(deal));

        verify(exchange, never()).getPendingMaterializedProtections(any(), any());
    }

    /**
     * Терминальный налитый родитель: защита, найденная самостоятельной
     * живой записью, читается ЖИВОЙ. Без второго цикла она читалась бы
     * снятой, и покрытие недосчитывалось бы — ошибка в разрешающую сторону.
     */
    @Test
    void aMaterializedProtectionFoundAliveStaysActive() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.PENDING);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT))
                .thenReturn(List.of(protection(null)));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(result.getHoldSignals()).isEmpty();
    }

    /**
     * Разбор истории нашёл ногу сработавшей — терминал ставится ею, а не
     * сырым статусом записи.
     */
    @Test
    void theHistoryLegThatFindsTheRecordEncodesTheTerminal() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getMaterializedProtectionHistory(ACCOUNT, INSTRUMENT, ProtectionHistoryLeg.EFFECTIVE))
                .thenReturn(List.of(protection(null)));

        orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(attached.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    /**
     * Пустой разбор истории — не факт, а его отсутствие: терминал не
     * ставится, а звено ЗАТРЕБУЕТ мягкую ступень инструмента; поднимает её
     * проход.
     */
    @Test
    void anEmptyHistoryAnalysisRequestsASoftInstrumentRung() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getMaterializedProtectionHistory(any(), any(), any())).thenReturn(List.of());

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(result.getHoldSignals()).hasSize(1);
        assertThat(result.getHoldSignals().getFirst().getCode())
                .isEqualTo("INSTRUMENT_PROTECTION_FATE_UNKNOWN");
    }

    /**
     * Живой риск транша без отдельной защиты и без нашего намерения снятия,
     * и ни одна нога разбора записи не дала — покрытие потеряно: терминал,
     * а не сигнал неопределённого исхода.
     */
    @Test
    void liveTrancheRiskWithoutAnIntentLosesTheProtectionAfterAnEmptyAnalysis() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWithLiveExposure(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getMaterializedProtectionHistory(any(), any(), any())).thenReturn(List.of());

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ERROR);
        assertThat(attached.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
        assertThat(result.getHoldSignals()).isEmpty();
        verify(exchange).getMaterializedProtectionHistory(ACCOUNT, INSTRUMENT, ProtectionHistoryLeg.EFFECTIVE);
    }

    /**
     * Защита, сработавшая у площадки, из живых уходит так же, как пропавшая,
     * а экспозиция транша до наблюдения срабатывания стоит налитой. Разбор
     * идёт и на этой ветви: сработавший стоп закрыл риск, и потерянным
     * покрытием он не читается.
     */
    @Test
    void aProtectionTriggeredAtTheVenueIsNotReadAsLostCoverage() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWithLiveExposure(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getMaterializedProtectionHistory(ACCOUNT, INSTRUMENT, ProtectionHistoryLeg.EFFECTIVE))
                .thenReturn(List.of(protection(null)));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(attached.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
        assertThat(result.getHoldSignals()).isEmpty();
    }

    /**
     * Защиту, которую сняли МЫ, пропавшей не читают, даже если экспозиция
     * транша ещё стоит налитой — аварийное снятие закрыло позицию вне окна
     * атрибуции. Её судьбу даёт разбор истории, а причина — стоящее
     * намерение, write-once.
     */
    @Test
    void aStandingKillSwitchIntentSendsTheProtectionToHistoryAnalysis() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        attached.setCloseReason(AttachedAlgoOrder.CloseReason.KILL_SWITCH);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWithLiveExposure(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getMaterializedProtectionHistory(ACCOUNT, INSTRUMENT, ProtectionHistoryLeg.EFFECTIVE))
                .thenReturn(List.of());
        when(exchange.getMaterializedProtectionHistory(ACCOUNT, INSTRUMENT, ProtectionHistoryLeg.CANCELED))
                .thenReturn(List.of(protection(null)));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(attached.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.KILL_SWITCH);
        assertThat(result.getHoldSignals()).isEmpty();
    }

    /**
     * Условная заявка: подтверждение прежнего статуса переходом не
     * является — граф переходов петель не содержит, а наблюдение живой
     * заявки идёт каждым тиком.
     */
    @Test
    void repeatedObservationOfALiveAlgoOrderIsNotATransition() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.ACTIVE);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID))
                .thenReturn(fetchedAlgo(AlgoOrder.Status.ACTIVE));

        ServiceCommandExecutionResult result = algoExecutor.execute(algoCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
    }

    /**
     * Условная заявка не найдена после ПОЛНОГО цикла: причина «не найдена
     * после добычи» плюс бросок. Архива глубже истории у неё нет.
     */
    @Test
    void anAlgoOrderMissingAfterTheFullCycleIsTerminalAndThrows() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.ACTIVE);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingAlgoOrders(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS))
                .thenReturn(List.of());
        when(exchange.getAlgoOrderHistory(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS, null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> algoExecutor.execute(algoCommand(), row(), context(deal)))
                .isInstanceOf(ExternalNotFoundException.class);

        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.ERROR);
        assertThat(algoOrder.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.MISSING_AFTER_REFRESH);
    }

    /**
     * Неотправленная условная заявка, не найденная полным циклом, до площадки
     * не дошла: терминал «не дошла до площадки» без броска и без ступени.
     */
    @Test
    void anUnsentAlgoOrderMissingAfterTheFullCycleIsWithdrawnWithoutRaising() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.CREATED);
        Deal deal = dealWithAlgo(algoOrder);
        DealActionState row = row();
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingAlgoOrders(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS))
                .thenReturn(List.of());
        when(exchange.getAlgoOrderHistory(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS, null))
                .thenReturn(List.of());

        ServiceCommandExecutionResult result = algoExecutor.execute(algoCommand(), row, context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.CANCELED);
        assertThat(algoOrder.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.NOT_PLACED);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
        verify(riskNumbersService).recompute(any());
    }

    /**
     * Проблемный статус условной заявки несёт причину СВОЕЙ сущности:
     * значения отказа постановки живут в её перечне и обычной заявке
     * недостижимы.
     */
    @Test
    void anAlgoRefusalCarriesAReasonFromItsOwnVocabulary() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.ACTIVE);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID))
                .thenThrow(new ExternalStatusException(ExternalStatusReason.ORDER_FAILED, "order_failed"));

        assertThatThrownBy(() -> algoExecutor.execute(algoCommand(), row(), context(deal)))
                .isInstanceOf(ExternalStatusException.class);

        assertThat(algoOrder.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.ORDER_FAILED);
    }

    /**
     * Неотправленная нога, найденная ИСПОЛНЕННОЙ, — ответ на отправку
     * потерян, а запись на площадке и есть подтверждение приёма. Ребра
     * «создана → исполнена» в матрице нет, и наблюдение проходит через
     * отправленную, а не падает на охране модели.
     */
    @Test
    void anUnsentOrderFoundCompletedPassesThroughPending() {
        Order order = order(Order.Status.CREATED, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.FILLED);
    }

    /** Неотправленная нога, найденная живой, становится активной — через отправленную, а не сеттером мимо матрицы. */
    @Test
    void anUnsentOrderFoundLiveBecomesActiveThroughPending() {
        Order order = order(Order.Status.CREATED, null);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.ACTIVE, "0"));

        orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(order.getStatus()).isEqualTo(Order.Status.ACTIVE);
        assertThat(order.canTransitionTo(Order.Status.COMPLETED)).isTrue();
    }

    /**
     * Нога в {@code ERROR}, которую площадка показала исполненной: статус
     * ноги не двигается — рёбер из проблемного терминала нет, — а судьба
     * защиты читается по НАБЛЮДЁННОМУ терминалу. Налив помеченной ноги
     * материализовал защиту, и цикл её ищет; класс проблемного увёл бы
     * живую защиту в неживые, и снятие риска её бы не сняло
     * (docs/spec/order-lifecycle.json, {@code attachedParentStatus}).
     */
    @Test
    void anErrorParentObservedFilledHasItsProtectionSearched() {
        Order order = order(Order.Status.ERROR, Order.CloseReason.MISSING_AFTER_REFRESH);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenReturn(fetchedOrder(Order.Status.COMPLETED, "1"));
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT))
                .thenReturn(List.of(protection(null)));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(Order.Status.ERROR);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.MISSING_AFTER_REFRESH);
        verify(exchange).getPendingMaterializedProtections(ACCOUNT, INSTRUMENT);
        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(result.getHoldSignals()).isEmpty();
    }

    /**
     * Наблюдённый ЖИВОЙ статус у ноги в {@code ERROR} даёт защите класс
     * ЖИВОГО родителя, как наблюдённый терминал даёт свой: защита остаётся
     * в теле родителя и наблюдается дальше, а не уходит в {@code ERROR}.
     * Иначе, когда снятие риска отменит помеченную ногу с наливом, площадка
     * материализует защиту живой заявкой, а модель держала бы её неживой
     * (docs/spec/order-lifecycle.json, {@code attachedParentStatus}). Статус
     * ноги не двигается, причина ошибки стоит, а наблюдённая живость —
     * истина.
     */
    @Test
    void anErrorParentObservedLiveTakesTheLiveClass() {
        Order order = order(Order.Status.ERROR, Order.CloseReason.MISSING_AFTER_REFRESH);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        Deal deal = dealWith(order);
        givenSaves();
        Order fetched = fetchedOrder(Order.Status.ACTIVE, "1");
        fetched.setAttachedAlgoOrders(List.of(protection(null)));
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID)).thenReturn(fetched);

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(Order.Status.ERROR);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.MISSING_AFTER_REFRESH);
        assertThat(order.getExternalLive()).isTrue();
        verify(exchange, never()).getPendingMaterializedProtections(any(), any());
        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(result.getHoldSignals()).isEmpty();
    }

    /**
     * Налитый родитель за горизонтом выдачи: запись ушла из истории, а факт
     * терминала добыт раньше и стоит. Ни терминала, ни броска, ни биржевой
     * ступени — а защита резолвится по локальному терминалу: второй цикл
     * идёт по инструменту и находит её живой
     * (docs/spec/external-status-resolution.json, {@code notFoundPastLocalTerminal}).
     */
    @Test
    void aFilledParentPastTheHorizonRaisesNothingAndItsProtectionIsFoundAlive() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        order.setAccumulatedFillSize(new BigDecimal("1"));
        Deal deal = dealWith(order);
        DealActionState row = row();
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingOrders(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getOrderHistory(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getPendingMaterializedProtections(ACCOUNT, INSTRUMENT))
                .thenReturn(List.of(protection(null)));

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row, context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getHoldSignals()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.FILLED);
        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
    }

    /**
     * Нога в {@code ERROR}, не найденная полным циклом, тоже локально
     * терминальна: броска нет. Судьба защиты читается классом по наливу
     * (docs/spec/order-lifecycle.json, attachedParentClass): налив не добыт —
     * поиск, пустой разбор истории — защита стоит, а звено затребует мягкую
     * ступень инструмента.
     */
    @Test
    void anErrorParentNotFoundWithAnUnknownFillLeavesTheProtectionFateUnknown() {
        Order order = order(Order.Status.ERROR, Order.CloseReason.MISSING_AFTER_REFRESH);
        AttachedAlgoOrder attached = protection(AttachedAlgoOrder.Status.ACTIVE);
        order.setAttachedAlgoOrders(new ArrayList<>(List.of(attached)));
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingOrders(ACCOUNT, INSTRUMENT)).thenReturn(List.of());
        when(exchange.getOrderHistory(ACCOUNT, INSTRUMENT)).thenReturn(List.of());

        ServiceCommandExecutionResult result = orderExecutor.execute(orderCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(Order.Status.ERROR);
        assertThat(order.getExternalLive()).isFalse();
        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(result.getHoldSignals()).hasSize(1);
        assertThat(result.getHoldSignals().getFirst().getCode())
                .isEqualTo("INSTRUMENT_PROTECTION_FATE_UNKNOWN");
    }

    /**
     * Отказ разбора статуса у терминального родителя уходит дальше — это
     * факт об источнике, — но терминала не перетирает: ребра в ошибочное из
     * терминала нет. Сохраняется только наблюдённая живость — пустотой:
     * неразобранный статус наблюдением не является
     * (docs/components/RefreshOrderExecutor.md).
     */
    @Test
    void aRefusedStatusOnATerminalParentPropagatesWithoutMovingIt() {
        Order order = order(Order.Status.COMPLETED, Order.CloseReason.FILLED);
        order.setExternalLive(Boolean.FALSE);
        Deal deal = dealWith(order);
        givenSaves();
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, ORDER_CLIENT_ID))
                .thenThrow(new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, "wat"));

        assertThatThrownBy(() -> orderExecutor.execute(orderCommand(), row(), context(deal)))
                .isInstanceOf(ExternalStatusException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(order.getCloseReason()).isEqualTo(Order.CloseReason.FILLED);
        assertThat(order.getExternalLive()).isNull();
    }

    /**
     * Неотправленная условная заявка, найденная живой, становится активной
     * через отправленную: прямого ребра из созданного в матрице нет.
     */
    @Test
    void anUnsentAlgoOrderFoundLiveBecomesActiveThroughPending() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.CREATED);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID))
                .thenReturn(fetchedAlgo(AlgoOrder.Status.ACTIVE));

        ServiceCommandExecutionResult result = algoExecutor.execute(algoCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
    }

    /**
     * Частичное срабатывание отправленной условной заявки проходит через
     * активную: сработать частично могла только вставшая заявка.
     */
    @Test
    void aPartialTriggerOfAPendingAlgoOrderPassesThroughActive() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.PENDING);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID))
                .thenReturn(fetchedAlgo(AlgoOrder.Status.PARTIALLY_COMPLETED));

        algoExecutor.execute(algoCommand(), row(), context(deal));

        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.PARTIALLY_COMPLETED);
    }

    /**
     * Откат живого статуса назад — частично сработавшая заявка, наблюдённая
     * активной, — состояния не двигает и прохода не роняет: ребра назад
     * матрица не содержит, и исполнитель спрашивает её предикатом, а не
     * отказом броском (docs/spec/algo-order-lifecycle.json,
     * {@code algoTransitionAllowed}).
     */
    @Test
    void anObservedRollbackOfALiveAlgoOrderDoesNotMoveIt() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.PARTIALLY_COMPLETED);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID))
                .thenReturn(fetchedAlgo(AlgoOrder.Status.ACTIVE));

        ServiceCommandExecutionResult result = algoExecutor.execute(algoCommand(), row(), context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.PARTIALLY_COMPLETED);
        assertThat(algoOrder.getCloseReason()).isNull();
    }

    /**
     * Терминальная условная заявка, не найденная полным циклом, ушла за
     * горизонт выдачи: терминала не теряет, ничего не пишется, броска нет,
     * звено завершается (docs/spec/external-status-resolution.json,
     * {@code notFoundPastLocalTerminal}).
     */
    @Test
    void aTerminalAlgoOrderMissingAfterTheCycleKeepsItsTerminalWithoutRaising() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.COMPLETED);
        algoOrder.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        Deal deal = dealWithAlgo(algoOrder);
        DealActionState row = row();
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID)).thenReturn(null);
        when(exchange.getPendingAlgoOrders(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS))
                .thenReturn(List.of());
        when(exchange.getAlgoOrderHistory(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS, null))
                .thenReturn(List.of());

        ServiceCommandExecutionResult result = algoExecutor.execute(algoCommand(), row, context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.COMPLETED);
        assertThat(algoOrder.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.TRIGGERED);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
        verify(algoOrderDataService, never()).save(any());
    }

    /**
     * Отказ разбора статуса у терминальной условной заявки бросок
     * сохраняет — неизвестное слово есть дефект словаря площадки, а не
     * свойство заявки, — но терминала не перетирает.
     */
    @Test
    void aRefusedStatusOnATerminalAlgoOrderPropagatesWithoutMovingIt() {
        AlgoOrder algoOrder = algoOrder(AlgoOrder.Status.COMPLETED);
        algoOrder.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        Deal deal = dealWithAlgo(algoOrder);
        givenSaves();
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, ALGO_CLIENT_ID))
                .thenThrow(new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, "wat"));

        assertThatThrownBy(() -> algoExecutor.execute(algoCommand(), row(), context(deal)))
                .isInstanceOf(ExternalStatusException.class);

        assertThat(algoOrder.getStatus()).isEqualTo(AlgoOrder.Status.COMPLETED);
        assertThat(algoOrder.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.TRIGGERED);
    }

    private void givenSaves() {
        when(orderDataService.save(any())).thenAnswer(call -> call.getArgument(0));
        when(algoOrderDataService.save(any())).thenAnswer(call -> call.getArgument(0));
        when(riskNumbersService.recompute(any())).thenReturn(true);
    }

    private static Order order(Order.Status status, Order.CloseReason closeReason) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setDealId(DEAL_ID);
        order.setDealTrancheId(TRANCHE_ID);
        order.setInternalId(ORDER_CLIENT_ID);
        order.setType(Order.Type.ENTRY);
        order.setStatus(status);
        order.setCloseReason(closeReason);
        order.setSize(new BigDecimal("1"));
        return order;
    }

    private static Order fetchedOrder(Order.Status status, String fill) {
        Order fetched = new Order();
        fetched.setInternalId(ORDER_CLIENT_ID);
        fetched.setExternalId("ex-1");
        fetched.setStatus(status);
        fetched.setExternalStatus("whatever");
        fetched.setAccumulatedFillSize(new BigDecimal(fill));
        return fetched;
    }

    private static AttachedAlgoOrder protection(AttachedAlgoOrder.Status status) {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setId(41L);
        attached.setOrderId(ORDER_ID);
        attached.setInternalId(PROTECTION_CLIENT_ID);
        attached.setStatus(status);
        attached.setSize(new BigDecimal("1"));
        return attached;
    }

    private static AlgoOrder algoOrder(AlgoOrder.Status status) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(ALGO_ID);
        algoOrder.setDealId(DEAL_ID);
        algoOrder.setDealTrancheId(TRANCHE_ID);
        algoOrder.setInternalId(ALGO_CLIENT_ID);
        algoOrder.setStatus(status);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(new BigDecimal("1"));
        return algoOrder;
    }

    private static AlgoOrder fetchedAlgo(AlgoOrder.Status status) {
        AlgoOrder fetched = new AlgoOrder();
        fetched.setInternalId(ALGO_CLIENT_ID);
        fetched.setExternalId("ex-algo-1");
        fetched.setStatus(status);
        fetched.setExternalStatus("live");
        return fetched;
    }

    private static Deal dealWith(Order order) {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setDealId(DEAL_ID);
        tranche.setOrders(new ArrayList<>(List.of(order)));
        tranche.setEntryFilled(new BigDecimal("1"));
        tranche.setProtectionClosed(new BigDecimal("1"));
        return deal(tranche);
    }

    /** Транш с налитым входом и без закрытий: экспозиция положительна, отдельной защиты нет. */
    private static Deal dealWithLiveExposure(Order order) {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setDealId(DEAL_ID);
        tranche.setOrders(new ArrayList<>(List.of(order)));
        tranche.setEntryFilled(new BigDecimal("1"));
        return deal(tranche);
    }

    private static Deal dealWithAlgo(AlgoOrder algoOrder) {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setDealId(DEAL_ID);
        tranche.setAlgoOrders(new ArrayList<>(List.of(algoOrder)));
        return deal(tranche);
    }

    private static Deal deal(DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setTranches(new ArrayList<>(List.of(tranche)));
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

    private static ServiceCommand orderCommand() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(42L)
                .payload(new RefreshOrderCommandPayload(ORDER_ID))
                .build();
    }

    private static ServiceCommand algoCommand() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(42L)
                .payload(new RefreshAlgoOrderCommandPayload(ALGO_ID))
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
