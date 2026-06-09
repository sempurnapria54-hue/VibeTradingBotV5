package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.BollingerBandsValue;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.MacdValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import com.example.tradingbot.domain.model.trade.indicator.RsiValue;
import com.example.tradingbot.domain.model.trade.indicator.StochasticValue;
import com.example.tradingbot.persistence.model.marketdata.AtrValueEntity;
import com.example.tradingbot.persistence.model.marketdata.BollingerBandsValueEntity;
import com.example.tradingbot.persistence.model.marketdata.EfficiencyRatioValueEntity;
import com.example.tradingbot.persistence.model.marketdata.EmaValueEntity;
import com.example.tradingbot.persistence.model.marketdata.IndicatorValueEntity;
import com.example.tradingbot.persistence.model.marketdata.MacdValueEntity;
import com.example.tradingbot.persistence.model.marketdata.ObvValueEntity;
import com.example.tradingbot.persistence.model.marketdata.RsiValueEntity;
import com.example.tradingbot.persistence.model.marketdata.StochasticValueEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

/**
 * Маппинг значения индикатора domain ↔ persistence. Полиморфные ветви
 * по типу индикатора — через SubclassMapping (плоская SINGLE_TABLE на
 * стороне persistence, дискриминатор выставляет JPA по подклассу).
 * Базовые поля (instrumentId/configId/candleTimestamp) и значения
 * наследников маппятся по имени.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface IndicatorValueMapper {

    @SubclassMapping(source = AtrValue.class, target = AtrValueEntity.class)
    @SubclassMapping(source = EmaValue.class, target = EmaValueEntity.class)
    @SubclassMapping(source = RsiValue.class, target = RsiValueEntity.class)
    @SubclassMapping(source = MacdValue.class, target = MacdValueEntity.class)
    @SubclassMapping(source = BollingerBandsValue.class, target = BollingerBandsValueEntity.class)
    @SubclassMapping(source = StochasticValue.class, target = StochasticValueEntity.class)
    @SubclassMapping(source = ObvValue.class, target = ObvValueEntity.class)
    @SubclassMapping(source = EfficiencyRatioValue.class, target = EfficiencyRatioValueEntity.class)
    IndicatorValueEntity domainToPersistence(IndicatorValue value);

    @SubclassMapping(source = AtrValueEntity.class, target = AtrValue.class)
    @SubclassMapping(source = EmaValueEntity.class, target = EmaValue.class)
    @SubclassMapping(source = RsiValueEntity.class, target = RsiValue.class)
    @SubclassMapping(source = MacdValueEntity.class, target = MacdValue.class)
    @SubclassMapping(source = BollingerBandsValueEntity.class, target = BollingerBandsValue.class)
    @SubclassMapping(source = StochasticValueEntity.class, target = StochasticValue.class)
    @SubclassMapping(source = ObvValueEntity.class, target = ObvValue.class)
    @SubclassMapping(source = EfficiencyRatioValueEntity.class, target = EfficiencyRatioValue.class)
    IndicatorValue persistenceToDomain(IndicatorValueEntity entity);
}
