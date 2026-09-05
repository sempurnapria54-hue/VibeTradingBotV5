package com.example.marketdata.mapping;

import static java.util.Objects.isNull;

import com.example.marketdata.api.model.CandleApiResponse;
import com.example.marketdata.api.model.CandleGroupApiResponse;
import com.example.marketdata.api.model.IndicatorConfigApiResponse;
import com.example.marketdata.api.model.IndicatorValueApiResponse;
import com.example.marketdata.api.model.InstrumentApiResponse;
import com.example.marketdata.api.model.MarketOrderBookApiResponse;
import com.example.marketdata.api.model.MarketPhaseApiResponse;
import com.example.marketdata.api.model.MarketPriceLevelApiResponse;
import com.example.marketdata.api.model.MarketStructureApiResponse;
import com.example.marketdata.api.model.MarketStructureConfigApiResponse;
import com.example.marketdata.api.model.MarketTickerApiResponse;
import com.example.marketdata.api.model.OrderBookLevelApiResponse;
import com.example.marketdata.domain.model.IndicatorConfig;
import com.example.marketdata.domain.model.MarketStructureConfig;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.BollingerBandsValue;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.MacdValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import com.example.tradingbot.domain.model.trade.indicator.RsiValue;
import com.example.tradingbot.domain.model.trade.indicator.StochasticValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import com.example.tradingbot.domain.model.trade.market_snapshot.OrderBookLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг domain → api для поверхности чтения.
 *
 * <p><b>Числовые идентификаторы наружу не едут:</b> сущности адресуются
 * {@code internalId} (.claude/rules/codestyle.md §«Идентичность наружу»).
 * Идентификаторы связанных сущностей проставляет вызывающая сторона —
 * она их и получила операндом запроса, так что второго резолва не нужно.
 *
 * <p><b>Значение индикатора собирается в ПЛОСКУЮ форму</b> ветвями по
 * подтипу: компоненты, которых тип не несёт, остаются пустыми.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketDataApiMapper {

    InstrumentApiResponse domainToApi(Instrument instrument);

    List<InstrumentApiResponse> domainToApiInstruments(List<Instrument> instruments);

    CandleGroupApiResponse domainToApi(CandleGroup group);

    List<CandleGroupApiResponse> domainToApiGroups(List<CandleGroup> groups);

    CandleApiResponse domainToApi(Candle candle);

    List<CandleApiResponse> domainToApiCandles(List<Candle> candles);

    IndicatorConfigApiResponse domainToApi(IndicatorConfig config);

    MarketStructureConfigApiResponse domainToApi(MarketStructureConfig config);

    /**
     * Значение индикатора в плоскую форму: базовые поля переносятся, а
     * компонент кладётся по подтипу.
     *
     * <p>Ветвление написано руками, а не выражено полиморфным маппингом:
     * цель у всех подтипов ОДНА, и полиморфизм источника в неё не
     * переходит — переходит только выбор поля.
     */
    default IndicatorValueApiResponse domainToApi(IndicatorValue value) {
        if (isNull(value)) {
            return null;
        }
        IndicatorValueApiResponse response = new IndicatorValueApiResponse();
        response.setIndicatorType(value.getType().name());
        response.setCandleTimestamp(value.getCandleTimestamp());
        fillComponent(value, response);
        return response;
    }

    private void fillComponent(IndicatorValue value, IndicatorValueApiResponse response) {
        switch (value) {
            case AtrValue atr -> response.setAtr(atr.getAtr());
            case EmaValue ema -> response.setEma(ema.getEma());
            case RsiValue rsi -> response.setRsi(rsi.getRsi());
            case ObvValue obv -> response.setObv(obv.getObv());
            case EfficiencyRatioValue ratio -> response.setEfficiencyRatio(ratio.getEfficiencyRatio());
            case MacdValue macd -> {
                response.setMacdLine(macd.getMacdLine());
                response.setSignalLine(macd.getSignalLine());
                response.setHistogram(macd.getHistogram());
            }
            case StochasticValue stochastic -> {
                response.setK(stochastic.getK());
                response.setD(stochastic.getD());
            }
            case BollingerBandsValue bands -> {
                response.setUpperBand(bands.getUpperBand());
                response.setMiddleBand(bands.getMiddleBand());
                response.setLowerBand(bands.getLowerBand());
                response.setBandwidth(bands.getBandwidth());
                response.setPercentB(bands.getPercentB());
            }
            default -> throw new IllegalStateException(
                    "Unmapped indicator value type: " + value.getType());
        }
    }

    @Mapping(target = "breakoutBrokenLevelType", source = "breakoutEvent.brokenLevelType")
    @Mapping(target = "breakoutDirection", source = "breakoutEvent.direction")
    @Mapping(target = "breakoutLevelPrice", source = "breakoutEvent.levelPrice")
    @Mapping(target = "breakoutConfirmedAt", source = "breakoutEvent.confirmedAt")
    MarketStructureApiResponse domainToApi(MarketStructure structure);

    MarketPriceLevelApiResponse domainToApi(MarketPriceLevel level);

    MarketOrderBookApiResponse domainToApi(MarketOrderBook orderBook);

    OrderBookLevelApiResponse domainToApi(OrderBookLevel level);

    MarketTickerApiResponse domainToApi(MarketTicker ticker);

    MarketPhaseApiResponse domainToApi(MarketPhase phase);
}
