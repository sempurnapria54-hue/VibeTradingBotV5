package com.example.tradingbot.domain.command.calc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Рассчитывает цены действия (entry/limit, SL/TP/trailing) и возвращает
 * {@link CalculatedPrice} (docs/components/PriceCalculator.md). Размер не
 * считает, риск не проверяет; ATR/структуру по свечам не считает — берёт
 * готовые из контекста. Округление по tick size, политика CONSERVATIVE
 * (защитная цена не ухудшается). Нет актуальной цены / entry / ATR /
 * structure / tickSize, либо цена невалидна после округления → controlled
 * {@link CalculationException}.
 */
@Component
public class PriceCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String NO_REFERENCE_PRICE = "NO_REFERENCE_PRICE";
    private static final String MISSING_TICK_SIZE = "MISSING_TICK_SIZE";
    private static final String MISSING_ATR = "MISSING_ATR";
    private static final String MISSING_STRUCTURE = "MISSING_STRUCTURE";
    private static final String MISSING_STOP_LOSS_SETTINGS = "MISSING_STOP_LOSS_SETTINGS";
    private static final String MISSING_DISTANCE = "MISSING_DISTANCE";
    private static final String INVALID_PRICE_AFTER_ROUNDING = "INVALID_PRICE_AFTER_ROUNDING";

    public CalculatedPrice calculate(CalculationContext context) {
        return switch (context.getAction()) {
            case StrategyOrderAction orderAction -> orderPrice(orderAction, context);
            case StrategyAlgoOrderAction algoAction -> algoPrice(algoAction, context);
            default -> CalculatedPrice.builder()
                    .priceMode(PriceMode.NOT_REQUIRED)
                    .sendPriceToExchange(Boolean.FALSE)
                    .description("price not required for action")
                    .build();
        };
    }

    private CalculatedPrice orderPrice(StrategyOrderAction action, CalculationContext context) {
        StrategyTradeDirection direction = context.getStrategyDirection();
        StrategyPricePlacement placement = action.getPlacement();
        CalculatedPrice.CalculatedPriceBuilder builder = CalculatedPrice.builder();
        BigDecimal entryPrice;
        if (isNull(placement)) {
            entryPrice = marketReference(context);
            builder.purpose(StrategyPricePurpose.ORDER_MARKET_REFERENCE_PRICE)
                    .priceMode(PriceMode.MARKET_LIKE)
                    .basePrice(entryPrice)
                    .rawPrice(entryPrice)
                    .roundedPrice(entryPrice)
                    .sendPriceToExchange(Boolean.FALSE)
                    .description("market-like entry; reference last price");
        } else {
            BigDecimal base = resolveBasePrice(placement, context);
            BigDecimal offset = percentOf(base, placement.getPercents());
            BigDecimal raw = StrategyPriceOffsetSide.ABOVE.equals(placement.getOffsetSide())
                    ? base.add(offset)
                    : base.subtract(offset);
            RoundingMode mode = StrategyTradeDirection.LONG.equals(direction) ? RoundingMode.DOWN : RoundingMode.UP;
            entryPrice = requirePositive(roundToTick(raw, context, mode));
            builder.purpose(StrategyPricePurpose.ORDER_LIMIT_PRICE)
                    .priceMode(PriceMode.EXPLICIT)
                    .basePrice(base)
                    .rawPrice(raw)
                    .roundedPrice(entryPrice)
                    .sendPriceToExchange(Boolean.TRUE)
                    .description("limit entry");
        }
        if (Order.Type.ENTRY_ATTACHED_STOP_LOSS.equals(action.getOrderType()) && nonNull(action.getAttachedProtection())) {
            builder.stopLossPrice(resolveStopLoss(
                    action.getAttachedProtection().getStopLossSettings(), entryPrice, direction, context));
        }
        return builder.build();
    }

    private CalculatedPrice algoPrice(StrategyAlgoOrderAction action, CalculationContext context) {
        StrategyTradeDirection direction = context.getStrategyDirection();
        BigDecimal entryPrice = entryReference(context);
        CalculatedPrice.CalculatedPriceBuilder builder = CalculatedPrice.builder()
                .priceMode(PriceMode.NOT_REQUIRED)
                .sendPriceToExchange(Boolean.FALSE)
                .description("algo price for " + action.getConditionType());
        switch (action.getConditionType()) {
            case STOP_LOSS, PARTIAL_STOP_LOSS -> builder
                    .purpose(StrategyPricePurpose.STOP_LOSS_TRIGGER_PRICE)
                    .stopLossPrice(resolveStopLoss(action.getStopLossSettings(), entryPrice, direction, context));
            case TAKE_PROFIT, PARTIAL_TAKE_PROFIT -> builder
                    .purpose(StrategyPricePurpose.TAKE_PROFIT_TRIGGER_PRICE)
                    .takeProfitPrice(resolveTakeProfit(action, entryPrice, direction, context));
            case OCO_FULL -> builder
                    .purpose(StrategyPricePurpose.STOP_LOSS_TRIGGER_PRICE)
                    .stopLossPrice(resolveStopLoss(action.getStopLossSettings(), entryPrice, direction, context))
                    .takeProfitPrice(resolveTakeProfit(action, entryPrice, direction, context));
            case TRAILING_PERCENTS, TRAILING_VALUE -> builder
                    .purpose(StrategyPricePurpose.TRAILING_ACTIVATION_PRICE)
                    .trailingPrice(resolveTrailing(action.getTrailingSettings(), entryPrice, direction, context));
        }
        return builder.build();
    }

    private ResolvedStopLossPrice resolveStopLoss(StopLossSettings settings, BigDecimal entryPrice,
                                                  StrategyTradeDirection direction, CalculationContext context) {
        if (isNull(settings)) {
            throw error(MISSING_STOP_LOSS_SETTINGS, "Stop-loss settings missing for action");
        }
        boolean isLong = StrategyTradeDirection.LONG.equals(direction);
        BigDecimal trigger = switch (settings.getCalculationType()) {
            case ENTRY_PRICE_PERCENT -> applyDistance(entryPrice, percentOf(entryPrice,
                    requireDistance(settings.getDistancePercents())), isLong, false);
            case ATR_PERCENT -> applyDistance(entryPrice, percentOf(atrValue(settings, context),
                    requireDistance(settings.getDistancePercents())), isLong, false);
            case MARKET_STRUCTURE_BUFFER_PERCENT -> structureStop(settings, isLong, context);
        };
        RoundingMode mode = isLong ? RoundingMode.DOWN : RoundingMode.UP;
        return ResolvedStopLossPrice.builder()
                .triggerPrice(requirePositive(roundToTick(trigger, context, mode)))
                .triggerPriceType(settings.getTriggerPriceType())
                .build();
    }

    private ResolvedTakeProfitPrice resolveTakeProfit(StrategyAlgoOrderAction action, BigDecimal entryPrice,
                                                      StrategyTradeDirection direction, CalculationContext context) {
        boolean isLong = StrategyTradeDirection.LONG.equals(direction);
        BigDecimal distance = percentOf(entryPrice, requireDistance(action.getTriggerProfitPercents()));
        BigDecimal trigger = applyDistance(entryPrice, distance, isLong, true);
        RoundingMode mode = isLong ? RoundingMode.UP : RoundingMode.DOWN;
        return ResolvedTakeProfitPrice.builder()
                .triggerPrice(requirePositive(roundToTick(trigger, context, mode)))
                .triggerPriceType(action.getTriggerPriceType())
                .build();
    }

    private ResolvedTrailingPrice resolveTrailing(TrailingSettings settings, BigDecimal entryPrice,
                                                  StrategyTradeDirection direction, CalculationContext context) {
        if (isNull(settings)) {
            throw error(MISSING_DISTANCE, "Trailing settings missing for action");
        }
        boolean isLong = StrategyTradeDirection.LONG.equals(direction);
        BigDecimal activationPrice = null;
        if (nonNull(settings.getActivationProfitPercents())) {
            BigDecimal activationPercent = settings.getActivationProfitPercents();
            if (nonNull(settings.getActivationBufferPercents())) {
                activationPercent = activationPercent.add(settings.getActivationBufferPercents());
            }
            BigDecimal distance = percentOf(entryPrice, activationPercent);
            RoundingMode mode = isLong ? RoundingMode.UP : RoundingMode.DOWN;
            activationPrice = requirePositive(roundToTick(applyDistance(entryPrice, distance, isLong, true), context, mode));
        }
        return ResolvedTrailingPrice.builder()
                .activationPrice(activationPrice)
                .callbackRatio(settings.getCallbackPercents())
                .build();
    }

    private BigDecimal structureStop(StopLossSettings settings, boolean isLong, CalculationContext context) {
        MarketStructure structure = context.findMarketStructureByKey(settings.getStructureKey());
        if (isNull(structure)) {
            throw error(MISSING_STRUCTURE, "Market structure not found for key " + settings.getStructureKey());
        }
        BigDecimal base = isLong
                ? structureLevel(structure, MarketPriceLevel.Type.SWING_LOW, MarketPriceLevel.Type.RANGE_LOW)
                : structureLevel(structure, MarketPriceLevel.Type.SWING_HIGH, MarketPriceLevel.Type.RANGE_HIGH);
        BigDecimal distance = percentOf(base, requireDistance(settings.getDistancePercents()));
        return applyDistance(base, distance, isLong, false);
    }

    private BigDecimal structureLevel(MarketStructure structure, MarketPriceLevel.Type primary,
                                      MarketPriceLevel.Type fallback) {
        MarketPriceLevel level = structure.findLevel(primary);
        if (isNull(level)) {
            level = structure.findLevel(fallback);
        }
        if (isNull(level) || isNull(level.getPrice())) {
            throw error(MISSING_STRUCTURE, "Structure level not found: " + primary + "/" + fallback);
        }
        return level.getPrice();
    }

    private BigDecimal atrValue(StopLossSettings settings, CalculationContext context) {
        IndicatorValue value = context.findIndicatorValueByKey(settings.getIndicatorKey());
        if (value instanceof AtrValue atr && nonNull(atr.getAtr())) {
            return atr.getAtr();
        }
        throw error(MISSING_ATR, "ATR not available for key " + settings.getIndicatorKey());
    }

    /** Применяет дистанцию к базе: protective (SL) — против профита, target (TP) — по профиту. */
    private BigDecimal applyDistance(BigDecimal base, BigDecimal distance, boolean isLong, boolean towardsProfit) {
        boolean shouldAdd = isLong == towardsProfit;
        return shouldAdd ? base.add(distance) : base.subtract(distance);
    }

    private BigDecimal marketReference(CalculationContext context) {
        MarketPriceData priceData = context.getMarketPriceData();
        if (nonNull(priceData) && nonNull(priceData.getExternalLastPrice())) {
            return priceData.getExternalLastPrice();
        }
        throw error(NO_REFERENCE_PRICE, "No market reference price available");
    }

    private BigDecimal entryReference(CalculationContext context) {
        Position position = context.getActivePosition();
        if (nonNull(position) && nonNull(position.getExternalAverageEntryPrice())) {
            return position.getExternalAverageEntryPrice();
        }
        Order entryOrder = context.getEntryOrder();
        if (nonNull(entryOrder) && nonNull(entryOrder.getAveragePrice())) {
            return entryOrder.getAveragePrice();
        }
        if (nonNull(entryOrder) && nonNull(entryOrder.getPrice())) {
            return entryOrder.getPrice();
        }
        return marketReference(context);
    }

    private BigDecimal resolveBasePrice(StrategyPricePlacement placement, CalculationContext context) {
        StrategyPriceBaseType baseType = placement.getBaseType();
        if (StrategyPriceBaseType.ENTRY_PRICE.equals(baseType)) {
            return entryReference(context);
        }
        if (StrategyPriceBaseType.MARKET_PRICE.equals(baseType)) {
            return marketPriceBySource(placement, context);
        }
        MarketStructure structure = context.findMarketStructureByKey(placement.getStructureKey());
        if (isNull(structure)) {
            throw error(MISSING_STRUCTURE, "Market structure not found for key " + placement.getStructureKey());
        }
        return structureLevel(structure, toLevelType(baseType), toLevelType(baseType));
    }

    private BigDecimal marketPriceBySource(StrategyPricePlacement placement, CalculationContext context) {
        MarketPriceData priceData = context.getMarketPriceData();
        BigDecimal resolved = switch (placement.getPriceSource()) {
            case BEST_BID_PRICE -> priceData.getExternalBidPrice();
            case BEST_ASK_PRICE -> priceData.getExternalAskPrice();
            case MID_PRICE -> priceData.midPrice();
            // OKX ticker не несёт mark/index — на первом этапе проксируем last.
            case LAST_PRICE, MARK_PRICE, INDEX_PRICE -> priceData.getExternalLastPrice();
        };
        if (isNull(resolved)) {
            throw error(NO_REFERENCE_PRICE, "Market price source unavailable: " + placement.getPriceSource());
        }
        return resolved;
    }

    private MarketPriceLevel.Type toLevelType(StrategyPriceBaseType baseType) {
        return switch (baseType) {
            case RANGE_LOW -> MarketPriceLevel.Type.RANGE_LOW;
            case RANGE_HIGH -> MarketPriceLevel.Type.RANGE_HIGH;
            case SWING_LOW -> MarketPriceLevel.Type.SWING_LOW;
            case SWING_HIGH -> MarketPriceLevel.Type.SWING_HIGH;
            case SUPPORT -> MarketPriceLevel.Type.SUPPORT;
            case RESISTANCE -> MarketPriceLevel.Type.RESISTANCE;
            case ENTRY_PRICE, MARKET_PRICE -> throw error(MISSING_STRUCTURE,
                    "Base type is not a structure level: " + baseType);
        };
    }

    private BigDecimal percentOf(BigDecimal base, BigDecimal percents) {
        if (isNull(percents)) {
            return BigDecimal.ZERO;
        }
        return base.multiply(percents).divide(HUNDRED, Constants.Calc.MATH_CONTEXT);
    }

    private BigDecimal requireDistance(BigDecimal distancePercents) {
        if (isNull(distancePercents)) {
            throw error(MISSING_DISTANCE, "Distance percents missing");
        }
        return distancePercents;
    }

    private BigDecimal roundToTick(BigDecimal price, CalculationContext context, RoundingMode mode) {
        BigDecimal tick = tickSize(context);
        BigDecimal units = price.divide(tick, 0, mode);
        return units.multiply(tick);
    }

    private BigDecimal tickSize(CalculationContext context) {
        InstrumentExternalRules rules = context.getInstrumentExternalRules();
        BigDecimal tick = isNull(rules) ? null : rules.tickSize();
        if (isNull(tick) || tick.signum() <= 0) {
            throw error(MISSING_TICK_SIZE, "Instrument tick size missing");
        }
        return tick;
    }

    private BigDecimal requirePositive(BigDecimal price) {
        if (isNull(price) || price.signum() <= 0) {
            throw error(INVALID_PRICE_AFTER_ROUNDING, "Price invalid after rounding: " + price);
        }
        return price;
    }

    private CalculationException error(String code, String message) {
        return new CalculationException(CalculationError.permanent(code, message));
    }
}
