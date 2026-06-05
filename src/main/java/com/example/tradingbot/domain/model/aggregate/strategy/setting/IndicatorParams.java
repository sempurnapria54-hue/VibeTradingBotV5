package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * База параметров индикатора. Собственного поля-типа не несёт:
 * дискриминатор подтипа — {@code indicatorType} настройки-владельца
 * ({@link StrategyIndicatorSetting}), маппится Jackson
 * EXTERNAL_PROPERTY и в JSON-payload params не дублируется
 * (docs/rules/persistence-representation.md). Наследники несут только
 * математические параметры по типу индикатора. Хранение — JSONB внутри
 * JSON настройки-владельца. См.
 * docs/models/domain/aggregate/Strategy.md (§IndicatorParams),
 * docs/decisions/strategy-condition-authoring-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonSubTypes({
        @JsonSubTypes.Type(value = AtrParams.class, name = "ATR"),
        @JsonSubTypes.Type(value = EmaParams.class, name = "EMA"),
        @JsonSubTypes.Type(value = RsiParams.class, name = "RSI"),
        @JsonSubTypes.Type(value = MacdParams.class, name = "MACD"),
        @JsonSubTypes.Type(value = ObvParams.class, name = "OBV"),
        @JsonSubTypes.Type(value = StochasticParams.class, name = "STOCHASTIC"),
        @JsonSubTypes.Type(value = BollingerBandsParams.class, name = "BOLLINGER_BANDS")
})
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
