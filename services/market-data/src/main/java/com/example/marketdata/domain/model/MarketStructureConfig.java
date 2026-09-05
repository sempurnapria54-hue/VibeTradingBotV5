package com.example.marketdata.domain.model;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Заказанная идентичность вычисления структуры рынка: таймфрейм,
 * параметры окна и идентичности её входов.
 *
 * <p><b>Идентичности входов входят в идентичность результата.</b> Две
 * структуры с одинаковым окном, но разными ER/ATR — разные вычисления, и
 * без входов в ключе последняя записанная затирала бы чужую
 * (docs/models/domain/other/MarketStructure.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketStructureConfig {

    /** Внутренний идентификатор идентичности. */
    private Long id;

    /** Межсервисный идентификатор: им идентичность называют потребители. */
    private String internalId;

    /** Таймфрейм серии, по которой считается структура. */
    private TimeFrame timeframe;

    /** Параметры расчёта окна и уровней. */
    private MarketStructureParams params;

    /** Идентичность входного ER; пусто — вход не объявлен. */
    private Long efficiencyRatioConfigId;

    /** Идентичность входного ATR; пусто — вход не объявлен. */
    private Long atrConfigId;
}
