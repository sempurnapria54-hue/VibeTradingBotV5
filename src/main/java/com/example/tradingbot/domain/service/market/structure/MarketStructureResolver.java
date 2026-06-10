package com.example.tradingbot.domain.service.market.structure;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Вычисляет структуру рынка из закрытых свечей окна: свинг-пивоты,
 * подтверждённые уровни, тип (RANGE/UPTREND/DOWNTREND/UNKNOWN) и
 * предвычисленное событие пробоя. Численные пороги — из
 * MarketStructureParams (хвост пользователя); точная арифметика
 * (толеранс кластеризации, ER-порог тренда, буфер/окно пробоя) — деталь
 * реализации (CODE) с торговой сверкой в ревью. Готовый ER/ATR
 * потребляются как опциональный вход; при их отсутствии — минимальный
 * внутренний прокси (нетто-ход / суммарный ход окна). Сам не персистит,
 * instrumentId/strategyMarketStructureSettingId проставляет MarketStructureJob. См.
 * docs/components/MarketStructureResolver.md,
 * docs/models/domain/other/MarketStructure.md (§Семантика классификации).
 */
@Component
public class MarketStructureResolver {

    /** Провизорный дефолт порога ER (тренд vs диапазон), если не задан в params (D2; калибровка — фаза 2). */
    private static final BigDecimal DEFAULT_TREND_EFFICIENCY_THRESHOLD = BigDecimal.valueOf(30L, 2);

    /** Провизорный дефолт множителя k толеранса = k·ATR, если не задан в params (D3; калибровка — фаза 2). */
    private static final BigDecimal DEFAULT_LEVEL_TOLERANCE_ATR_MULTIPLIER = BigDecimal.valueOf(5L, 1);

    /** Fallback-толеранс кластеризации, доля цены — когда ATR-вход не объявлен (нет каталожного ATR). */
    private static final BigDecimal LEVEL_TOLERANCE_FRACTION_FALLBACK = BigDecimal.valueOf(5L, 3);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);

    /**
     * Вычисляет структуру окна; instrumentId/strategyMarketStructureSettingId не заполняет (это
     * job). Готовые каталожные ER (тренд/шум, fork-A) и ATR (толеранс
     * уровней, D3) потребляются как входы; при отсутствии ER — внутренний
     * прокси, при отсутствии ATR — fallback-толеранс долей цены. Неполные/
     * пустые params или окно → консервативный UNKNOWN (без NPE и спама).
     */
    public MarketStructure resolve(List<Candle> windowCandles, BigDecimal efficiencyRatio, BigDecimal atr,
                                   MarketStructureParams params) {
        MarketStructure structure = new MarketStructure();
        structure.setLevels(new ArrayList<>());
        if (isEmpty(windowCandles)) {
            structure.setType(MarketStructure.Type.UNKNOWN);
            return structure;
        }
        OffsetDateTime windowEndAt = timestampOf(windowCandles.get(windowCandles.size() - 1));
        structure.setWindowStartAt(timestampOf(windowCandles.get(0)));
        structure.setWindowEndAt(windowEndAt);
        structure.setConfirmedAt(windowEndAt);
        if (isFalse(hasRequiredParams(params))) {
            structure.setType(MarketStructure.Type.UNKNOWN);
            return structure;
        }

        List<MarketPriceLevel> swingHighs = swings(windowCandles, params.getSwingLookbackBars(), true);
        List<MarketPriceLevel> swingLows = swings(windowCandles, params.getSwingLookbackBars(), false);
        BigDecimal resistance = highestPrice(swingHighs);
        BigDecimal support = lowestPrice(swingLows);
        BigDecimal efficiency = nonNull(efficiencyRatio) ? efficiencyRatio : proxyEfficiency(windowCandles);

        List<MarketPriceLevel> levels = new ArrayList<>();
        levels.addAll(swingHighs);
        levels.addAll(swingLows);

        MarketStructure.Type type = classify(swingHighs, swingLows, resistance, support, efficiency, atr, params);
        decorateBoundaryLevels(type, levels, resistance, support, windowEndAt);
        structure.setType(type);
        structure.setLevels(levels);
        structure.setBreakoutEvent(detectBreakout(windowCandles, resistance, support, params, windowEndAt));
        return structure;
    }

    /** Все численные пороги структуры заданы (иначе расчёт не определён → UNKNOWN). */
    private boolean hasRequiredParams(MarketStructureParams params) {
        return nonNull(params)
                && nonNull(params.getSwingLookbackBars())
                && nonNull(params.getMinTouches())
                && nonNull(params.getMinRangeWidthPercents())
                && nonNull(params.getMaxRangeWidthPercents())
                && nonNull(params.getBreakoutBufferPercents())
                && nonNull(params.getBreakoutConfirmationBars());
    }

    private MarketStructure.Type classify(List<MarketPriceLevel> swingHighs, List<MarketPriceLevel> swingLows,
                                          BigDecimal resistance, BigDecimal support, BigDecimal efficiency,
                                          BigDecimal atr, MarketStructureParams params) {
        if (swingHighs.size() < 2 || swingLows.size() < 2 || isNull(resistance) || isNull(support)) {
            return MarketStructure.Type.UNKNOWN;
        }
        boolean higherHighs = isHigher(swingHighs);
        boolean higherLows = isHigher(swingLows);
        boolean lowerHighs = isLower(swingHighs);
        boolean lowerLows = isLower(swingLows);
        boolean trendStrength = efficiency.compareTo(trendEfficiencyThreshold(params)) >= 0;
        if (higherHighs && higherLows && trendStrength) {
            return MarketStructure.Type.UPTREND;
        }
        if (lowerHighs && lowerLows && trendStrength) {
            return MarketStructure.Type.DOWNTREND;
        }
        if (isRange(swingHighs, swingLows, resistance, support, atr, params)) {
            return MarketStructure.Type.RANGE;
        }
        return MarketStructure.Type.UNKNOWN;
    }

    private BigDecimal trendEfficiencyThreshold(MarketStructureParams params) {
        return nonNull(params.getTrendEfficiencyThreshold())
                ? params.getTrendEfficiencyThreshold() : DEFAULT_TREND_EFFICIENCY_THRESHOLD;
    }

    private boolean isRange(List<MarketPriceLevel> swingHighs, List<MarketPriceLevel> swingLows,
                            BigDecimal resistance, BigDecimal support, BigDecimal atr, MarketStructureParams params) {
        BigDecimal mid = resistance.add(support).divide(BigDecimal.valueOf(2L), Constants.Calc.MATH_CONTEXT);
        if (mid.signum() == 0) {
            return false;
        }
        BigDecimal widthPercent = resistance.subtract(support)
                .multiply(HUNDRED).divide(mid, Constants.Calc.MATH_CONTEXT);
        boolean withinWidth = widthPercent.compareTo(params.getMinRangeWidthPercents()) >= 0
                && widthPercent.compareTo(params.getMaxRangeWidthPercents()) <= 0;
        int touchesHigh = touchesNear(swingHighs, resistance, atr, params);
        int touchesLow = touchesNear(swingLows, support, atr, params);
        boolean enoughTouches = touchesHigh >= params.getMinTouches() && touchesLow >= params.getMinTouches();
        return withinWidth && enoughTouches;
    }

    private void decorateBoundaryLevels(MarketStructure.Type type, List<MarketPriceLevel> levels,
                                        BigDecimal resistance, BigDecimal support, OffsetDateTime windowEndAt) {
        if (Objects.equals(type, MarketStructure.Type.RANGE)) {
            levels.add(boundaryLevel(MarketPriceLevel.Type.RANGE_HIGH, resistance, windowEndAt));
            levels.add(boundaryLevel(MarketPriceLevel.Type.RANGE_LOW, support, windowEndAt));
        } else if (Objects.equals(type, MarketStructure.Type.UPTREND)
                || Objects.equals(type, MarketStructure.Type.DOWNTREND)) {
            levels.add(boundaryLevel(MarketPriceLevel.Type.RESISTANCE, resistance, windowEndAt));
            levels.add(boundaryLevel(MarketPriceLevel.Type.SUPPORT, support, windowEndAt));
        }
    }

    private MarketBreakoutEvent detectBreakout(List<Candle> candles, BigDecimal resistance, BigDecimal support,
                                               MarketStructureParams params, OffsetDateTime windowEndAt) {
        int confirmationBars = params.getBreakoutConfirmationBars();
        if (isNull(resistance) || isNull(support) || candles.size() < confirmationBars || confirmationBars < 1) {
            return null;
        }
        BigDecimal buffer = params.getBreakoutBufferPercents().divide(HUNDRED, Constants.Calc.MATH_CONTEXT);
        BigDecimal upThreshold = resistance.add(resistance.multiply(buffer));
        BigDecimal downThreshold = support.subtract(support.multiply(buffer));
        List<Candle> tail = candles.subList(candles.size() - confirmationBars, candles.size());
        if (tail.stream().allMatch(candle -> candle.getClose().compareTo(upThreshold) > 0)) {
            return breakoutEvent(MarketPriceLevel.Type.RESISTANCE, MarketBreakoutEvent.Direction.UP,
                    resistance, windowEndAt);
        }
        if (tail.stream().allMatch(candle -> candle.getClose().compareTo(downThreshold) < 0)) {
            return breakoutEvent(MarketPriceLevel.Type.SUPPORT, MarketBreakoutEvent.Direction.DOWN,
                    support, windowEndAt);
        }
        return null;
    }

    private List<MarketPriceLevel> swings(List<Candle> candles, int lookback, boolean high) {
        List<MarketPriceLevel> result = new ArrayList<>();
        for (int index = lookback; index < candles.size() - lookback; index++) {
            BigDecimal pivot = high ? candles.get(index).getHigh() : candles.get(index).getLow();
            if (isLocalExtreme(candles, index, lookback, high, pivot)) {
                MarketPriceLevel level = new MarketPriceLevel();
                level.setType(high ? MarketPriceLevel.Type.SWING_HIGH : MarketPriceLevel.Type.SWING_LOW);
                level.setPrice(pivot);
                level.setDetectedAt(timestampOf(candles.get(index)));
                level.setConfirmedAt(timestampOf(candles.get(index + lookback)));
                result.add(level);
            }
        }
        return result;
    }

    private boolean isLocalExtreme(List<Candle> candles, int index, int lookback, boolean high, BigDecimal pivot) {
        for (int offset = index - lookback; offset <= index + lookback; offset++) {
            if (offset == index) {
                continue;
            }
            BigDecimal neighbour = high ? candles.get(offset).getHigh() : candles.get(offset).getLow();
            boolean exceedsPivot = high ? neighbour.compareTo(pivot) > 0 : neighbour.compareTo(pivot) < 0;
            if (exceedsPivot) {
                return false;
            }
        }
        return true;
    }

    private boolean isHigher(List<MarketPriceLevel> levels) {
        BigDecimal last = levels.get(levels.size() - 1).getPrice();
        BigDecimal previous = levels.get(levels.size() - 2).getPrice();
        return last.compareTo(previous) > 0;
    }

    private boolean isLower(List<MarketPriceLevel> levels) {
        BigDecimal last = levels.get(levels.size() - 1).getPrice();
        BigDecimal previous = levels.get(levels.size() - 2).getPrice();
        return last.compareTo(previous) < 0;
    }

    private int touchesNear(List<MarketPriceLevel> levels, BigDecimal target, BigDecimal atr,
                            MarketStructureParams params) {
        BigDecimal tolerance = clusterTolerance(target, atr, params);
        int touches = 0;
        for (MarketPriceLevel level : levels) {
            if (level.getPrice().subtract(target).abs().compareTo(tolerance) <= 0) {
                touches++;
            }
        }
        return touches;
    }

    /** Толеранс кластеризации: k·ATR при готовом каталожном ATR (D3), иначе доля цены (fallback). */
    private BigDecimal clusterTolerance(BigDecimal target, BigDecimal atr, MarketStructureParams params) {
        if (nonNull(atr)) {
            BigDecimal multiplier = nonNull(params.getLevelToleranceAtrMultiplier())
                    ? params.getLevelToleranceAtrMultiplier() : DEFAULT_LEVEL_TOLERANCE_ATR_MULTIPLIER;
            return atr.multiply(multiplier).abs();
        }
        return target.multiply(LEVEL_TOLERANCE_FRACTION_FALLBACK).abs();
    }

    private BigDecimal highestPrice(List<MarketPriceLevel> levels) {
        return levels.stream().map(MarketPriceLevel::getPrice).max(BigDecimal::compareTo).orElse(null);
    }

    private BigDecimal lowestPrice(List<MarketPriceLevel> levels) {
        return levels.stream().map(MarketPriceLevel::getPrice).min(BigDecimal::compareTo).orElse(null);
    }

    private BigDecimal proxyEfficiency(List<Candle> candles) {
        BigDecimal netMove = candles.get(candles.size() - 1).getClose()
                .subtract(candles.get(0).getClose()).abs();
        BigDecimal totalMove = BigDecimal.ZERO;
        for (int index = 1; index < candles.size(); index++) {
            totalMove = totalMove.add(candles.get(index).getClose()
                    .subtract(candles.get(index - 1).getClose()).abs());
        }
        return totalMove.signum() == 0 ? BigDecimal.ZERO
                : netMove.divide(totalMove, Constants.Calc.MATH_CONTEXT);
    }

    private MarketPriceLevel boundaryLevel(MarketPriceLevel.Type type, BigDecimal price, OffsetDateTime confirmedAt) {
        MarketPriceLevel level = new MarketPriceLevel();
        level.setType(type);
        level.setPrice(price);
        level.setDetectedAt(confirmedAt);
        level.setConfirmedAt(confirmedAt);
        return level;
    }

    private MarketBreakoutEvent breakoutEvent(MarketPriceLevel.Type brokenLevelType,
                                              MarketBreakoutEvent.Direction direction, BigDecimal levelPrice,
                                              OffsetDateTime confirmedAt) {
        MarketBreakoutEvent event = new MarketBreakoutEvent();
        event.setBrokenLevelType(brokenLevelType);
        event.setDirection(direction);
        event.setLevelPrice(levelPrice);
        event.setConfirmedAt(confirmedAt);
        return event;
    }

    private OffsetDateTime timestampOf(Candle candle) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC);
    }
}
