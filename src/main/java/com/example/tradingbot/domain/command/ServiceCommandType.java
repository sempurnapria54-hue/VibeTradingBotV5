package com.example.tradingbot.domain.command;

/**
 * Тип атомарной операции команды над runtime-сущностью (17 значений).
 * Амендных команд нет: AMEND_* сняты, ремоделирование — REPLACE-
 * оркестрация существующих команд (docs/decisions/replace-not-amend.md).
 * Refresh-набор — ровно по одной команде на сущность (bulk-команды
 * сняты, CMD-Q3). Graceful shutdown / protection switch / REPLACE /
 * safety-flow собираются из этих команд, отдельных типов под них нет.
 * См. docs/components/models/ServiceCommand.md.
 */
public enum ServiceCommandType {

    /** Обновить баланс по фактам биржи. */
    REFRESH_BALANCE,

    /** Обновить позицию по фактам биржи. */
    REFRESH_POSITION,

    /** Закрыть позицию (market reduce-only). */
    CLOSE_POSITION,

    /** Создать локальный ordinary order. */
    CREATE_ORDER,

    /** Отправить ordinary order на биржу (или восстановить факт по stable client id). */
    SUBMIT_ORDER,

    /** Отменить ordinary order. */
    CANCEL_ORDER,

    /** Обновить ordinary order по фактам (evidence-cycle внутри команды). */
    REFRESH_ORDER,

    /** Создать локальный standalone algo-order. */
    CREATE_ALGO_ORDER,

    /** Отправить algo-order на биржу (или восстановить факт по stable client id). */
    SUBMIT_ALGO_ORDER,

    /** Отменить algo-order. */
    CANCEL_ALGO_ORDER,

    /**
     * Снять ВСТРОЕННУЮ защиту. Своя команда, а не адресат в CANCEL_ALGO_ORDER:
     * цель — другая сущность с непересекающимся словарём причин
     * (docs/components/models/ServiceCommand.md).
     */
    CANCEL_ATTACHED_PROTECTION,

    /** Обновить algo-order по фактам (evidence-cycle внутри команды). */
    REFRESH_ALGO_ORDER,

    /** Подтянуть fills (evidence-cycle 3d→3m внутри команды). */
    REFRESH_FILLS,

    /** Финализировать вход сделки (подтверждённая позиция). */
    FINALIZE_DEAL_ENTRY,

    /** Финализировать выход сделки (расчёт итогового результата). */
    FINALIZE_DEAL_EXIT,

    /** Пометить сделку штатно закрытой. */
    MARK_DEAL_CLOSED,

    /** Пометить сделку в ошибке. */
    MARK_DEAL_ERROR
}
