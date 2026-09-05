package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Роль объявления уровня — операнд области первичной постановки
 * (docs/spec/stop-distance.json, операнд {@code placementRole}).
 *
 * <p>Собственного поля в дереве стратегии не заводится: роль выводима из
 * типа действия — замещение с указанной целью переносит уже стоящий
 * уровень, всякое иное объявление ставит его впервые
 * (`.claude/rules/design-simplicity.md`).
 */
public enum StrategyPlacementRole {

    /** Первичная постановка уровня: встроенная защита входа, первичная защитная заявка. */
    PRIMARY,

    /** Перенос уже стоящего уровня защитным замещением с указанной целью. */
    TRANSFER
}
