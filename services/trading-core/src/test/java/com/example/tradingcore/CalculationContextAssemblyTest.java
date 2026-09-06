package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.CalculationException;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingcore.domain.calc.CalculationContextFactory;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Сборка контекста расчёта на одно действие.
 *
 * <p><b>Предмет — резолв готового значения по авторскому имени.</b>
 * Прежняя редакция искала значение равенством «идентичность вычисления ==
 * идентификатор строки объявления»: в монолите одна настройка и была одним
 * вычислением, а у сервисов это две разные номенклатуры — резолв возвращал
 * бы пустоту ВСЕГДА, и цена по уровню индикатора считалась бы на
 * недоступном операнде. Поэтому проверяется не наличие раскладки, а исход
 * резолва.
 */
class CalculationContextAssemblyTest {

    private static final Long DEAL_ID = 7L;
    private static final Long ACCOUNT_ID = 3L;
    private static final Long INSTRUMENT_ID = 5L;
    private static final Long DETAIL_ID = 11L;
    private static final Long TRANCHE_ID = 21L;
    private static final String ATR = "atr";
    private static final String RANGE = "range";

    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);
    private final MarketFeatureService marketFeatureService = mock(MarketFeatureService.class);
    private final InstrumentExternalRulesDataService rulesDataService =
            mock(InstrumentExternalRulesDataService.class);

    private final CalculationContextFactory factory =
            new CalculationContextFactory(strategyDataService, marketFeatureService, rulesDataService);

    @BeforeEach
    void setUp() {
        when(strategyDataService.findOwnerOfDetailWithSettings(DETAIL_ID))
                .thenReturn(Optional.of(new Strategy()));
        when(marketFeatureService.readForCalculation(any(), any())).thenReturn(features());
        when(rulesDataService.findByInstrumentId(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    /** Готовое значение и структура резолвятся авторским именем операнда. */
    @Test
    void featuresResolveByAuthorKey() {
        StrategyAlgoOrderAction action = protectionStep(101L);
        DealContext dealContext = dealContext(detailWithLadder(action));

        CalculationContext context = factory.build(dealContext, action, tranche());

        assertThat(context.findIndicatorValueByKey(ATR)).isNotNull();
        assertThat(context.findMarketStructureByKey(RANGE)).isNotNull();
        assertThat(context.findIndicatorValueByKey("nobody-declared-this")).isNull();
    }

    /**
     * Фичи снимаются заново на КАЖДОЕ действие.
     *
     * <p>Контекст расчёта обязан быть собран максимально близко ко времени
     * создания команды: между двумя действиями одного шага меняются и
     * цены, и позиция, и заявки. Общая связка на проход отдала бы второму
     * действию числа, посчитанные до первого.
     */
    @Test
    void everyActionGetsItsOwnFreshRead() {
        StrategyAlgoOrderAction action = protectionStep(101L);
        DealContext dealContext = dealContext(detailWithLadder(action));

        factory.build(dealContext, action, tranche());
        factory.build(dealContext, action, tranche());

        verify(marketFeatureService, times(2)).readForCalculation(any(), any());
    }

    /**
     * У набора из ОДНОЙ ступени сумма пуста, у лестницы — посчитана.
     *
     * <p>Одиночной ступени вся экспозиция причитается по построению, и
     * операнд ей не нужен; у лестницы пустота читалась бы нулём и отдала
     * бы последней ступени всю экспозицию — умолчание в благоприятную
     * сторону.
     */
    @Test
    void ladderTotalIsEmptyForASingleStepAndSummedForALadder() {
        StrategyAlgoOrderAction single = protectionStep(101L);
        DealContext soleStep = dealContext(detailWithLadder(single));

        assertThat(factory.build(soleStep, single, tranche()).getLadderPreviousStepsTotal()).isNull();

        StrategyAlgoOrderAction first = protectionStep(101L);
        StrategyAlgoOrderAction second = protectionStep(102L);
        DealContext ladder = dealContext(detailWithLadder(first, second), placedState(first.getId()));

        assertThat(factory.build(ladder, second, trancheWithPlacedStep()).getLadderPreviousStepsTotal())
                .isEqualByComparingTo("4");
    }

    /**
     * Ни одна ступень лестницы ещё не поставлена — сумма НОЛЬ, а не
     * пустота: у лестницы пустота есть «неизвестно», и калькулятор на ней
     * отказывает, тогда как здесь известно и равно нулю.
     */
    @Test
    void ladderWithNothingPlacedYetSumsToZero() {
        StrategyAlgoOrderAction first = protectionStep(101L);
        StrategyAlgoOrderAction second = protectionStep(102L);
        DealContext ladder = dealContext(detailWithLadder(first, second));

        assertThat(factory.build(ladder, second, tranche()).getLadderPreviousStepsTotal())
                .isEqualByComparingTo("0");
    }

    /**
     * База риска — снимок сделки, если он есть, иначе живая база счёта.
     *
     * <p>Развилка одна на преконтроль и на сайзинг: разойдись они, проверка
     * и размер считались бы от разных потолков.
     */
    @Test
    void riskBaseTakesTheFrozenSnapshotFirst() {
        StrategyAlgoOrderAction action = protectionStep(101L);
        DealContext live = dealContext(detailWithLadder(action));

        assertThat(factory.build(live, action, tranche()).getRiskBase()).isEqualByComparingTo("1000");

        DealContext frozen = dealContext(detailWithLadder(action));
        frozen.getDeal().setPlannedRiskEquityBase(new BigDecimal("800"));

        assertThat(factory.build(frozen, action, tranche()).getRiskBase()).isEqualByComparingTo("800");
    }

    /**
     * Структурно неполный вход — контролируемая ошибка расчёта, а не
     * контекст с пустотами: калькулятор упал бы на нём неожиданным
     * исключением, и потребитель не получил бы машинного кода.
     */
    @Test
    void structurallyIncompleteInputIsAControlledError() {
        DealContext dealContext = dealContext(detailWithLadder(protectionStep(101L)));

        assertThatThrownBy(() -> factory.build(dealContext, null, tranche()))
                .isInstanceOf(CalculationException.class)
                .hasMessageContaining("strategy action");
    }

    private MarketFeatures features() {
        AtrValue atr = new AtrValue();
        atr.setAtr(new BigDecimal("5"));
        MarketStructure structure = new MarketStructure();
        structure.setType(MarketStructure.Type.RANGE);
        return MarketFeatures.builder()
                .latestIndicators(Map.<String, IndicatorValue>of(ATR, atr))
                .structures(Map.of(RANGE, structure))
                .build();
    }

    private DealContext dealContext(StrategyDetail detail, DealActionState... states) {
        return DealContext.builder()
                .deal(deal())
                .exchangeAccount(account())
                .instrument(instrument())
                .strategyDetail(detail)
                .actionStates(List.of(states))
                .build();
    }

    private Deal deal() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(INSTRUMENT_ID);
        deal.setStrategyDetailId(DETAIL_ID);
        deal.setPositions(List.of());
        return deal;
    }

    private ExchangeAccount account() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setRiskBase(new BigDecimal("1000"));
        return account;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId("in-0001");
        return instrument;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setEpisodeSeq(1);
        tranche.setOrders(List.<Order>of());
        tranche.setAlgoOrders(List.<AlgoOrder>of());
        return tranche;
    }

    /** Транш, у которого уже стоит условная заявка первой ступени. */
    private DealTranche trancheWithPlacedStep() {
        AlgoOrder placed = new AlgoOrder();
        placed.setId(901L);
        placed.setSize(new BigDecimal("4"));
        DealTranche tranche = tranche();
        tranche.setAlgoOrders(List.of(placed));
        return tranche;
    }

    private DealActionState placedState(Long strategyActionId) {
        DealActionState state = new DealActionState();
        state.setActionKind(ActionKind.STRATEGY);
        state.setStrategyActionId(strategyActionId);
        state.setDealTrancheId(TRANCHE_ID);
        state.setTrancheEpisodeSeq(1);
        state.targetAt(TargetEntityType.ALGO_ORDER, 901L);
        return state;
    }

    private StrategyAlgoOrderAction protectionStep(Long id) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setKey("protection-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return action;
    }

    private StrategyDetail detailWithLadder(StrategyAlgoOrderAction... ladder) {
        StrategyStep step = new StrategyStep();
        step.setActions(List.of(ladder));

        StrategyTranche tranche = new StrategyTranche();
        tranche.setStepsByStatus(Map.of(DealTranche.Status.MANAGING, List.of(step)));

        StrategyDetail detail = new StrategyDetail();
        detail.setId(DETAIL_ID);
        detail.setTranches(List.of(tranche));
        return detail;
    }
}
