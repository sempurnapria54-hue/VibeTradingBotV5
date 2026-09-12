package com.example.tradingbot.domain.model.trade.market_phase;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Фаза рынка — вычисляемое на лету значение (не персистируется): резолвится
 * по запросу из текущих IndicatorValue / MarketStructure через
 * MarketPhaseResolver (first-match по авторским phaseRules). Своих часов и
 * срока свежести у фазы нет — свежесть наследуется от входов (устаревший
 * вход → недоступен → UNKNOWN). EntryScannerJob по type выбирает
 * StrategyDetail. См. docs/components/MarketPhaseService.md,
 * docs/models/domain/other/MarketPhase.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketPhase {

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** Тип фазы (первая сработавшая клауза phaseRules, либо UNKNOWN). */
    private Type type;

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
