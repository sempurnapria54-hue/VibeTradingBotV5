package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Фичи одного момента решения наружу.
 *
 * <p><b>Раскладки ключуются авторскими именами операндов</b>, которыми их
 * назвал сам читатель в привязках: идентичность вычисления — номенклатура
 * владельца, и заставлять читателя переводить ответ обратно в свои имена
 * значило бы отдавать ему работу, которую он уже сделал запросом.
 *
 * <p><b>Отсутствующее имя означает «вход недоступен».</b> Устаревшее и
 * несуществующее значение ключа не занимают вовсе — обе пустоты ведут к
 * одной реакции читателя, и различать их ему не нужно
 * (docs/spec/market-data-freshness.json).
 */
@Getter
@Setter
public class MarketFeatureBundleApiResponse {

    @Schema(description = "Инструмент, по которому сняты фичи")
    private String instrumentInternalId;

    @Schema(description = "Последние свежие значения индикаторов по авторскому имени операнда")
    private Map<String, IndicatorValueApiResponse> latestIndicators;

    @Schema(description = "Предыдущие значения тех же идентичностей — вторая половина сравнений")
    private Map<String, IndicatorValueApiResponse> previousIndicators;

    @Schema(description = "Последние свежие структуры рынка по авторскому имени операнда")
    private Map<String, MarketStructureApiResponse> structures;

    @Schema(description = "Цены момента; пусто — не спрашивались либо площадка отказала")
    private MarketPriceData marketPriceData;

    @Schema(description = "Фаза рынка по клаузам читателя; пусто — клауз не передано")
    private MarketPhaseApiResponse marketPhase;
}
