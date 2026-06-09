package com.example.tradingbot.domain.model.trade.market_phase;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Готовая фаза рынка, рассчитанная MarketPhaseJob по
 * StrategyMarketPhaseSetting на основе готовых IndicatorValue и
 * MarketStructure. Type определяется авторскими условиями (phaseRules)
 * через MarketPhaseClassifier (first-match), не скоринговым алгоритмом.
 * Ключуется контейнером-настройкой (per-strategy) — осознанное
 * исключение из шаринга по идентичности. Хранится история (строка на
 * свечу); актуальная фаза = последняя по candleTimestamp.
 * Persisted-модель рыночных данных. См.
 * docs/models/domain/other/MarketPhase.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketPhase extends Auditable {

    /** Технический ID результата расчёта. */
    private Long id;

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** Настройка стратегии, по которой рассчитана фаза (ключ хранения — её id). */
    private StrategyMarketPhaseSetting setting;

    /** Тип рассчитанной фазы. */
    private Type type;

    /** Время свечи расчёта (точка отсчёта свежести). */
    private OffsetDateTime candleTimestamp;

    /**
     * Время, с которого фазу можно использовать без look-ahead:
     * консервативный max по гейт-операндам сработавшей клаузы (производный,
     * прежний confirmationBars распущен). См.
     * docs/models/domain/other/MarketPhase.md (§Деривация confirmedAt).
     */
    private OffsetDateTime confirmedAt;

    /** Тип фазы рынка. */
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
