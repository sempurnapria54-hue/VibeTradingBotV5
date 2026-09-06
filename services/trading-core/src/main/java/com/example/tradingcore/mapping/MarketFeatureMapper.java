package com.example.tradingcore.mapping;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

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
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.integration.model.IndicatorValueResponse;
import com.example.tradingcore.integration.model.MarketFeatureBundleResponse;
import com.example.tradingcore.integration.model.MarketPhaseResponse;
import com.example.tradingcore.integration.model.MarketPriceLevelResponse;
import com.example.tradingcore.integration.model.MarketStructureResponse;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг ответа владельца рыночных данных в доменные фичи прохода.
 *
 * <p><b>Подтип значения восстанавливается по названному типу.</b> Владелец
 * отдаёт плоскую форму со всеми компонентами, и полиморфизм цели в ней не
 * выражен — его собирает ветвление здесь, ровно как обратное ветвление
 * собирает плоскую форму у владельца.
 *
 * <p><b>Незнакомый тип индикатора — отказ, а не пустое значение.</b>
 * Каталог перечня общий (артефакт {@code domain-model}), и значение с
 * типом вне него означает, что стороны разошлись версиями; подставить на
 * его место пустоту значило бы дать предикату консервативную ложь по
 * причине, которой у него нет, — и молча не исполнить шаг стратегии.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketFeatureMapper {

    /** Связка ответа в фичи прохода: раскладки поэлементно, цены как есть. */
    MarketFeatures responseToDomain(MarketFeatureBundleResponse response);

    /**
     * Значение индикатора из плоской формы: базовые поля переносятся,
     * подтип выбирается по названному типу.
     */
    default IndicatorValue responseToDomain(IndicatorValueResponse response) {
        if (isNull(response) || isBlank(response.getIndicatorType())) {
            return null;
        }
        IndicatorValue value = byType(IndicatorValue.Type.valueOf(response.getIndicatorType()), response);
        value.setCandleTimestamp(response.getCandleTimestamp());
        return value;
    }

    private IndicatorValue byType(IndicatorValue.Type type, IndicatorValueResponse response) {
        return switch (type) {
            case ATR -> atr(response);
            case EMA -> ema(response);
            case RSI -> rsi(response);
            case OBV -> obv(response);
            case EFFICIENCY_RATIO -> efficiencyRatio(response);
            case MACD -> macd(response);
            case STOCHASTIC -> stochastic(response);
            case BOLLINGER_BANDS -> bollingerBands(response);
        };
    }

    private IndicatorValue atr(IndicatorValueResponse response) {
        AtrValue value = new AtrValue();
        value.setAtr(response.getAtr());
        return value;
    }

    private IndicatorValue ema(IndicatorValueResponse response) {
        EmaValue value = new EmaValue();
        value.setEma(response.getEma());
        return value;
    }

    private IndicatorValue rsi(IndicatorValueResponse response) {
        RsiValue value = new RsiValue();
        value.setRsi(response.getRsi());
        return value;
    }

    private IndicatorValue obv(IndicatorValueResponse response) {
        ObvValue value = new ObvValue();
        value.setObv(response.getObv());
        return value;
    }

    private IndicatorValue efficiencyRatio(IndicatorValueResponse response) {
        EfficiencyRatioValue value = new EfficiencyRatioValue();
        value.setEfficiencyRatio(response.getEfficiencyRatio());
        return value;
    }

    private IndicatorValue macd(IndicatorValueResponse response) {
        MacdValue value = new MacdValue();
        value.setMacdLine(response.getMacdLine());
        value.setSignalLine(response.getSignalLine());
        value.setHistogram(response.getHistogram());
        return value;
    }

    private IndicatorValue stochastic(IndicatorValueResponse response) {
        StochasticValue value = new StochasticValue();
        value.setK(response.getK());
        value.setD(response.getD());
        return value;
    }

    private IndicatorValue bollingerBands(IndicatorValueResponse response) {
        BollingerBandsValue value = new BollingerBandsValue();
        value.setUpperBand(response.getUpperBand());
        value.setMiddleBand(response.getMiddleBand());
        value.setLowerBand(response.getLowerBand());
        value.setBandwidth(response.getBandwidth());
        value.setPercentB(response.getPercentB());
        return value;
    }

    Map<String, IndicatorValue> responseToDomainIndicators(Map<String, IndicatorValueResponse> responses);

    Map<String, MarketStructure> responseToDomainStructures(Map<String, MarketStructureResponse> responses);

    /**
     * Структура из плоской формы: событие пробоя собирается обратно во
     * вложенную, которую читает условие подтверждённого пробоя.
     */
    @Mapping(target = "breakoutEvent", source = "response")
    MarketStructure responseToDomain(MarketStructureResponse response);

    List<MarketPriceLevel> responseToDomainLevels(List<MarketPriceLevelResponse> responses);

    MarketPriceLevel responseToDomain(MarketPriceLevelResponse response);

    /**
     * Событие подтверждённого пробоя; пусто — пробоя в окне нет.
     *
     * <p>Признак наличия — <b>тип сломанного уровня</b>: он есть у всякого
     * события и отсутствует у всякой структуры без пробоя. Собирать
     * событие всегда значило бы отдать условию непустой объект с пустыми
     * полями, и предикат «пробой есть» стал бы истинным везде.
     */
    default MarketBreakoutEvent responseToBreakoutEvent(MarketStructureResponse response) {
        if (isNull(response) || isBlank(response.getBreakoutBrokenLevelType())) {
            return null;
        }
        MarketBreakoutEvent event = new MarketBreakoutEvent();
        event.setBrokenLevelType(MarketPriceLevel.Type.valueOf(response.getBreakoutBrokenLevelType()));
        event.setDirection(MarketBreakoutEvent.Direction.valueOf(response.getBreakoutDirection()));
        event.setLevelPrice(response.getBreakoutLevelPrice());
        event.setConfirmedAt(response.getBreakoutConfirmedAt());
        return event;
    }

    MarketPhase responseToDomain(MarketPhaseResponse response);
}
