package com.example.tradingcore.integration.internal.api.model;

import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Фичи одного момента решения от владельца рыночных данных.
 *
 * <p><b>Раскладки ключуются нашими авторскими именами операндов</b> — теми,
 * которыми мы назвали их в привязках запроса: идентичность вычисления есть
 * номенклатура владельца, и переводить ответ обратно в свои имена читателю
 * не приходится.
 *
 * <p><b>Отсутствующее имя означает «вход недоступен».</b> Устаревшее и
 * несуществующее значение ключа не занимают вовсе — обе пустоты ведут к
 * одной реакции (docs/spec/market-data-freshness.json).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketFeatureBundleResponse {

    /** Последние свежие значения индикаторов по авторскому имени операнда. */
    private Map<String, IndicatorValueResponse> latestIndicators;

    /** Предыдущие значения тех же идентичностей — вторая половина сравнений. */
    private Map<String, IndicatorValueResponse> previousIndicators;

    /** Последние свежие структуры рынка по авторскому имени операнда. */
    private Map<String, MarketStructureResponse> structures;

    /** Цены момента; пусто — не спрашивались либо площадка отказала. */
    private MarketPriceData marketPriceData;

    /** Фаза рынка по нашим клаузам; пусто — клауз не передавали. */
    private MarketPhaseResponse marketPhase;
}
