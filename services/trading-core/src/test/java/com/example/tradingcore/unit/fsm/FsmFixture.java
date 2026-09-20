package com.example.tradingcore.unit.fsm;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.market.MarketFeatures;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Общая сборка предмета {@code trading-core-fsm} — слоя статусных машин
 * сделки и транша
 * (`.claude/tests/cases/trading-core-fsm.md` §«Чем достаются выходы»).
 *
 * <p><b>Субстрата у предмета нет:</b> гейты, машины, каскад, диспозиция и
 * обработчики конструируются {@code new}; аннотации {@code @Service} и
 * {@code @Component} на классах — деталь потребителя.
 *
 * <p><b>Состояние собирается настоящими полями доменных моделей</b> —
 * ногами траншей с их наливом, эпизодами позиции, защитами с их
 * уровнями, — а не подменёнными предикатами: подменённый предикат
 * проверял бы машину против модели, которой в проде не существует
 * (`.claude/rules/codestyle.md` §«Тесты доменных моделей»).
 *
 * <p><b>Экспозиция транша здесь производная, а не поле:</b> её дают налив
 * входа, налив собственного выхода и приписанное закрытие уровня сделки —
 * ровно так, как их читает {@code DealTranche#exposure}.
 */
final class FsmFixture {

    /** Сделка базовой сборки. */
    static final Long DEAL_ID = 7L;

    /** Транш базовой сборки; ноги, защиты и налив висят на нём. */
    static final Long TRANCHE_ID = 21L;

    /** Соседний транш той же сделки — операнд агрегатных предикатов. */
    static final Long SECOND_TRANCHE_ID = 22L;

    /** Объявление, по которому материализован транш базовой сборки. */
    static final Long DECLARATION_ID = 31L;

    /** Объявление соседнего транша. */
    static final Long SECOND_DECLARATION_ID = 32L;

    /** Инструмент сделки — вторая половина ключа пары. */
    static final Long INSTRUMENT_ID = 3L;

    /** Биржевой счёт сделки — первая половина ключа пары. */
    static final Long ACCOUNT_ID = 2L;

    private FsmFixture() {
    }

    // ------------------------------------------------------------------
    // Сделка и её граф
    // ------------------------------------------------------------------

    /** Сделка названного статуса с названными траншами; ног и эпизодов у неё нет. */
    static Deal deal(Deal.Status status, DealTranche... tranches) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(status);
        deal.setInstrumentId(INSTRUMENT_ID);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setEntryReason(Deal.EntryReason.STRATEGY);
        deal.setTranches(new ArrayList<>(List.of(tranches)));
        deal.setOrders(new ArrayList<>());
        deal.setAlgoOrders(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        return deal;
    }

    /** Транш базовой сборки в названном статусе. */
    static DealTranche tranche(DealTranche.Status status) {
        return tranche(TRANCHE_ID, status);
    }

    /** Транш с названным идентификатором: налива, ног и защит у него нет. */
    static DealTranche tranche(Long id, DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setId(id);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(status);
        tranche.setEpisodeSeq(1);
        tranche.setStrategyTrancheId(declarationOf(id));
        tranche.setOrders(new ArrayList<>());
        tranche.setAlgoOrders(new ArrayList<>());
        return tranche;
    }

    private static Long declarationOf(Long trancheId) {
        return SECOND_TRANCHE_ID.equals(trancheId) ? SECOND_DECLARATION_ID : DECLARATION_ID;
    }

    /**
     * Налив транша: вход и собственный выход. Экспозиция получается
     * вычитанием, а не присваиванием.
     */
    static DealTranche fills(DealTranche tranche, String entryFilled, String reduceOnlyFilled) {
        tranche.setEntryFilled(decimal(entryFilled));
        tranche.setReduceOnlyFilled(decimal(reduceOnlyFilled));
        return tranche;
    }

    /** Транш с названной экспозицией: налив входа при нулевом выходе. */
    static DealTranche exposed(DealTranche tranche, String exposure) {
        return fills(tranche, exposure, "0");
    }

    /** Живая входная нога транша: не только уменьшает позицию, налива нет. */
    static Order liveEntryLeg(Long id, Long trancheId) {
        return leg(id, trancheId, Order.Status.ACTIVE, Boolean.FALSE, "0");
    }

    /** Входная нога, налитая целиком. */
    static Order filledEntryLeg(Long id, Long trancheId, String fill) {
        Order leg = leg(id, trancheId, Order.Status.COMPLETED, Boolean.FALSE, fill);
        leg.setCloseReason(Order.CloseReason.FILLED);
        return leg;
    }

    /** Входная нога, снятая без налива. */
    static Order cancelledEntryLeg(Long id, Long trancheId) {
        Order leg = leg(id, trancheId, Order.Status.CANCELED, Boolean.FALSE, "0");
        leg.setCloseReason(Order.CloseReason.CANCELED_BY_STRATEGY);
        return leg;
    }

    /** Живая reduce-only нога транша: риск она снимает, а не создаёт. */
    static Order liveReduceOnlyLeg(Long id, Long trancheId) {
        return leg(id, trancheId, Order.Status.ACTIVE, Boolean.TRUE, "0");
    }

    /** Налитая reduce-only нога: инициатор выхода «закрыли мы». */
    static Order filledReduceOnlyLeg(Long id, Long trancheId, String fill) {
        Order leg = leg(id, trancheId, Order.Status.COMPLETED, Boolean.TRUE, fill);
        leg.setCloseReason(Order.CloseReason.FILLED);
        return leg;
    }

    /** Нога с названными статусом, намерением и наливом. */
    static Order leg(Long id, Long trancheId, Order.Status status, Boolean reducingOnly, String fill) {
        Order order = new Order();
        order.setId(id);
        order.setDealId(DEAL_ID);
        order.setDealTrancheId(trancheId);
        order.setStatus(status);
        order.setPositionReducingOnly(reducingOnly);
        order.setAccumulatedFillSize(decimal(fill));
        order.setAttachedAlgoOrders(new ArrayList<>());
        return order;
    }

    /** Живая ОТДЕЛЬНАЯ защита транша с уровнем остановки убытка. */
    static AlgoOrder protection(Long id, Long trancheId, String size) {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setType(AlgoOrder.TriggerPriceType.MARK);
        stopLoss.setValue(new BigDecimal("2910"));
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(id);
        algoOrder.setDealId(DEAL_ID);
        algoOrder.setDealTrancheId(trancheId);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(decimal(size));
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS, new Trigger(stopLoss, null), null));
        return algoOrder;
    }

    /** Сработавшая отдельная защита названного рода — инициатор выхода транша. */
    static AlgoOrder triggeredProtection(Long id, Long trancheId, AlgoOrder.ConditionType type) {
        AlgoOrder algoOrder = protection(id, trancheId, "1");
        algoOrder.setConditionType(type);
        algoOrder.setStatus(AlgoOrder.Status.COMPLETED);
        algoOrder.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        return algoOrder;
    }

    /**
     * Живая ВСТРОЕННАЯ защита, висящая на своей родительской ноге.
     * Уровня остановки убытка она не несёт: покрытие по размеру он не
     * задаёт, а разрешимость уровня — отдельная ось.
     */
    static AttachedAlgoOrder attachedProtection(Long id, String size) {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setId(id);
        protection.setStatus(AttachedAlgoOrder.Status.ACTIVE);
        protection.setSize(decimal(size));
        return protection;
    }

    /** Та же защита с НАБЛЮДЁННЫМ уровнем остановки убытка. */
    static AttachedAlgoOrder attachedProtection(Long id, String size, String stopPrice) {
        AttachedAlgoOrder protection = attachedProtection(id, size);
        protection.setStopLossTriggerPrice(decimal(stopPrice));
        return protection;
    }

    /** Живой эпизод позиции с названным нетто-размером. */
    static Position livePosition(String size) {
        Position position = new Position();
        position.setId(50L);
        position.setDealId(DEAL_ID);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(decimal(size));
        return position;
    }

    /** Закрытый эпизод с НЕОБНУЛЁННЫМ внешним размером — штатное состояние. */
    static Position closedPosition(String size) {
        Position position = new Position();
        position.setId(51L);
        position.setDealId(DEAL_ID);
        position.setStatus(Position.Status.CLOSED);
        position.setExternalSize(decimal(size));
        return position;
    }

    // ------------------------------------------------------------------
    // Контекст прохода
    // ------------------------------------------------------------------

    /** Контекст базовой сборки: граф предъявлен целиком, деталь рабочая. */
    static DealContext context(Deal deal) {
        return contextBuilder(deal).build();
    }

    /** Заготовка контекста: граф полон, деталь с объявлением транша. */
    static DealContext.DealContextBuilder contextBuilder(Deal deal) {
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument())
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE)))
                .actionStates(new ArrayList<>())
                .graphComplete(Boolean.TRUE);
    }

    /** Инструмент сделки с расчётной валютой. */
    static Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("ETH-USDT-SWAP");
        instrument.setExternalSettlementCurrency("USDT");
        return instrument;
    }

    /** Снимок средств с названным моментом обновления. */
    static BalanceContainer balance(OffsetDateTime updatedAt) {
        BalanceContainer container = new BalanceContainer();
        container.setId(60L);
        container.setExchangeAccountId(ACCOUNT_ID);
        container.setExternalUpdatedAt(updatedAt);
        return container;
    }

    /** Момент, отстоящий от текущего на названное число минут назад. */
    static OffsetDateTime minutesAgo(long minutes) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
    }

    // ------------------------------------------------------------------
    // Объявление стратегии
    // ------------------------------------------------------------------

    /** Закреплённая деталь с названными объявлениями траншей. */
    static StrategyDetail detail(StrategyTranche... declarations) {
        StrategyDetail detail = new StrategyDetail();
        detail.setId(41L);
        detail.setTranches(new ArrayList<>(List.of(declarations)));
        return detail;
    }

    /** Объявление транша: переоткрытие разрешено либо запрещено, шагов нет. */
    static StrategyTranche declaration(Long id, Boolean reopenAllowed) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(id);
        declaration.setKey("t" + id);
        declaration.setPositionReopenAllowed(reopenAllowed);
        return declaration;
    }

    /** Объявление с шагами названного статуса транша. */
    static StrategyTranche declaration(Long id, Boolean reopenAllowed, DealTranche.Status status,
                                       StrategyStep... steps) {
        StrategyTranche declaration = declaration(id, reopenAllowed);
        Map<DealTranche.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(status, new ArrayList<>(List.of(steps)));
        declaration.setStepsByStatus(byStatus);
        return declaration;
    }

    /** Шаг с названными типом и пакетом действий. */
    static StrategyStep step(Long id, StrategyStepType type, StrategyAction... actions) {
        StrategyStep step = new StrategyStep();
        step.setId(id);
        step.setStepType(type);
        step.setActions(new ArrayList<>(List.of(actions)));
        return step;
    }

    /** Объявление действия: создание обычной заявки. */
    static StrategyAction action(Long id) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(id);
        action.setKey("a" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        return action;
    }

    /** Объявление ЗАЩИТНОГО действия: создание условной заявки со стопом. */
    static StrategyAction protectiveAction(Long id) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setKey("p" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return action;
    }

    /** Строка исполнения стратегийного действия названного уровня и эпизода. */
    static DealActionState strategyRow(Long actionId, Long trancheId, Integer episodeSeq,
                                       DealActionStateStatus status) {
        DealActionState state = new DealActionState();
        state.setId(70L + actionId);
        state.setDealId(DEAL_ID);
        state.setActionKind(ActionKind.STRATEGY);
        state.setStrategyActionId(actionId);
        state.setDealTrancheId(trancheId);
        state.setTrancheEpisodeSeq(episodeSeq);
        state.setStatus(status);
        return state;
    }

    // ------------------------------------------------------------------
    // Условия шагов, фичи момента и строка пары
    // ------------------------------------------------------------------

    /** Условие, не спрашивающее рыночных данных вовсе: правил у него нет. */
    static StrategyCondition plainCondition() {
        return new StrategyCondition(new ArrayList<>());
    }

    /** Условие, читающее значение индикатора по названному ключу. */
    static StrategyCondition indicatorCondition(String indicatorKey) {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.INDICATOR);
        left.setIndicatorKey(indicatorKey);
        StrategyConditionOperand right = new StrategyConditionOperand();
        right.setSourceType(StrategyConditionSourceType.CONSTANT);
        right.setValueType(ConstantValueType.NUMBER);
        right.setValue("0");
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setOperator(StrategyConditionOperator.GT);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return new StrategyCondition(new ArrayList<>(List.of(rule)));
    }

    /** Фичи момента без единого снятого значения. */
    static MarketFeatures emptyFeatures() {
        return MarketFeatures.builder().latestIndicators(Map.of()).structures(Map.of()).build();
    }

    /** Фичи момента, накрывающие названный ключ индикатора. */
    static MarketFeatures featuresWith(String indicatorKey) {
        EmaValue value = new EmaValue();
        value.setEma(new BigDecimal("1"));
        return MarketFeatures.builder()
                .latestIndicators(Map.of(indicatorKey, value))
                .structures(Map.of())
                .build();
    }

    /** Строка пары «счёт, инструмент» с названной ступенью лестницы. */
    static AccountInstrumentState pairState(Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setExchangeAccountId(ACCOUNT_ID);
        state.setInstrumentId(INSTRUMENT_ID);
        state.setSafetyRung(rung);
        return state;
    }

    /** Настройка реакции шага на устаревание данных: защищённая и незащищённая ветви. */
    static StrategyMarketDataExpiredSetting expiredSetting(MarketDataExpiredAction whenProtected,
                                                           MarketDataExpiredAction whenUnprotected) {
        StrategyMarketDataExpiredSetting setting = new StrategyMarketDataExpiredSetting();
        setting.setProtectedPositionAction(whenProtected);
        setting.setUnprotectedPositionAction(whenUnprotected);
        return setting;
    }

    /** Десятичное значение; пусто — значения нет. */
    static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
