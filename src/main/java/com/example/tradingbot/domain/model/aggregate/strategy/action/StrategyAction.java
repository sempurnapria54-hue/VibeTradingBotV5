package com.example.tradingbot.domain.model.aggregate.strategy.action;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Ожидаемое действие стратегии. Это не ServiceCommand: действие
 * описывает, что должно быть создано/изменено/отменено; runtime-
 * сущность связывается через DealActionState
 * (strategyActionId → RuntimeTarget). JSON-дискриминатор actionKind
 * (ORDER/ALGO_ORDER/POSITION) — только для сериализации формы ввода,
 * не поле домена. Каркасный реляционный узел дерева: базовая таблица
 * strategy_action + таблицы по видам (JOINED).
 *
 * <p><b>Уровня у действия нет:</b> уровень — свойство ТРАНША
 * (docs/models/domain/aggregate/Strategy.md §StrategyTranche), сетку
 * задаёт шаблон объявления, а порядок исполнения пакета шага определяет
 * риск-класс (docs/rules/live-risk-protection.md). См.
 * docs/models/domain/aggregate/Strategy.md (§Действия, §Связь с
 * DealActionState).
 */
public interface StrategyAction {

    /** Технический ID действия (runtime-связь через DealActionState). */
    Long getId();

    /** Стабильный ключ действия в рамках одной StrategyDetail. */
    String getKey();

    /**
     * Ключ действия, создавшего runtime-сущность, — для REPLACE/CANCEL;
     * для CREATE null. При сохранении резолвится во внутреннюю ссылку.
     */
    String getTargetActionKey();

    /** Тип действия. */
    StrategyActionType getActionType();

    /**
     * Чем задан уровень действия. Резолв — по объявленному блоку настроек
     * (docs/spec/strategy-reference.json, величина
     * {@code actionLevelSource}); собственного поля у него нет.
     */
    StrategyLevelSource levelSource();

    /**
     * Роль объявления уровня: замещение с указанной целью переносит уже
     * стоящий уровень, всякое иное объявление ставит его впервые
     * (docs/spec/stop-distance.json, операнд {@code placementRole}).
     */
    default StrategyPlacementRole placementRole() {
        return StrategyActionType.REPLACE_ACTION.equals(getActionType()) && isNotBlank(getTargetActionKey())
                ? StrategyPlacementRole.TRANSFER
                : StrategyPlacementRole.PRIMARY;
    }
}
