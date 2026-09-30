package com.example.marketdata.domain.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Заказанная идентичность вычисления индикатора: тип, таймфрейм и
 * канонические параметры.
 *
 * <p><b>Это не настройка стратегии, а вопрос к рынку.</b> Строка отвечает
 * на «ATR(14) на 1H» и принадлежит market-data; кто её заказал — здесь не
 * записано и записываться не должно: одно и то же вычисление шарится
 * между всеми, кому оно нужно, а фичам по всему листингу заказчика нет
 * вовсе (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»).
 *
 * <p><b>Срока свежести на ней тоже нет:</b> толерантность принадлежит
 * читателю и приезжает операндом чтения
 * (docs/rules/market-data-freshness.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class IndicatorConfig {

    /** Внутренний идентификатор идентичности. */
    private Long id;

    /** Межсервисный идентификатор: им идентичность называют потребители. */
    private String internalId;

    /** Тип индикатора. */
    private IndicatorValue.Type indicatorType;

    /** Таймфрейм серии, по которой считается индикатор. */
    private TimeFrame timeframe;

    /** Параметры расчёта по типу индикатора. */
    private IndicatorParams params;

    /**
     * Дефекты параметров, на которых вычисление не определено: период
     * отсутствует либо неположителен; у MACD сверх того быстрый период не
     * меньше медленного; у полос Боллинджера множитель отклонения пуст либо
     * отрицателен; у любого типа объявленный прогрев отрицателен. Пусто —
     * параметры годны.
     *
     * <p><b>Идентичность с дефектом не заводится вовсе.</b> Дошедший до
     * вычислителя, такой параметр дал бы отказ расчёта, а его гасит джоба:
     * заказанная идентичность молча не считалась бы никогда
     * (docs/architecture/market-data-collection.md §«Как потребность доходит
     * до сбора»).
     *
     * <p><b>Пустой прогрев и нулевой множитель дефектами не считаются.</b>
     * Пустой прогрев — объявленное «выводится реализацией», нулевой —
     * «разгонной зоны нет»; нулевой множитель схлопывает полосы в середину, и
     * исход у него определён. Отрицательный прогрев уводит индекс ряда за его
     * начало, отрицательный множитель ставит верхнюю полосу ниже нижней.
     */
    public List<String> parameterDefects() {
        List<String> defects = new ArrayList<>();
        if (isNull(params)) {
            defects.add("params are required");
            return defects;
        }
        if (nonNull(params.getWarmup()) && params.getWarmup() < 0) {
            defects.add("warmup must not be negative");
        }
        switch (params) {
            case AtrParams atr -> requirePositive(defects, "period", atr.getPeriod());
            case EmaParams ema -> requirePositive(defects, "period", ema.getPeriod());
            case RsiParams rsi -> requirePositive(defects, "period", rsi.getPeriod());
            case BollingerBandsParams bands -> collectBollingerDefects(defects, bands);
            case EfficiencyRatioParams efficiency -> requirePositive(defects, "period", efficiency.getPeriod());
            case StochasticParams stochastic -> collectStochasticDefects(defects, stochastic);
            case MacdParams macd -> collectMacdDefects(defects, macd);
            default -> {
                // Параметров-периодов у типа нет (OBV): проверять нечего.
            }
        }
        return defects;
    }

    private static void collectBollingerDefects(List<String> defects, BollingerBandsParams bands) {
        requirePositive(defects, "period", bands.getPeriod());
        if (isNull(bands.getDeviationMultiplier()) || bands.getDeviationMultiplier().signum() < 0) {
            defects.add("deviationMultiplier must be present and not negative");
        }
    }

    private static void collectStochasticDefects(List<String> defects, StochasticParams stochastic) {
        requirePositive(defects, "kPeriod", stochastic.getkPeriod());
        requirePositive(defects, "dPeriod", stochastic.getdPeriod());
        requirePositive(defects, "smoothPeriod", stochastic.getSmoothPeriod());
    }

    /** Быстрая линия, не меньшая медленной, вырождает MACD: при равенстве линия тождественно нулевая. */
    private static void collectMacdDefects(List<String> defects, MacdParams macd) {
        requirePositive(defects, "fastPeriod", macd.getFastPeriod());
        requirePositive(defects, "slowPeriod", macd.getSlowPeriod());
        requirePositive(defects, "signalPeriod", macd.getSignalPeriod());
        if (isPositive(macd.getFastPeriod()) && isPositive(macd.getSlowPeriod())
                && macd.getFastPeriod() >= macd.getSlowPeriod()) {
            defects.add("fastPeriod must be less than slowPeriod");
        }
    }

    private static void requirePositive(List<String> defects, String name, Integer period) {
        if (isNull(period) || period <= 0) {
            defects.add(name + " must be present and positive");
        }
    }

    private static boolean isPositive(Integer period) {
        return nonNull(period) && period > 0;
    }
}
