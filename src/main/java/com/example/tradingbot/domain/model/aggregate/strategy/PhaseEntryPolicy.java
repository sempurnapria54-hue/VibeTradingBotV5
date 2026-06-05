package com.example.tradingbot.domain.model.aggregate.strategy;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.Objects;

/**
 * Политика торговли детали в своей фазе рынка. Матрица допустимости по
 * фазе: BULL_TREND/BEAR_TREND → FOLLOW_PHASE/CONTRARIAN/NO_TRADE;
 * RANGE → GRID/NO_TRADE; UNKNOWN → NO_TRADE (консервативность вне
 * определённости). CONTRARIAN без тренд/волатильностного guard-условия
 * — «ловля ножей» (чек-лист авторинга СТ-1). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyDetail).
 */
public enum PhaseEntryPolicy {

    /** Торгуем по направлению фазы. */
    FOLLOW_PHASE,

    /** Торгуем против направления фазы (требует guard-условий). */
    CONTRARIAN,

    /** Сетка внутри диапазона. */
    GRID,

    /** В этой фазе не торгуем. */
    NO_TRADE;

    /**
     * Допустима ли политика для фазы (матрица допустимости — инвариант
     * доменной модели; NO_TRADE допустим в любой фазе).
     */
    public Boolean isAllowedFor(MarketPhase.Type phase) {
        return switch (this) {
            case NO_TRADE -> true;
            case FOLLOW_PHASE, CONTRARIAN -> Objects.equals(phase, MarketPhase.Type.BULL_TREND)
                    || Objects.equals(phase, MarketPhase.Type.BEAR_TREND);
            case GRID -> Objects.equals(phase, MarketPhase.Type.RANGE);
        };
    }
}
