package com.example.tradingbot.domain.service.deal.command.core;

/**
 * Сервисные команды, которые возвращает state machine.
 * <p>
 * Важно:
 * - это не команды биржи;
 * - это команды application/service-слою;
 * - уже соответствующий сервис сам решает, что именно делать:
 * читать БД, вызывать биржу, сохранять результат и т.д.
 */
public enum ServiceCommandType {

    /**
     * Обновить фактические позиции по инструменту.
     * <p>
     * Обычно этим занимается PositionService:
     * - читает состояние на бирже;
     * - синхронизирует позиции в БД;
     * - после этого DealContextService может заново собрать контекст.
     */
    REFRESH_POSITIONS,

    /**
     * Обновить баланс/маржу аккаунта.
     * <p>
     * Обычно этим занимается AccountService или BalanceService.
     */
    REFRESH_BALANCE,

    /**
     * Обновить pending обычные ордера по инструменту.
     * <p>
     * Обычно этим занимается OrderService.
     */
    REFRESH_PENDING_ORDERS,

    /**
     * Отправить entry order на вход в сделку.
     * <p>
     * Обычно этим занимается OrderService:
     * - рассчитывает параметры входа;
     * - создаёт Order;
     * - вызывает биржу;
     * - сохраняет результат в БД.
     */
    CREATE_ENTRY_ORDER,

    /**
     * Обновить состояние входного ордера.
     * <p>
     * Обычно этим занимается OrderService:
     * - читает детали ордера на бирже;
     * - обновляет его статус/поля в БД.
     */
    REFRESH_ENTRY_ORDER,

    /**
     * Обновить активные algo-ордера сделки.
     * <p>
     * Обычно этим занимается AlgoOrderService.
     */
    REFRESH_ALGO_ORDERS,

    /**
     * Создать основную защиту сделки.
     * <p>
     * Обычно этим занимается AlgoOrderService:
     * - создаёт основной SL/TP/trailing;
     * - отправляет их на биржу;
     * - сохраняет их в БД.
     */
    CREATE_MAIN_PROTECTION,

    /**
     * Отменить attached-защиту после переключения на основную.
     * <p>
     * Обычно этим занимается AlgoOrderService или отдельный AttachedAlgoOrderService.
     */
    CANCEL_ATTACHED_PROTECTION,

    /**
     * Изменить основную защиту сделки.
     * <p>
     * Обычно этим занимается AlgoOrderService:
     * - amend existing algo,
     * - либо cancel + recreate, если так требует логика.
     */
    AMEND_MAIN_PROTECTION,

    /**
     * Финализировать закрытие сделки.
     * <p>
     * Обычно этим занимается DealService / PositionService / AlgoOrderService совместно:
     * - убрать хвосты;
     * - собрать fills/history;
     * - определить closeReason;
     * - завершить сделку в БД.
     */
    FINALIZE_EXIT,

    /**
     * Выполнить kill-switch по инструменту.
     * <p>
     * Обычно это оркестр нескольких сервисов:
     * - отменить pending обычные ордера;
     * - отменить pending algo-ордера;
     * - закрыть позицию;
     * - убедиться, что риск по инструменту снят.
     */
    EXECUTE_KILL_SWITCH
}
