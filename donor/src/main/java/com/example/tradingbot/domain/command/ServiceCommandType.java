package com.example.tradingbot.domain.command;

/**
 * Тип атомарной операции команды над runtime-сущностью. Амендных команд
 * нет: AMEND_* сняты, ремоделирование — REPLACE-оркестрация существующих
 * команд (docs/rules/replace-not-amend.md). Refresh-набор — ровно по
 * одной команде на сущность (bulk-команды сняты). Graceful shutdown /
 * protection switch / REPLACE / safety-flow собираются из этих команд,
 * отдельных типов под них нет.
 *
 * <p><b>Маркер уровня — суффикс {@code _COMMAND}:</b> команда есть
 * уровень абстракции ПОД действием ({@link SystemActionType} и
 * {@code StrategyActionType} с суффиксом {@code _ACTION}), и уровень
 * читается по хвосту имени в любом значении
 * (.claude/rules/naming.md §«Разведение уровней абстракции»). В БД
 * значения не хранятся — миграции значений перечень не требует.
 *
 * <p>См. docs/components/models/ServiceCommand.md.
 */
public enum ServiceCommandType {

    /** Обновить баланс по фактам биржи. */
    REFRESH_BALANCE_COMMAND,

    /** Обновить позицию по фактам биржи. */
    REFRESH_POSITION_COMMAND,

    /** Закрыть позицию (market reduce-only). */
    CLOSE_POSITION_COMMAND,

    /** Создать локальный ordinary order. */
    CREATE_ORDER_COMMAND,

    /** Отправить ordinary order на биржу (или восстановить факт по stable client id). */
    SUBMIT_ORDER_COMMAND,

    /** Отменить ordinary order. */
    CANCEL_ORDER_COMMAND,

    /** Обновить ordinary order по фактам (evidence-cycle внутри команды). */
    REFRESH_ORDER_COMMAND,

    /** Создать локальный standalone algo-order. */
    CREATE_ALGO_ORDER_COMMAND,

    /** Отправить algo-order на биржу (или восстановить факт по stable client id). */
    SUBMIT_ALGO_ORDER_COMMAND,

    /** Отменить algo-order. */
    CANCEL_ALGO_ORDER_COMMAND,

    /**
     * Снять ВСТРОЕННУЮ защиту. Своя команда, а не адресат в
     * CANCEL_ALGO_ORDER_COMMAND: цель — другая сущность с
     * непересекающимся словарём причин
     * (docs/components/models/ServiceCommand.md).
     */
    CANCEL_ATTACHED_PROTECTION_COMMAND,

    /** Обновить algo-order по фактам (evidence-cycle внутри команды). */
    REFRESH_ALGO_ORDER_COMMAND,

    /** Подтянуть движения средств окна сделки (конвейер свежий→архив внутри команды). */
    REFRESH_BILLS_COMMAND,

    /** Финализировать вход транша (подтверждённая позиция). */
    FINALIZE_DEAL_ENTRY_COMMAND,

    /** Финализировать выход сделки (расчёт итогового результата и признаков отбора). */
    FINALIZE_DEAL_EXIT_COMMAND,

    /** Пометить сделку штатно закрытой. */
    MARK_DEAL_CLOSED_COMMAND,

    /** Пометить сделку в ошибке. */
    MARK_DEAL_ERROR_COMMAND,

    /** Поставить аварийный терминал сделки (ERROR → EMERGENCY_CLOSED). */
    MARK_DEAL_EMERGENCY_CLOSED_COMMAND
}
