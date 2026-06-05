package com.example.tradingbot.domain.model.aggregate.strategy.action;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ожидаемое действие над позицией. Допустим только CLOSE_FULL:
 * CLOSE_PARTIAL запрещён всегда (постоянный инвариант
 * docs/rules/no-partial-close.md) — частичное уменьшение только через
 * Order/AlgoOrder с position-reducing-only semantics. Собственных
 * полей сверх общих не несёт. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyPositionAction).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyPositionAction extends Auditable implements StrategyAction {

    /** Технический ID действия. */
    private Long id;

    /** Стабильный ключ действия в рамках StrategyDetail. */
    private String key;

    /** Ключ target-действия; у позиционного действия обычно null. */
    private String targetActionKey;

    /** Тип действия: только CLOSE_FULL. */
    private StrategyActionType actionType;

    /** Уровень действия внутри стратегии. */
    private Integer level;
}
