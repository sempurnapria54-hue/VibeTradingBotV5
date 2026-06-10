package com.example.tradingbot.domain.model.aggregate.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Набор торговых правил для конкретной фазы рынка. Каркасный
 * реляционный узел дерева (строка strategy_detail); шаги — плоские строки
 * strategy_step (deal_status + step_index). Индикаторы/структуры, нужные
 * детали, объявлены на уровне стратегии (strategy-scope) и адресуются по
 * {@code key} из условий и листьев действий (трек D — настройки в
 * собственные строки). Ровно одна detail на один MarketPhase.Type
 * (инвариант). См. docs/models/domain/aggregate/Strategy.md (§StrategyDetail).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyDetail extends Auditable {

    /** Технический ID детали. */
    private Long id;

    /** Фаза рынка, в которой работает деталь. */
    private MarketPhase.Type marketPhaseType;

    /** Политика торговли в этой фазе. */
    private PhaseEntryPolicy phaseEntryPolicy;

    /** Риск на сделку, % от капитала (percent-risk модель сайзинга). */
    private BigDecimal riskPerTradePercent;

    /** High-level ориентир risk/reward. */
    private BigDecimal targetRiskRewardRatio;

    /** Шаги, сгруппированные по статусу сделки. */
    private Map<Deal.Status, List<StrategyStep>> stepsByStatus;
}
