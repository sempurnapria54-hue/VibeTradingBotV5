package com.example.tradingbot.domain.service.strategy.price;

import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.ConditionType;
import com.example.tradingbot.domain.model.core.algo_order.OcoFullCondition;
import com.example.tradingbot.domain.model.core.algo_order.PartialStopLossCondition;
import com.example.tradingbot.domain.model.core.algo_order.PartialTakeProfitCondition;
import com.example.tradingbot.domain.model.core.algo_order.StopLossCondition;
import com.example.tradingbot.domain.model.core.algo_order.TakeProfitCondition;
import com.example.tradingbot.domain.model.core.algo_order.TrailingPercentsCondition;
import com.example.tradingbot.domain.model.core.algo_order.TrailingValueCondition;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPriceType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.trade.strategy.StrategyOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPositionAction;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class StrategyPriceResolver {

    private static final BigDecimal PLACEHOLDER_PRICE = BigDecimal.ONE;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public ResolvedOrderPrice resolveOrderPrice(StrategyOrderAction action, DealContext context) {
        // Stub until the dedicated strategy price resolver implements ATR/range/trailing formulas.
        return new ResolvedOrderPrice(null);
    }

    public ResolvedAlgoOrderPrice resolveAlgoOrderPrice(StrategyAlgoOrderAction action, DealContext context) {
        // Stub until the dedicated strategy price resolver implements ATR/range/trailing formulas.
        return new ResolvedAlgoOrderPrice(resolveCondition(action));
    }

    public ResolvedAttachedProtectionPrice resolveAttachedProtectionPrice(StrategyAttachedProtectionSettings settings,
                                                                          DealContext context) {
        // Stub until the dedicated strategy price resolver implements attached protection formulas.
        if (Objects.isNull(settings)) {
            return new ResolvedAttachedProtectionPrice(null);
        }
        return new ResolvedAttachedProtectionPrice(PLACEHOLDER_PRICE);
    }

    public ResolvedPositionClosePrice resolvePositionClosePrice(StrategyPositionAction action, DealContext context) {
        // Stub until position close price policies are implemented.
        return new ResolvedPositionClosePrice(null);
    }

    private Condition resolveCondition(StrategyAlgoOrderAction action) {
        if (Objects.isNull(action) || Objects.isNull(action.getConditionType())) {
            return new StopLossCondition(PLACEHOLDER_PRICE, TriggerPriceType.MARK, BigDecimal.ONE);
        }

        BigDecimal closeFraction = toFraction(action.getCloseFractionPercents());
        TriggerPriceType triggerPriceType = resolveTriggerPriceType(action);
        BigDecimal trailingPercents = resolveTrailingPercents(action);

        return switch (action.getConditionType()) {
            case STOP_LOSS -> new StopLossCondition(PLACEHOLDER_PRICE, triggerPriceType, closeFraction);
            case TAKE_PROFIT -> new TakeProfitCondition(PLACEHOLDER_PRICE, triggerPriceType, closeFraction);
            case OCO_FULL -> new OcoFullCondition(PLACEHOLDER_PRICE,
                                                  triggerPriceType,
                                                  PLACEHOLDER_PRICE,
                                                  triggerPriceType,
                                                  closeFraction);
            case PARTIAL_TAKE_PROFIT ->
                    new PartialTakeProfitCondition(PLACEHOLDER_PRICE, triggerPriceType, closeFraction);
            case PARTIAL_STOP_LOSS ->
                    new PartialStopLossCondition(PLACEHOLDER_PRICE, triggerPriceType, closeFraction);
            case TRAILING_PERCENTS -> new TrailingPercentsCondition(trailingPercents, null, closeFraction);
            case TRAILING_VALUE -> new TrailingValueCondition(PLACEHOLDER_PRICE, null, closeFraction);
        };
    }

    private TriggerPriceType resolveTriggerPriceType(StrategyAlgoOrderAction action) {
        if (Objects.nonNull(action.getTriggerPriceType())) {
            return action.getTriggerPriceType();
        }
        if (Objects.nonNull(action.getStopLossSettings())
                && Objects.nonNull(action.getStopLossSettings().getTriggerPriceType())) {
            return action.getStopLossSettings().getTriggerPriceType();
        }
        return TriggerPriceType.MARK;
    }

    private BigDecimal resolveTrailingPercents(StrategyAlgoOrderAction action) {
        if (Objects.nonNull(action.getTrailingSettings())
                && Objects.nonNull(action.getTrailingSettings().getCallbackPercents())) {
            return action.getTrailingSettings().getCallbackPercents();
        }
        return BigDecimal.ONE;
    }

    private BigDecimal toFraction(BigDecimal percents) {
        if (Objects.isNull(percents)) {
            return BigDecimal.ONE;
        }
        return percents.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }
}
