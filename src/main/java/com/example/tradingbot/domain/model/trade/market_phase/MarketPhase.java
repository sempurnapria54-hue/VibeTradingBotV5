package com.example.tradingbot.domain.model.trade.market_phase;

/**
 * Фаза рынка — готовый результат классификации рынка, рассчитываемый
 * MarketPhaseJob по StrategyMarketPhaseSetting. Полная модель дозревает
 * на шаге расчёта рыночных данных (Фаза 1, шаг 3 и далее); на шаге 2
 * класс несёт только {@link Type} — по нему выбирается
 * {@code StrategyDetail}. См. docs/models/domain/other/MarketPhase.md.
 */
public class MarketPhase {

    /** Тип рассчитанной фазы рынка. */
    public enum Type {

        /** Бычий тренд. */
        BULL_TREND,

        /** Медвежий тренд. */
        BEAR_TREND,

        /** Боковик (диапазон). */
        RANGE,

        /** Фаза не определена (консервативный режим — не торгуем). */
        UNKNOWN
    }
}
