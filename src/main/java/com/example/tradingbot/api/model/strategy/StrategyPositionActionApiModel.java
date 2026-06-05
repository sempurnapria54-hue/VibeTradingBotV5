package com.example.tradingbot.api.model.strategy;

import lombok.Getter;
import lombok.Setter;

/**
 * Действие над позицией (API, actionKind = POSITION). Собственных полей
 * сверх базовых не несёт; допустимый actionType — только CLOSE_FULL
 * (инвариант no-partial-close).
 */
@Getter
@Setter
public class StrategyPositionActionApiModel extends StrategyActionApiModel {
}
