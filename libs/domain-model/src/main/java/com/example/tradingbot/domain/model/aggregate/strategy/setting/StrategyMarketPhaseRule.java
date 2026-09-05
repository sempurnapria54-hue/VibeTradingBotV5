package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Клауза «условие → фаза» в наборе правил классификации
 * StrategyMarketPhaseSetting.phaseRules. Набор — упорядоченный
 * first-match-список: клаузы проверяются по позиции в списке (поле
 * {@code level} снято — трек D, docs/decisions/market-phase-stateless.md),
 * первая с истинным {@code condition} задаёт MarketPhase.Type; не сработала
 * ни одна → UNKNOWN. Condition — тот же StrategyCondition, но в контексте
 * классификации фазы (до выбора детали, без сделки): операнды
 * INDICATOR/MARKET_STRUCTURE/PRICE/CONSTANT/TIME, без MARKET_PHASE и
 * runtime-источников сделки (контекстный whitelist — create-валидация).
 * Хранится JSONB (колонка phase_rules на строке настройки фазы). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyMarketPhaseRule),
 * docs/decisions/market-phase-conditional-classification.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMarketPhaseRule {

    /** Фаза, назначаемая при истинном condition. */
    private MarketPhase.Type type;

    /** Условие клаузы (в контексте классификации фазы). */
    private StrategyCondition condition;
}
