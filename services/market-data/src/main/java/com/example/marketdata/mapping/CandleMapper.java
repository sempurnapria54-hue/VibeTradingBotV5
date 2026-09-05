package com.example.marketdata.mapping;

import com.example.marketdata.persistence.model.CandleEntity;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг свечи domain ↔ persistence. Идентификатора у ряда нет — ключ
 * естественный (группа, открытие бара), и его половину проставляет
 * пишущая граница.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CandleMapper {

    CandleEntity domainToPersistence(Candle candle);

    Candle persistenceToDomain(CandleEntity entity);
}
