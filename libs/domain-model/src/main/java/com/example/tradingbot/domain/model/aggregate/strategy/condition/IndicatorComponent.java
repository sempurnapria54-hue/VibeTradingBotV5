package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Адресуемый компонент многокомпонентного индикатора в операнде условия:
 * автор выбирает, какую часть индикатора сравнивать. Снимает
 * масштаб-зависимость абсолютного compare (например, MACD-линия в ценовых
 * единицах нестабильна межинструментно — автор берёт осмысленный
 * компонент). У одно-компонентных индикаторов (EMA/RSI/ATR/OBV/
 * EFFICIENCY_RATIO) не задаётся (единственное значение подразумевается).
 * См. docs/decisions/strategy-condition-authoring-contract.md.
 */
public enum IndicatorComponent {

    /** MACD: линия (EMA_fast − EMA_slow). */
    MACD_LINE,

    /** MACD: сигнальная линия (EMA от линии). */
    SIGNAL_LINE,

    /** MACD: гистограмма (линия − сигнальная). */
    HISTOGRAM,

    /** Stochastic: быстрая линия %K. */
    STOCH_K,

    /** Stochastic: сглаженная линия %D. */
    STOCH_D,

    /** Bollinger: верхняя полоса. */
    UPPER_BAND,

    /** Bollinger: средняя полоса. */
    MIDDLE_BAND,

    /** Bollinger: нижняя полоса. */
    LOWER_BAND,

    /** Bollinger: ширина полос (нормированная волатильность). */
    BANDWIDTH,

    /** Bollinger: %B (нормированная позиция цены в полосах). */
    PERCENT_B
}
