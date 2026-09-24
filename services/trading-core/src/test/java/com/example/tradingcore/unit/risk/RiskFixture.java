package com.example.tradingcore.unit.risk;

import static java.util.Objects.isNull;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.ResolvedTakeProfitPrice;
import com.example.strategy.engine.calc.SizeMode;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Общая сборка предмета {@code trading-core-risk} — слоя преконтроля
 * риска и карты его реакций
 * (`.claude/tests/cases/trading-core-risk.md` §«Чем достаются выходы»).
 *
 * <p><b>Субстрата у предмета нет:</b> валидатор, резолвер, гейт и
 * счётчик чисел конструируются {@code new}; аннотации {@code @Component}
 * и {@code @Service} на классах — деталь потребителя. Границы хранилища
 * подменяет {@code RiskHarness}; всё остальное — настоящее.
 *
 * <p><b>Состояние собирается настоящими полями доменных моделей</b> —
 * ногами траншей с их наливом, эпизодом позиции, защитами с их
 * уровнями, — а не подменёнными предикатами: подменённый предикат
 * проверял бы слой против модели, которой в проде не существует
 * (`.claude/rules/codestyle.md` §«Тесты доменных моделей»).
 *
 * <p><b>Базовая сборка U1.1</b> — {@link #workingContext()} плюс
 * {@link #entryAction()}. Её числа сходятся так: убыток на стопе на
 * единицу равен {@code (3000 − 2910) + 0.0005 × (3000 + 2910) = 92.955},
 * риск акта — {@code 92.955 × 10 × 0.1 = 92.955}, нотинал акта —
 * {@code 10 × 0.1 × 3000 = 3000}; поактный потолок
 * {@code 1 % × 10000 = 100} их накрывает.
 */
final class RiskFixture {

    /** Биржевой счёт базовой сборки — первая половина ключа пары. */
    static final Long ACCOUNT_ID = 3L;

    /** Инструмент базовой сборки — вторая половина ключа пары. */
    static final Long INSTRUMENT_ID = 7L;

    /** Транш базовой сборки; ноги и защиты висят на нём. */
    static final Long TRANCHE_ID = 10L;

    /** Соседний транш той же сделки — операнд агрегатности потолков. */
    static final Long NEIGHBOUR_TRANCHE_ID = 11L;

    /** Тенант-владелец счёта: по нему резолвятся числа риск-аппетита. */
    static final String TENANT = "tn-0001";

    /** Якорь себестоимости базовой сборки — плановая цена действия. */
    static final BigDecimal ANCHOR = new BigDecimal("3000");

    /** Уровень остановки убытка базовой сборки: убыточная сторона длинного. */
    static final BigDecimal STOP = new BigDecimal("2910");

    /** Ставка комиссии базовой сборки. */
    static final String FEE = "0.0005";

    /** Рабочее плечо пары базовой сборки — ниже биржевого максимума 125. */
    static final Integer LEVERAGE = 10;

    /** Стоимость контракта базовой сборки. */
    static final String CONTRACT_VALUE = "0.1";

    /** Живая база счёта базовой сборки — делитель всех потолков. */
    static final String BASE = "10000";

    /** Размер действия базовой сборки в контрактах. */
    static final String SIZE = "10";

    /** Риск акта базовой сборки: {@code 92.955 × 10 × 0.1}. */
    static final BigDecimal ACT_RISK = new BigDecimal("92.955");

    private RiskFixture() {
    }

    // ------------------------------------------------------------------
    // Границы хранилища: что отдают их стабы
    // ------------------------------------------------------------------

    /** Справочные правила рабочего инструмента: материализован и торгуем. */
    static InstrumentExternalRules workingRules() {
        return rules(CONTRACT_VALUE, "1", "1", FEE);
    }

    /** Правила с названными стоимостью контракта, шагом лота, минимумом и ставкой. */
    static InstrumentExternalRules rules(String contractValue, String lotSize, String minSize, String feeRate) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setStatus(InstrumentExternalRules.Status.LIVE);
        rules.setExternalContractValue(contractValue);
        rules.setExternalLotSize(lotSize);
        rules.setExternalMinSize(minSize);
        rules.setExternalTakerFeeRate(feeRate);
        rules.setExternalMaxLimitSize("100000");
        rules.setExternalMaxMarketSize("50000");
        rules.setExternalMaxLeverage("125");
        rules.setExternalTickSize("0.1");
        return rules;
    }

    /** Строка пары «счёт, инструмент»: изолированная маржа, плечо назначено, ступени не стои́т. */
    static AccountInstrumentState workingPairState() {
        return pairState(Instrument.SafetyRung.ACTIVE);
    }

    /** Строка пары с названной ступенью лестницы радиуса. */
    static AccountInstrumentState pairState(Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setExchangeAccountId(ACCOUNT_ID);
        state.setInstrumentId(INSTRUMENT_ID);
        state.setSafetyRung(rung);
        state.setMarginMode(Instrument.MarginMode.ISOLATED);
        state.setLeverage(LEVERAGE);
        return state;
    }

    /** Строка риск-аппетита тенанта: оба числа назначены. */
    static Tenant workingAppetite() {
        return appetite("1", 3);
    }

    /** Строка риск-аппетита с названными процентом одновременного риска и порогом серии. */
    static Tenant appetite(String simultaneousPercent, Integer lossLimit) {
        Tenant tenant = new Tenant();
        tenant.setInternalId(TENANT);
        tenant.setGlobalSimultaneousRiskPerDealPercent(decimal(simultaneousPercent));
        tenant.setGlobalCatastrophicRiskPerDealMultiplier(new BigDecimal("300"));
        tenant.setGlobalConsecutiveLossLimit(lossLimit);
        return tenant;
    }

    // ------------------------------------------------------------------
    // Деталь стратегии и контекст прохода
    // ------------------------------------------------------------------

    /** Закреплённая деталь стратегии: все её числа объявлены. */
    static StrategyDetail workingDetail() {
        return detail("1", "3", "1", "300");
    }

    /** Деталь с названными процентом на действие и тремя множителями; пусто — не объявлено. */
    static StrategyDetail detail(String perAction, String cumulativeMultiplier, String strategySimultaneous,
                                 String catastrophicMultiplier) {
        StrategyDetail detail = new StrategyDetail();
        detail.setId(21L);
        detail.setRiskPerActionPercent(decimal(perAction));
        detail.setCumulativeRiskPerDealMultiplier(decimal(cumulativeMultiplier));
        detail.setStrategySimultaneousRiskPerDealPercent(decimal(strategySimultaneous));
        detail.setStrategyCatastrophicRiskPerDealMultiplier(decimal(catastrophicMultiplier));
        return detail;
    }

    /** Биржевой счёт с названной живой базой риска. */
    static ExchangeAccount account(String riskBase) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId("ea-0001");
        account.setTenantId(TENANT);
        account.setRiskBase(decimal(riskBase));
        return account;
    }

    /** Инструмент с названной расчётной валютой. */
    static Instrument instrument(String settlementCurrency) {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("ETH-USDT-SWAP");
        instrument.setExternalSettlementCurrency(settlementCurrency);
        return instrument;
    }

    /** Контекст базовой сборки поверх пустой сделки. */
    static DealContext workingContext() {
        return contextBuilder(emptyDeal()).build();
    }

    /** Контекст базовой сборки поверх названной сделки. */
    static DealContext context(Deal deal) {
        return contextBuilder(deal).build();
    }

    /** Заготовка контекста: граф предъявлен целиком, деталь и счёт рабочие. */
    static DealContext.DealContextBuilder contextBuilder(Deal deal) {
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account(BASE))
                .instrument(instrument("USDT"))
                .strategyDetail(workingDetail())
                .graphComplete(true);
    }

    // ------------------------------------------------------------------
    // Граф сделки
    // ------------------------------------------------------------------

    /** Сделка без траншей, ног и эпизодов; снимка базы у неё нет. */
    static Deal emptyDeal() {
        return deal(BigDecimal.ZERO, null);
    }

    /** Сделка с названными взятым за жизнь риском и снимком базы. */
    static Deal deal(BigDecimal plannedRisk, BigDecimal frozenBase) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setPlannedRiskAmount(plannedRisk);
        deal.setPlannedRiskEquityBase(frozenBase);
        deal.setTranches(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        return deal;
    }

    /** Транш базовой сборки с названными ногами и отдельными защитами. */
    static DealTranche tranche(List<Order> orders, List<AlgoOrder> protections) {
        return tranche(TRANCHE_ID, orders, protections);
    }

    /** Транш с названным идентификатором: операнд агрегатности потолков. */
    static DealTranche tranche(Long id, List<Order> orders, List<AlgoOrder> protections) {
        DealTranche tranche = new DealTranche();
        tranche.setId(id);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setOrders(new ArrayList<>(orders));
        tranche.setAlgoOrders(new ArrayList<>(protections));
        return tranche;
    }

    /** Входная нога транша базовой сборки. */
    static Order entryLeg(Order.Status status, String plannedRisk, String plannedSize, String filled) {
        return entryLeg(TRANCHE_ID, status, plannedRisk, plannedSize, filled);
    }

    /** Входная нога названного транша: плановые цены и стоимость контракта — базовой сборки. */
    static Order entryLeg(Long trancheId, Order.Status status, String plannedRisk, String plannedSize,
                          String filled) {
        Order leg = new Order();
        leg.setId(100L + trancheId);
        leg.setDealTrancheId(trancheId);
        leg.setType(Order.Type.ENTRY);
        leg.setStatus(status);
        leg.setPositionReducingOnly(false);
        leg.setPlannedRiskAmount(decimal(plannedRisk));
        leg.setPlannedSizeContracts(decimal(plannedSize));
        leg.setAccumulatedFillSize(decimal(filled));
        leg.setPlannedEntryPrice(ANCHOR);
        leg.setPlannedStopPrice(STOP);
        leg.setPlannedContractValue(new BigDecimal(CONTRACT_VALUE));
        return leg;
    }

    /** Живая отдельная защита транша базовой сборки с названным уровнем. */
    static AlgoOrder protection(String triggerPrice) {
        return protection(55L, TRANCHE_ID, triggerPrice, "100");
    }

    /** Живая отдельная защита названного транша: идентификатор, уровень и размер названы. */
    static AlgoOrder protection(Long id, Long trancheId, String triggerPrice, String size) {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setType(AlgoOrder.TriggerPriceType.MARK);
        stopLoss.setValue(decimal(triggerPrice));
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(id);
        algoOrder.setDealTrancheId(trancheId);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(decimal(size));
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(stopLoss, null), null));
        return algoOrder;
    }

    /** Живой эпизод позиции с названными нетто-размером и средней ценой входа. */
    static Position episode(String size, BigDecimal averagePrice) {
        Position position = new Position();
        position.setId(9L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(decimal(size));
        position.setExternalAverageEntryPrice(averagePrice);
        return position;
    }

    // ------------------------------------------------------------------
    // Рассчитанные действия
    // ------------------------------------------------------------------

    /** Risk-creating вход базовой сборки: размер 10, якорь 3000, стоп 2910. */
    static CalculatedStrategyAction entryAction() {
        return entryAction(SIZE, ANCHOR, STOP.toPlainString());
    }

    /** Risk-creating вход с названными размером, плановой ценой и уровнем стопа. */
    static CalculatedStrategyAction entryAction(String sizeContracts, BigDecimal anchor, String stopPrice) {
        return CalculatedStrategyAction.builder()
                .sourceAction(entrySourceAction())
                .calculatedPrice(price(anchor, stopPrice))
                .calculatedSize(size(sizeContracts))
                .description("entry")
                .build();
    }

    /** Объявление входа: создание ordinary-заявки, не уменьшающей позицию. */
    static StrategyOrderAction entrySourceAction() {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(1L);
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setPositionReducingOnly(false);
        return action;
    }

    /** Действие, помеченное «только уменьшает позицию»: риска не создаёт. */
    static CalculatedStrategyAction reducingOnlyAction() {
        StrategyOrderAction action = entrySourceAction();
        action.setKey("exit");
        action.setPositionReducingOnly(true);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(ANCHOR, null))
                .calculatedSize(size(SIZE))
                .description("reduce-only")
                .build();
    }

    /** Снятие защиты: risk-weakening, уровня не ставит. */
    static CalculatedStrategyAction weakeningAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(2L);
        action.setKey("cancel-protection");
        action.setActionType(StrategyActionType.CANCEL_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(null, null))
                .calculatedSize(size("1"))
                .description("weakening")
                .build();
    }

    /** Защитное действие, ставящее уровень остановки убытка впервые. */
    static CalculatedStrategyAction protectionAction(String stopPrice) {
        return CalculatedStrategyAction.builder()
                .sourceAction(protectionSourceAction())
                .calculatedPrice(price(ANCHOR, stopPrice))
                .calculatedSize(size("1"))
                .description("protection")
                .build();
    }

    /** Объявление защитного создания с уровнем остановки убытка. */
    static StrategyAlgoOrderAction protectionSourceAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(3L);
        action.setKey("protection");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return action;
    }

    /** Перенос уже стоящего уровня: замещение с указанной целью. */
    static CalculatedStrategyAction transferAction(String stopPrice) {
        StrategyAlgoOrderAction action = protectionSourceAction();
        action.setKey("transfer");
        action.setActionType(StrategyActionType.REPLACE_ACTION);
        action.setTargetActionKey("protection");
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(ANCHOR, stopPrice))
                .calculatedSize(size("1"))
                .description("transfer")
                .build();
    }

    /** Защитное создание с НАБЛЮДАЕМЫМ уровнем: блок настроек трейлинга объявлен. */
    static CalculatedStrategyAction trailingAction(String stopPrice) {
        StrategyAlgoOrderAction action = protectionSourceAction();
        action.setKey("trailing");
        action.setTrailingSettings(new TrailingSettings());
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(ANCHOR, stopPrice))
                .calculatedSize(size("1"))
                .description("trailing")
                .build();
    }

    /** Постановка уровня фиксации прибыли: контроля риска не ослабляет. */
    static CalculatedStrategyAction takeProfitAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(4L);
        action.setKey("take-profit");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.TAKE_PROFIT);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(null, null))
                .calculatedSize(size("1"))
                .description("take-profit")
                .build();
    }

    /** Вход, у которого признак «только уменьшает позицию» не объявлен вовсе. */
    static CalculatedStrategyAction entryActionWithUndeclaredReducingFlag(String stopPrice) {
        StrategyOrderAction action = entrySourceAction();
        action.setPositionReducingOnly(null);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(ANCHOR, stopPrice))
                .calculatedSize(size(SIZE))
                .description("entry with undeclared reducing flag")
                .build();
    }

    /** Системный акт: исходного объявления у него нет вовсе. */
    static CalculatedStrategyAction systemAction(String stopPrice) {
        return CalculatedStrategyAction.builder()
                .calculatedPrice(price(ANCHOR, stopPrice))
                .calculatedSize(size(SIZE))
                .description("system act")
                .build();
    }

    /** Действие, у которого уровень стопа ОБЪЯВЛЕН, а цена срабатывания пуста. */
    static CalculatedStrategyAction actionWithUnresolvedStopTrigger() {
        return CalculatedStrategyAction.builder()
                .sourceAction(protectionSourceAction())
                .calculatedPrice(CalculatedPrice.builder()
                        .purpose(StrategyPricePurpose.ORDER_LIMIT_PRICE)
                        .priceMode(PriceMode.EXPLICIT)
                        .roundedPrice(ANCHOR)
                        .stopLossPrice(ResolvedStopLossPrice.builder()
                                .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                                .build())
                        .build())
                .calculatedSize(size("1"))
                .description("declared stop without trigger price")
                .build();
    }

    /** Живой эпизод с названной марк-ценой — операнд охраны переноса уровня. */
    static Position episodeWithMark(String size, BigDecimal averagePrice, String markPrice) {
        Position position = episode(size, averagePrice);
        position.setExternalMarkPrice(decimal(markPrice));
        return position;
    }

    /** Живой эпизод с названной ценой ликвидации. */
    static Position episodeWithLiquidation(String size, BigDecimal averagePrice, String liquidationPrice) {
        Position position = episode(size, averagePrice);
        position.setExternalLiquidationPrice(decimal(liquidationPrice));
        return position;
    }

    /** Рассчитанная цена: режим явный, якорь и уровень стопа названы. */
    static CalculatedPrice price(BigDecimal anchor, String stopPrice) {
        return priceBuilder(anchor, stopPrice).build();
    }

    /** Заготовка рассчитанной цены — под кейсы, меняющие режим и уровень прибыли. */
    static CalculatedPrice.CalculatedPriceBuilder priceBuilder(BigDecimal anchor, String stopPrice) {
        return CalculatedPrice.builder()
                .purpose(StrategyPricePurpose.ORDER_LIMIT_PRICE)
                .priceMode(PriceMode.EXPLICIT)
                .roundedPrice(anchor)
                .stopLossPrice(isNull(stopPrice) ? null : ResolvedStopLossPrice.builder()
                        .triggerPrice(new BigDecimal(stopPrice))
                        .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                        .build());
    }

    /** Уровень фиксации прибыли с названной ценой срабатывания. */
    static ResolvedTakeProfitPrice takeProfit(String triggerPrice) {
        return ResolvedTakeProfitPrice.builder()
                .triggerPrice(new BigDecimal(triggerPrice))
                .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                .build();
    }

    /** Рассчитанный размер в контрактах. */
    static CalculatedSize size(String sizeContracts) {
        return CalculatedSize.builder()
                .sizeContracts(decimal(sizeContracts))
                .sizeMode(SizeMode.OPEN_OR_INCREASE)
                .build();
    }

    /** Рассчитанный размер без числа контрактов: режим объявлен, величины нет. */
    static CalculatedSize sizeWithoutContracts() {
        return CalculatedSize.builder().sizeMode(SizeMode.OPEN_OR_INCREASE).build();
    }

    /** То же действие с подменённой рассчитанной ценой. */
    static CalculatedStrategyAction withPrice(CalculatedStrategyAction action, CalculatedPrice price) {
        return CalculatedStrategyAction.builder()
                .sourceAction(action.getSourceAction())
                .calculatedPrice(price)
                .calculatedSize(action.getCalculatedSize())
                .description(action.getDescription())
                .build();
    }

    /** То же действие с подменённым рассчитанным размером. */
    static CalculatedStrategyAction withSize(CalculatedStrategyAction action, CalculatedSize size) {
        return CalculatedStrategyAction.builder()
                .sourceAction(action.getSourceAction())
                .calculatedPrice(action.getCalculatedPrice())
                .calculatedSize(size)
                .description(action.getDescription())
                .build();
    }

    // ------------------------------------------------------------------
    // Чтение выхода
    // ------------------------------------------------------------------

    /** Коды вердикта в порядке накопления: и состав перечня, и позиция члена. */
    static List<RiskCheckCode> codes(RiskValidationResult result) {
        return codesOf(result.getChecks());
    }

    /** Коды перечня проверок в порядке накопления. */
    static List<RiskCheckCode> codesOf(List<RiskCheckResult> checks) {
        return checks.stream().map(RiskCheckResult::getCode).toList();
    }

    /** Блокирующий вердикт, построенный ПРЯМО: предмет карты — свёртка, а не производитель. */
    static RiskValidationResult blockedVerdict(RiskCheckCode... codes) {
        return RiskValidationResult.builder()
                .decision(RiskValidationResult.RiskDecision.BLOCKED)
                .checks(Arrays.stream(codes)
                        .map(code -> RiskCheckResult.blocked(code, code.name(), null))
                        .toList())
                .comment("blocked")
                .build();
    }

    /** Вердикт с названным решением и пустым перечнем проверок. */
    static RiskValidationResult verdict(RiskValidationResult.RiskDecision decision) {
        return RiskValidationResult.builder()
                .decision(decision)
                .checks(new ArrayList<>())
                .comment("verdict " + decision)
                .build();
    }

    /** Десятичное значение либо пустота — под кейсы незаданных операндов. */
    static BigDecimal decimal(String value) {
        return isNull(value) ? null : new BigDecimal(value);
    }
}
