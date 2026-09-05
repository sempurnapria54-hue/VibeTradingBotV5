package com.example.marketdata.domain.model;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Заказанная идентичность вычисления индикатора: тип, таймфрейм и
 * канонические параметры.
 *
 * <p><b>Это не настройка стратегии, а вопрос к рынку.</b> Строка отвечает
 * на «ATR(14) на 1H» и принадлежит market-data; кто её заказал — здесь не
 * записано и записываться не должно: одно и то же вычисление шарится
 * между всеми, кому оно нужно, а фичам по всему листингу заказчика нет
 * вовсе (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»).
 *
 * <p><b>Срока свежести на ней тоже нет:</b> толерантность принадлежит
 * читателю и приезжает операндом чтения
 * (docs/rules/market-data-freshness.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class IndicatorConfig {

    /** Внутренний идентификатор идентичности. */
    private Long id;

    /** Межсервисный идентификатор: им идентичность называют потребители. */
    private String internalId;

    /** Тип индикатора. */
    private IndicatorValue.Type indicatorType;

    /** Таймфрейм серии, по которой считается индикатор. */
    private TimeFrame timeframe;

    /** Параметры расчёта по типу индикатора. */
    private IndicatorParams params;
}
