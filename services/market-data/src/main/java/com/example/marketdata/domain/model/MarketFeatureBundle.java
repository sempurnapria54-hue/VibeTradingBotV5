package com.example.marketdata.domain.model;

import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Фичи одного момента решения: готовые значения по авторским именам
 * операндов, цена момента и классифицированная фаза. Runtime-объект, не
 * хранится.
 *
 * <p><b>Предыдущее значение индикатора — не удобство, а операнд.</b> На
 * нём стои́т весь класс правил пересечения ({@code CROSSOVER}) и объёмный
 * фильтр: они сравнивают текущее значение с прошлым
 * (docs/components/StrategyConditionEvaluator.md). Читатель, получивший
 * только последнее, вычислил бы такое правило на пустом операнде и
 * получил бы ЛОЖЬ — то есть шаг стратегии не исполнился бы молча, без
 * отказа и без записи в журнале.
 *
 * <p><b>Свежестью гейтится только последнее значение.</b> Предыдущее —
 * направление, а не точка решения: гейтить его собственным сроком значило
 * бы отбрасывать вторую половину сравнения, оставляя первую
 * (docs/rules/market-data-freshness.md).
 *
 * <p><b>Пустое место в раскладке означает «вход недоступен».</b>
 * Отсутствующее и устаревшее ключа в раскладке не занимают вовсе, и
 * читатель отличает «нет» от «есть, но старое» ровно так же, как на
 * одиночном чтении — никак: обе пустоты ведут к одной реакции
 * (docs/spec/market-data-freshness.json).
 */
@Getter
@Builder
public class MarketFeatureBundle {

    /** Последние свежие значения индикаторов по авторскому имени операнда. */
    private final Map<String, IndicatorValue> latestIndicators;

    /** Предыдущие значения тех же идентичностей — вторая половина сравнений. */
    private final Map<String, IndicatorValue> previousIndicators;

    /** Последние свежие структуры рынка по авторскому имени операнда. */
    private final Map<String, MarketStructure> structures;

    /** Цены момента; пусто — читатель их не спрашивал либо площадка отказала. */
    private final MarketPriceData marketPriceData;

    /** Фаза рынка по клаузам читателя; пусто — клауз не передано. */
    private final MarketPhase marketPhase;
}
