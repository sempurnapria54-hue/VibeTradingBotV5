package com.example.tradingbot.domain.model.aggregate.strategy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Политика шага на случай устаревания нужных именно этому шагу данных.
 * Обязательна для каждого шага (default на уровне detail не
 * используется); не определяет, когда данные устарели — это
 * expirationDuration настроек + MarketDataExpirationChecker. Хранится
 * JSONB-полем на строке strategy_step. См.
 * docs/models/domain/aggregate/Strategy.md
 * (§StrategyMarketDataExpiredSetting).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMarketDataExpiredSetting {

    /** Реакция при защищённой позиции. */
    private MarketDataExpiredAction protectedPositionAction;

    /** Реакция при незащищённой позиции. */
    private MarketDataExpiredAction unprotectedPositionAction;
}
