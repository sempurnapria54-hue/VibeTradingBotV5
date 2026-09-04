package com.example.tradingbot.domain.command.calc;

/**
 * Назначение рассчитанной цены (что именно эта цена выражает). См.
 * docs/components/models/CalculatedPrice.md (§StrategyPricePurpose),
 * docs/components/PriceCalculator.md (таблица «источник цены → тип цены»).
 */
public enum StrategyPricePurpose {

    /** Лимитная цена ордера. */
    ORDER_LIMIT_PRICE,

    /** Reference-цена market-like ордера (на биржу не шлётся). */
    ORDER_MARKET_REFERENCE_PRICE,

    /** Цена новой сущности REPLACE-ремодела. */
    ORDER_REPLACE_PRICE,

    /** Плановая цена входа. */
    ENTRY_PLANNED_PRICE,

    /** Средняя цена входа. */
    ENTRY_AVERAGE_PRICE,

    /** Цена безубытка. */
    BREAKEVEN_PRICE,

    /** Trigger-цена attached stop-loss. */
    ATTACHED_STOP_LOSS_TRIGGER_PRICE,

    /** Order-цена attached stop-loss. */
    ATTACHED_STOP_LOSS_ORDER_PRICE,

    /** Trigger-цена attached take-profit. */
    ATTACHED_TAKE_PROFIT_TRIGGER_PRICE,

    /** Order-цена attached take-profit. */
    ATTACHED_TAKE_PROFIT_ORDER_PRICE,

    /** Trigger-цена standalone stop-loss. */
    STOP_LOSS_TRIGGER_PRICE,

    /** Order-цена standalone stop-loss. */
    STOP_LOSS_ORDER_PRICE,

    /** Trigger-цена standalone take-profit. */
    TAKE_PROFIT_TRIGGER_PRICE,

    /** Order-цена standalone take-profit. */
    TAKE_PROFIT_ORDER_PRICE,

    /** Цена активации трейлинга. */
    TRAILING_ACTIVATION_PRICE,

    /** Callback трейлинга в виде spread. */
    TRAILING_CALLBACK_SPREAD,

    /** Callback трейлинга в виде ratio. */
    TRAILING_CALLBACK_RATIO,

    /** Trigger-цена trigger-ордера. */
    TRIGGER_ORDER_TRIGGER_PRICE,

    /** Цена исполнения trigger-ордера. */
    TRIGGER_ORDER_EXECUTION_PRICE,

    /** Фактическая цена исполнения (по fills). */
    ACTUAL_EXECUTION_PRICE,

    /** Reference-цена расчёта риска позиции. */
    POSITION_RISK_REFERENCE_PRICE,

    /** Цена-ориентир guard'а ликвидации. */
    LIQUIDATION_GUARD_PRICE,

    /** Минимальная допустимая цена (валидация). */
    PRICE_VALIDATION_MIN_PRICE,

    /** Максимальная допустимая цена (валидация). */
    PRICE_VALIDATION_MAX_PRICE,

    /** Шаг округления цены (tick size). */
    PRICE_ROUNDING_STEP
}
