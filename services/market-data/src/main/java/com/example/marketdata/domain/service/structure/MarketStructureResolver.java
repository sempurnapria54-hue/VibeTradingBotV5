package com.example.marketdata.domain.service.structure;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
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
 * instrumentId/marketStructureConfigId проставляет MarketStructureJob. См.
 * docs/components/MarketStructureResolver.md,
 * docs/models/domain/other/MarketStructure.md (§Семантика классификации).
 *
 * <p>Граничный уровень выдаётся только подтверждённым, пробой считается на
 * выданном граничном уровне по хвосту окна и ломает структуру — дом
 * docs/models/domain/other/MarketStructure.md §«Семантика классификации
 * (как считается)» и §«Валидный уровень».
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
     * Вычисляет структуру окна; instrumentId/marketStructureConfigId не заполняет (это
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
        structure.setWindowStartAt(timestampOf(windowCandles.get(0)));
        structure.setWindowEndAt(timestampOf(windowCandles.get(windowCandles.size() - 1)));
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
        structure.setLevels(levels);

        MarketStructure.Type type = classify(swingHighs, swingLows, resistance, support, efficiency, atr, params);
        MarketPriceLevel ceiling = confirmedBoundary(boundaryTypeOf(type, true), resistance, swingHighs,
                atr, params);
        MarketPriceLevel floor = confirmedBoundary(boundaryTypeOf(type, false), support, swingLows, atr, params);
        MarketBreakoutEvent breakout = detectBreakout(windowCandles, ceiling, floor, params);
        // Подтверждённый пробой ломает структуру окна: граничных уровней нет,
        // свинги остаются, а момент подтверждения несёт событие, не каркас.
        if (nonNull(breakout)) {
            structure.setType(MarketStructure.Type.UNKNOWN);
            structure.setBreakoutEvent(breakout);
            return structure;
        }
        Stream.of(ceiling, floor).filter(Objects::nonNull).forEach(levels::add);
        structure.setType(type);
        structure.setConfirmedAt(structureConfirmedAt(type, swingHighs, swingLows, ceiling, floor));
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
        BigDecimal mid = resistance.add(support).divide(BigDecimal.valueOf(2L), DomainMath.CONTEXT);
        if (mid.signum() == 0) {
            return false;
        }
        BigDecimal widthPercent = resistance.subtract(support)
                .multiply(HUNDRED).divide(mid, DomainMath.CONTEXT);
        boolean withinWidth = widthPercent.compareTo(params.getMinRangeWidthPercents()) >= 0
                && widthPercent.compareTo(params.getMaxRangeWidthPercents()) <= 0;
        int touchesHigh = touchesNear(swingHighs, resistance, atr, params);
        int touchesLow = touchesNear(swingLows, support, atr, params);
        boolean enoughTouches = touchesHigh >= params.getMinTouches() && touchesLow >= params.getMinTouches();
        return withinWidth && enoughTouches;
    }

    /**
     * Тип граничного уровня стороны у типа структуры: у диапазона — его
     * границы, у тренда — сопротивление и поддержка; у консервативного
     * исхода границ нет, и типа тоже.
     */
    private MarketPriceLevel.Type boundaryTypeOf(MarketStructure.Type type, boolean ceiling) {
        if (Objects.equals(type, MarketStructure.Type.RANGE)) {
            return ceiling ? MarketPriceLevel.Type.RANGE_HIGH : MarketPriceLevel.Type.RANGE_LOW;
        }
        if (isTrend(type)) {
            return ceiling ? MarketPriceLevel.Type.RESISTANCE : MarketPriceLevel.Type.SUPPORT;
        }
        return null;
    }

    private boolean isTrend(MarketStructure.Type type) {
        return Objects.equals(type, MarketStructure.Type.UPTREND)
                || Objects.equals(type, MarketStructure.Type.DOWNTREND);
    }

    /**
     * Момент подтверждения каркаса — момент, на котором завершилось
     * свидетельство его типа, а не конец окна (дом —
     * docs/models/domain/other/MarketStructure.md §«Семантика
     * классификации (как считается)», пункт о моменте подтверждения).
     * Диапазон подтверждён, когда подтверждены обе границы — у диапазона
     * они выданы всегда: его предикат требует тех же касаний; тренд — когда
     * известны последние свинг-максимум и свинг-минимум, на которых стои́т
     * его пара; у консервативного исхода подтверждать нечего.
     */
    private OffsetDateTime structureConfirmedAt(MarketStructure.Type type, List<MarketPriceLevel> swingHighs,
                                                List<MarketPriceLevel> swingLows, MarketPriceLevel ceiling,
                                                MarketPriceLevel floor) {
        if (Objects.equals(type, MarketStructure.Type.RANGE)) {
            return later(ceiling.getConfirmedAt(), floor.getConfirmedAt());
        }
        if (isTrend(type)) {
            return later(swingHighs.get(swingHighs.size() - 1).getConfirmedAt(),
                    swingLows.get(swingLows.size() - 1).getConfirmedAt());
        }
        return null;
    }

    /** Позднейший из двух моментов; пустой проигрывает известному. */
    private OffsetDateTime later(OffsetDateTime first, OffsetDateTime second) {
        if (isNull(first) || isNull(second)) {
            return isNull(first) ? second : first;
        }
        return first.isAfter(second) ? first : second;
    }

    /**
     * Пробит бывает выданный граничный уровень: нет его у стороны — ломать
     * там нечего. Детекция читает хвост окна — событие мерит удержание на
     * хвосте, — а момент события берётся из последнего бара удержания:
     * свидетельство называет себя само, и с концом окна момент совпадает по
     * построению хвоста, а не по присваиванию. Тип сломанного уровня — тип
     * выданного уровня.
     */
    private MarketBreakoutEvent detectBreakout(List<Candle> candles, MarketPriceLevel ceiling,
                                               MarketPriceLevel floor, MarketStructureParams params) {
        int confirmationBars = params.getBreakoutConfirmationBars();
        if ((isNull(ceiling) && isNull(floor)) || candles.size() < confirmationBars || confirmationBars < 1) {
            return null;
        }
        BigDecimal buffer = params.getBreakoutBufferPercents().divide(HUNDRED, DomainMath.CONTEXT);
        List<Candle> tail = candles.subList(candles.size() - confirmationBars, candles.size());
        OffsetDateTime lastHoldBarAt = timestampOf(tail.get(tail.size() - 1));
        if (nonNull(ceiling) && isHeldBeyond(tail, ceiling.getPrice().add(ceiling.getPrice().multiply(buffer)),
                true)) {
            return breakoutEvent(ceiling, MarketBreakoutEvent.Direction.UP, lastHoldBarAt);
        }
        if (nonNull(floor) && isHeldBeyond(tail, floor.getPrice().subtract(floor.getPrice().multiply(buffer)),
                false)) {
            return breakoutEvent(floor, MarketBreakoutEvent.Direction.DOWN, lastHoldBarAt);
        }
        return null;
    }

    /** Каждый бар хвоста закрыт за порогом: выше — у потолка, ниже — у пола. */
    private boolean isHeldBeyond(List<Candle> tail, BigDecimal threshold, boolean above) {
        return tail.stream().allMatch(candle -> above
                ? candle.getClose().compareTo(threshold) > 0
                : candle.getClose().compareTo(threshold) < 0);
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
        return touchesOf(levels, target, atr, params).size();
    }

    /** Пивоты, касающиеся цены в пределах толеранса, — в порядке времени, как их отдал поиск. */
    private List<MarketPriceLevel> touchesOf(List<MarketPriceLevel> pivots, BigDecimal target, BigDecimal atr,
                                             MarketStructureParams params) {
        BigDecimal tolerance = clusterTolerance(target, atr, params);
        List<MarketPriceLevel> touches = new ArrayList<>();
        for (MarketPriceLevel pivot : pivots) {
            if (pivot.getPrice().subtract(target).abs().compareTo(tolerance) <= 0) {
                touches.add(pivot);
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
                : netMove.divide(totalMove, DomainMath.CONTEXT);
    }

    /**
     * Граничный уровень стороны — только подтверждённый. Строится от
     * крайнего пивота стороны: его цена — цена уровня, а пивоты стороны в
     * пределах толеранса от неё — его касания. Найден — на баре самого
     * раннего касания; подтверждён — в момент касания номер
     * {@code minTouches}, а момент касания есть бар подтверждения его
     * пивота: раньше пивот не отличим от продолжения движения. Касаний
     * меньше требуемого — уровня нет вовсе, и пустого момента подтверждения
     * у выданного граничного уровня поэтому не бывает. Типа нет —
     * у исхода границ нет, и уровня тоже.
     *
     * <p>Касание есть всегда хотя бы одно: цена границы — цена крайнего
     * пивота, и его отстояние от себя нулевое.
     */
    private MarketPriceLevel confirmedBoundary(MarketPriceLevel.Type type, BigDecimal price,
                                               List<MarketPriceLevel> pivots, BigDecimal atr,
                                               MarketStructureParams params) {
        if (isNull(type)) {
            return null;
        }
        List<MarketPriceLevel> touches = touchesOf(pivots, price, atr, params);
        int requiredTouches = Math.max(params.getMinTouches(), 1);
        if (touches.size() < requiredTouches) {
            return null;
        }
        MarketPriceLevel level = new MarketPriceLevel();
        level.setType(type);
        level.setPrice(price);
        level.setDetectedAt(touches.get(0).getDetectedAt());
        level.setConfirmedAt(touches.get(requiredTouches - 1).getConfirmedAt());
        return level;
    }

    private MarketBreakoutEvent breakoutEvent(MarketPriceLevel brokenLevel, MarketBreakoutEvent.Direction direction,
                                              OffsetDateTime confirmedAt) {
        MarketBreakoutEvent event = new MarketBreakoutEvent();
        event.setBrokenLevelType(brokenLevel.getType());
        event.setDirection(direction);
        event.setLevelPrice(brokenLevel.getPrice());
        event.setConfirmedAt(confirmedAt);
        return event;
    }

    private OffsetDateTime timestampOf(Candle candle) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC);
    }
}
