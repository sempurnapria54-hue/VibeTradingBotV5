package com.example.tradingbot.domain.command.resolve;

/**
 * Нога РАЗБОРА ИСТОРИИ, нашедшая запись материализованной защиты. Ног три
 * — по числу терминальных {@code state} контракта эндпоинта истории
 * условных заявок, а не по паре значений, которые имелись в виду: перечень
 * выводится из контракта (docs/models/mapping/Order.md). ОТСУТСТВИЕ ноги
 * ({@code null}) — не член перечня: это отсутствие факта, и терминал на
 * нём не ставится (docs/lifecycles/Order.md §«Пустой разбор истории»).
 * Форма исхода — docs/spec/order-lifecycle.json (attachedHistoryStatus,
 * attachedHistoryCloseReason).
 */
public enum ProtectionHistoryLeg {

    /** Запись найдена ногой state=effective: защита сработала. */
    EFFECTIVE,

    /** Запись найдена ногой state=canceled: защита снята. */
    CANCELED,

    /** Запись найдена ногой state=order_failed: сработала, заявка не исполнилась. */
    ORDER_FAILED
}
