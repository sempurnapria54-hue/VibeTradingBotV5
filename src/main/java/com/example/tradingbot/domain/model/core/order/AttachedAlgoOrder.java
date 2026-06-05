package com.example.tradingbot.domain.model.core.order;

/**
 * Attached protection — встроенная защита внутри ordinary order
 * (embedded protection parent order), в отличие от standalone
 * {@code AlgoOrder}. Полная модель дозревает на шаге 4 Фазы 1; на
 * шаге 2 класс несёт только {@link Type} — тип attached-защиты в
 * {@code StrategyAttachedProtectionSettings.attachedType}. См.
 * docs/models/domain/core/Order.md (§AttachedAlgoOrder).
 */
public class AttachedAlgoOrder {

    /** Внутренний тип attached-защиты. */
    public enum Type {

        /** Встроенный stop-loss входного ордера. */
        ATTACHED_STOP_LOSS
    }
}
