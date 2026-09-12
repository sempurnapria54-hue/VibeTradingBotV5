package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * База параметров индикатора. Собственного поля-типа не несёт: подтип
 * резолвится по {@code indicatorType} настройки-владельца
 * ({@link StrategyIndicatorSetting}) вручную в StrategyJsonConverter
 * (сериализация — конкретным подтипом без тега, десериализация — в
 * конкретный класс по indicator_type), поэтому тег в JSON-payload params
 * не дублируется (docs/rules/persistence-representation.md). Наследники
 * несут только математические параметры по типу индикатора. Хранение —
 * JSONB внутри JSON настройки-владельца. См.
 * docs/models/domain/aggregate/Strategy.md (§IndicatorParams),
 * docs/rules/strategy-condition-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class IndicatorParams {

    /** Доменный таймфрейм серии, по которой считается индикатор. */
    private TimeFrame timeframe;

    /**
     * Явный override warmup-глубины (бары прогрева). По умолчанию null —
     * warmup выводится реализацией индикатора из типа и периода;
     * эффективный warmup = override ?? derived. Потребитель —
     * candle-loading (глубина истории), runtime-пропуск разгонной зоны —
     * IndicatorJob.
     */
    private Integer warmup;
}
