package com.example.tradingbot.domain.model.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.market.MarketPhase;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.BooleanUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
public class StrategyDetails extends Auditable {

    /**
     * Технический ID БД.
     */
    private Long id;

    /**
     * Владелец detail.
     */
    private Long strategyId;

    /**
     * Для какой фазы рынка работает detail.
     */
    private MarketPhase.Type marketPhaseType;

    /**
     * Как торгуем в этой фазе рынка.
     */
    private PhaseEntryPolicy phaseEntryPolicy;

    /**
     * Риск на сделку в процентах от доступного капитала.
     */
    private BigDecimal riskPerTradePercent;

    /**
     * Максимально допустимое плечо.
     */
    private Integer maxLeverage;

    /**
     * High-level ориентир reward/risk.
     */
    private BigDecimal targetRiskRewardRatio;

    /**
     * Шаги стратегии, сгруппированные по статусу сделки.
     */
    private Map<Deal.Status, List<StrategyStep>> stepsByStatus;

    public boolean isTradingEnabled() {
        return BooleanUtils.isFalse(isTradingDisabled());
    }

    public boolean isTradingDisabled() {
        if (Objects.isNull(this.phaseEntryPolicy)) {
            return true;
        }

        return Objects.equals(this.phaseEntryPolicy, PhaseEntryPolicy.NO_TRADE);
    }
}
