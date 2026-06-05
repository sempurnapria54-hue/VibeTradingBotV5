package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Ожидаемое действие стратегии. Это не ServiceCommand: действие
 * описывает, что должно быть создано/изменено/отменено; runtime-
 * сущность связывается через DealActionState
 * (strategyActionId → RuntimeTarget). JSON-дискриминатор actionKind
 * (ORDER/ALGO_ORDER/POSITION) — только для сериализации формы ввода,
 * не поле домена. Каркасный реляционный узел дерева: базовая таблица
 * strategy_action + таблицы по видам (JOINED). См.
 * docs/models/domain/aggregate/Strategy.md (§Действия, §Связь с
 * DealActionState).
 */
public interface StrategyAction {

    /** Технический ID действия (runtime-связь через DealActionState). */
    Long getId();

    /** Стабильный ключ действия в рамках одной StrategyDetail. */
    String getKey();

    /**
     * Ключ действия, создавшего runtime-сущность, — для AMEND/CANCEL;
     * для CREATE null. При сохранении резолвится во внутреннюю ссылку.
     */
    String getTargetActionKey();

    /** Тип действия. */
    StrategyActionType getActionType();

    /** Уровень действия внутри стратегии (не переносится в runtime-сущности). */
    Integer getLevel();
}
