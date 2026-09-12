package com.example.strategy.engine.calc;

/**
 * Назначение рассчитанной цены — что именно она выражает
 * (docs/components/models/CalculatedPrice.md §StrategyPricePurpose).
 *
 * <p><b>Перечень равен тому, что расчётный слой эмитит</b>, а не каталогу
 * всех мыслимых цен: значение, которое никто не производит, различает
 * несуществующее (.claude/rules/codestyle.md §«Неиспользуемый код»).
 */
public enum StrategyPricePurpose {

    /** Лимитная цена входной заявки. */
    ORDER_LIMIT_PRICE,

    /** Цена-ориентир market-like входа; на биржу не уходит. */
    ORDER_MARKET_REFERENCE_PRICE,

    /** Триггерная цена уровня остановки убытка. */
    STOP_LOSS_TRIGGER_PRICE,

    /** Триггерная цена уровня фиксации прибыли. */
    TAKE_PROFIT_TRIGGER_PRICE,

    /** Цена активации трейлинга. */
    TRAILING_ACTIVATION_PRICE,

    /**
     * Цена безубытка — уровень нулевого P&amp;L с учётом round-trip
     * комиссии. Разведён с уровнем остановки убытка намеренно: назначение
     * «безубыток» обещает ноль, а не дистанцию, и по данным это обязано
     * быть различимо (docs/concept.md, П3).
     */
    BREAKEVEN_PRICE
}
