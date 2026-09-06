package com.example.tradingcore.domain.command;

/**
 * Тип атомарной операции над runtime-сущностью.
 *
 * <p>Амендных команд нет: ремодел идёт замещением
 * (docs/rules/replace-not-amend.md). Пакетных чтений тоже нет — на
 * сущность ровно одна команда добычи.
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

    /** Обновить снимок средств счёта по фактам площадки. */
    REFRESH_BALANCE_COMMAND,

    /** Обновить эпизод позиции по фактам площадки. */
    REFRESH_POSITION_COMMAND,

    /** Закрыть позицию по рынку. */
    CLOSE_POSITION_COMMAND,

    /** Создать локальную обычную заявку. */
    CREATE_ORDER_COMMAND,

    /** Отправить обычную заявку либо восстановить факт отправки по клиентскому идентификатору. */
    SUBMIT_ORDER_COMMAND,

    /** Отменить обычную заявку. */
    CANCEL_ORDER_COMMAND,

    /** Обновить обычную заявку по фактам: цикл добычи внутри команды. */
    REFRESH_ORDER_COMMAND,

    /** Создать локальную отдельную условную заявку. */
    CREATE_ALGO_ORDER_COMMAND,

    /** Отправить условную заявку либо восстановить факт отправки по клиентскому идентификатору. */
    SUBMIT_ALGO_ORDER_COMMAND,

    /** Отменить отдельную условную заявку. */
    CANCEL_ALGO_ORDER_COMMAND,

    /**
     * Снять ВСТРОЕННУЮ защиту. Своя команда, а не адресат в отмене
     * условной заявки: цель — другая сущность с непересекающимся словарём
     * причин (docs/components/models/ServiceCommand.md).
     */
    CANCEL_ATTACHED_PROTECTION_COMMAND,

    /** Обновить условную заявку по фактам: цикл добычи внутри команды. */
    REFRESH_ALGO_ORDER_COMMAND,

    /** Подтянуть движения средств окна сделки: конвейер свежий → архив внутри команды. */
    REFRESH_BILLS_COMMAND,

    /** Финализировать вход транша по подтверждённой позиции. */
    FINALIZE_DEAL_ENTRY_COMMAND,

    /** Финализировать выход сделки: итоговое число и признаки отбора. */
    FINALIZE_DEAL_EXIT_COMMAND,

    /** Пометить сделку штатно закрытой. */
    MARK_DEAL_CLOSED_COMMAND,

    /** Пометить сделку в ошибке. */
    MARK_DEAL_ERROR_COMMAND,

    /** Поставить аварийный терминал сделки. */
    MARK_DEAL_EMERGENCY_CLOSED_COMMAND
}
