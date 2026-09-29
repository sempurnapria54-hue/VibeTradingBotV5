package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Общий тип действия стратегии. Какие значения допустимы у какого вида
 * действия — перечень пар в docs/models/domain/aggregate/Strategy.md
 * §Действия; пару вне него отвергает создание определения.
 *
 * <p><b>Маркер уровня — суффикс {@code _ACTION}</b>, а не уникальные
 * основы: действие стратегии есть уровень абстракции НАД командой
 * ({@code ServiceCommandType.CREATE_ORDER_COMMAND} и соседние), и уровень
 * читается по хвосту имени в любом значении
 * (.claude/rules/naming.md §«Разведение уровней абстракции»).
 *
 * <p>Частичное уменьшение позиции действием не выражается — только
 * reduce-only заявкой (docs/rules/no-partial-close.md); полное закрытие
 * выражается либо условием-переходом, либо явным {@code EXIT_ACTION}
 * шага {@code EXIT} (docs/rules/no-partial-close.md §«Две законные формы
 * полного выхода»). См. docs/models/domain/aggregate/Strategy.md (§Действия).
 */
public enum StrategyActionType {

    /** Создать runtime-сущность. */
    CREATE_ACTION,

    /**
     * Ремоделировать runtime-сущность, созданную target-действием:
     * заместить новой сущностью + отменить старую (AMEND из домена
     * убран; docs/rules/replace-not-amend.md).
     */
    REPLACE_ACTION,

    /** Отменить runtime-сущность, созданную target-действием. */
    CANCEL_ACTION,

    /**
     * Выйти: снять живые входные ноги своей области и закрыть экспозицию.
     * Область задаёт уровень объявления: у транша действие исполняет
     * исполнитель выхода, у сделки — сворачивание
     * (docs/rules/no-partial-close.md).
     */
    EXIT_ACTION
}
