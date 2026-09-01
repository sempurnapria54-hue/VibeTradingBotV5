package com.example.tradingbot.domain.deal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.safety.HoldScope;
import com.example.tradingbot.domain.service.market.MarketDataExpirationChecker;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает объявленные реакции шага на устаревание данных с их
 * потребителем: до этого захода {@code GRACEFUL_CLOSE} и {@code KILL_SWITCH}
 * не наступали ни разу — устаревшие данные просто исчезали из контекста, и
 * шаг молча не брался к применению.
 *
 * <p>Несущее для этого теста — <b>две тропы вывода сделки из штатного
 * ведения</b> (docs/lifecycles/Deal.md §«Причина выхода из штатного
 * ведения»): управляемая пишет причину на ребре в EXIT_PENDING, аварийная
 * поднимает жёсткую ступень инструмента и статуса сама не двигает — увод в
 * ошибочное состояние принадлежит шагу энфорсмента, а не этому handler'у.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DealActiveHandlerExpiredDataTest {

    private static final String INDICATOR_KEY = "ema-fast";

    @Mock
    private DealFsmSupport support;

    private final MarketDataExpirationChecker expirationChecker = new MarketDataExpirationChecker();

    private DealActiveHandler handler() {
        return new DealActiveHandler(support, expirationChecker);
    }

    @Test
    @DisplayName("Свежие данные реакции не производят: сделка остаётся в ведении")
    void freshDataProducesNoReaction() {
        DealContext dealContext = context(step(MarketDataExpiredAction.WAIT,
                MarketDataExpiredAction.GRACEFUL_CLOSE), bareTranche());
        contextWith(indicatorPresent());

        assertTrue(handler().checkTransition(dealContext).isEmpty());
    }

    @Test
    @DisplayName("Незащищённый транш при устаревших данных уходит в EXIT_PENDING с причиной устаревания")
    void unprotectedTrancheLeavesNormalFlow() {
        DealContext dealContext = context(step(MarketDataExpiredAction.WAIT,
                MarketDataExpiredAction.GRACEFUL_CLOSE), bareTranche());
        contextWith(Map.of());

        DealTransition transition = handler().checkTransition(dealContext).orElseThrow();

        assertEquals(Deal.Status.EXIT_PENDING, transition.getNextStatus());
        assertEquals(Deal.ShutdownReason.MARKET_DATA_EXPIRED, transition.getShutdownReason());
    }

    @Test
    @DisplayName("Покрытый транш при тех же устаревших данных из ведения не выводится")
    void coveredTrancheStaysInNormalFlow() {
        DealContext dealContext = context(step(MarketDataExpiredAction.WAIT,
                MarketDataExpiredAction.GRACEFUL_CLOSE), coveredTranche());
        contextWith(Map.of());

        assertTrue(handler().checkTransition(dealContext).isEmpty());
    }

    @Test
    @DisplayName("Аварийная реакция поднимает жёсткую ступень инструмента, статуса сама не двигает")
    void killSwitchRaisesInstrumentRung() {
        DealContext dealContext = context(step(MarketDataExpiredAction.WAIT,
                MarketDataExpiredAction.KILL_SWITCH), bareTranche());
        contextWith(Map.of());

        DealTransition transition = handler().checkTransition(dealContext).orElseThrow();

        assertNull(transition.getNextStatus());
        assertEquals(HoldScope.INSTRUMENT, transition.getHoldSignal().getScope());
        assertEquals(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED, transition.getHoldSignal().getCode());
    }

    @Test
    @DisplayName("Аварийная реакция сильнее управляемой: снятие риска не отдаётся расчёту по тем же данным")
    void killSwitchDominatesGracefulClose() {
        DealContext dealContext = context(List.of(step(MarketDataExpiredAction.WAIT,
                        MarketDataExpiredAction.GRACEFUL_CLOSE),
                step(MarketDataExpiredAction.WAIT, MarketDataExpiredAction.KILL_SWITCH)), bareTranche());
        contextWith(Map.of());

        DealTransition transition = handler().checkTransition(dealContext).orElseThrow();

        assertNull(transition.getNextStatus());
        assertEquals(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED, transition.getHoldSignal().getCode());
    }

    @Test
    @DisplayName("Запрошенное сворачивание и терминальность траншей проверяются раньше устаревания")
    void declaredTransitionsWinOverExpiration() {
        DealContext dealContext = context(step(MarketDataExpiredAction.WAIT,
                MarketDataExpiredAction.KILL_SWITCH), bareTranche());
        dealContext.getDeal().setShutdownReason(Deal.ShutdownReason.STRATEGY_DELETED);
        contextWith(Map.of());

        DealTransition transition = handler().checkTransition(dealContext).orElseThrow();

        assertEquals(Deal.Status.EXIT_PENDING, transition.getNextStatus());
        assertNull(transition.getHoldSignal());
    }

    private void contextWith(Map<String, IndicatorValue> indicators) {
        when(support.conditionContext(any())).thenReturn(ConditionEvaluationContext.builder()
                .latestIndicators(indicators)
                .previousIndicators(Map.of())
                .structures(Map.of())
                .build());
    }

    private Map<String, IndicatorValue> indicatorPresent() {
        EmaValue value = new EmaValue();
        value.setEma(BigDecimal.ONE);
        return Map.of(INDICATOR_KEY, value);
    }

    private DealContext context(StrategyStep step, DealTranche tranche) {
        return context(List.of(step), tranche);
    }

    private DealContext context(List<StrategyStep> steps, DealTranche tranche) {
        Deal deal = new Deal();
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setTranches(List.of(tranche));
        StrategyDetail detail = new StrategyDetail();
        detail.setStepsByStatus(Map.of(DealTranche.Status.MANAGING, steps));
        when(support.stepsFor(any(), any())).thenReturn(steps);
        return DealContext.builder()
                .deal(deal)
                .strategyDetail(detail)
                .build();
    }

    /** Шаг сопровождения, чьё условие читает индикатор по ключу настройки. */
    private StrategyStep step(MarketDataExpiredAction protectedAction, MarketDataExpiredAction unprotectedAction) {
        StrategyStep step = new StrategyStep();
        step.setStepType(StrategyStepType.PROTECTION_ADJUSTMENT);
        step.setMarketDataExpiredSetting(
                new StrategyMarketDataExpiredSetting(protectedAction, unprotectedAction));
        StrategyConditionOperand indicator = new StrategyConditionOperand();
        indicator.setSourceType(StrategyConditionSourceType.INDICATOR);
        indicator.setIndicatorKey(INDICATOR_KEY);
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setLevel(1);
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setOperator(StrategyConditionOperator.GT);
        rule.setLeftOperand(indicator);
        step.setCondition(new StrategyCondition(List.of(rule)));
        return step;
    }

    /** Транш с живой экспозицией и без защиты: риск есть, покрытия нет. */
    private DealTranche bareTranche() {
        return tranche();
    }

    /** Транш с живой экспозицией, покрытой стоп-защитой на весь объём. */
    private DealTranche coveredTranche() {
        AlgoOrder protection = new AlgoOrder();
        protection.setStatus(AlgoOrder.Status.ACTIVE);
        protection.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        protection.setSize(BigDecimal.ONE);
        return tranche(protection);
    }

    private DealTranche tranche(AlgoOrder... protections) {
        DealTranche tranche = new DealTranche();
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEpisodeSeq(1);
        tranche.setEntryFilled(BigDecimal.ONE);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of(protections));
        return tranche;
    }
}
