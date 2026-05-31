package com.example.tradingbot.domain.model.commands;

public enum ServiceCommandType {

    /**
     * Обновить локальный snapshot баланса с биржи.
     */
    REFRESH_BALANCE,

    /**
     * Обновить локальное состояние позиции по инструменту сделки.
     */
    REFRESH_POSITION,

    /**
     * Закрыть активную позицию по команде стратегии или защитного flow.
     */
    CLOSE_POSITION,

    /**
     * Создать локальный обычный order в БД без обращения к бирже.
     */
    CREATE_ORDER,

    /**
     * Отправить локальный order на биржу или восстановить факт отправки по clientOrderId.
     */
    SUBMIT_ORDER,

    /**
     * Изменить уже созданный order на бирже.
     */
    AMEND_ORDER,

    /**
     * Отменить уже созданный order на бирже.
     */
    CANCEL_ORDER,

    /**
     * Обновить состояние одного или нескольких orders сделки по snapshot биржи.
     */
    REFRESH_ORDER,

    /**
     * Обновить pending orders по инструменту сделки.
     */
    REFRESH_PENDING_ORDERS,

    /**
     * Обновить историческое состояние orders, которые уже могли финализироваться.
     */
    REFRESH_ORDER_HISTORY,

    /**
     * Создать локальный algo-order в БД без обращения к бирже.
     */
    CREATE_ALGO_ORDER,

    /**
     * Отправить локальный algo-order на биржу или восстановить факт отправки по clientAlgoOrderId.
     */
    SUBMIT_ALGO_ORDER,

    /**
     * Изменить algo-order. Если биржа не поддерживает amend, executor может выполнить cancel + create.
     */
    AMEND_ALGO_ORDER,

    /**
     * Отменить algo-order на бирже.
     */
    CANCEL_ALGO_ORDER,

    /**
     * Обновить состояние одного algo-order по snapshot биржи.
     */
    REFRESH_ALGO_ORDER,

    /**
     * Обновить активные algo-orders сделки.
     */
    REFRESH_ALGO_ORDERS,

    /**
     * Обновить историческое состояние algo-orders, которые уже могли финализироваться.
     */
    REFRESH_ALGO_ORDER_HISTORY,

    /**
     * Обновить fills сделки, если для текущего flow это требуется.
     */
    REFRESH_FILLS,

    /**
     * Зафиксировать завершение входа в сделку.
     */
    FINALIZE_DEAL_ENTRY,

    /**
     * Зафиксировать завершение выхода из сделки.
     */
    FINALIZE_DEAL_EXIT,

    /**
     * Пометить сделку закрытой после подтверждения финальных фактов.
     */
    MARK_DEAL_CLOSED,

    /**
     * Пометить сделку ошибочной после защитного flow.
     */
    MARK_DEAL_ERROR,

    /**
     * Запустить kill-switch для снятия риска по инструменту сделки.
     */
    EXECUTE_KILL_SWITCH
}
