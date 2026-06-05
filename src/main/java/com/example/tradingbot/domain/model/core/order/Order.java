package com.example.tradingbot.domain.model.core.order;

/**
 * Ordinary order — заявка на бирже. Полная модель (runtime-поля,
 * external snapshots, attached protection) дозревает на шаге 4 Фазы 1
 * (команды и их жизненный цикл); на шаге 2 класс несёт только
 * {@link Type} — бизнес-тип ордера, на который ссылается
 * {@code StrategyOrderAction.orderType}. См.
 * docs/models/domain/core/Order.md, docs/lifecycles/Order.md.
 */
public class Order {

    /** Бизнес-тип ordinary order (не подменяет strategy role). */
    public enum Type {

        /** Входной ордер без встроенной защиты. */
        ENTRY,

        /** Входной ордер со встроенным attached stop-loss. */
        ENTRY_ATTACHED_STOP_LOSS
    }
}
