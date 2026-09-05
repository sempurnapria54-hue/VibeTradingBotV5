package com.example.marketdata.mapping;

import com.example.marketdata.persistence.model.OrderBookSnapshotEntity;
import com.example.marketdata.persistence.model.TickerSnapshotEntity;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг невосполнимых срезов domain ↔ persistence. Уровни книги
 * переносит {@link OrderBookLevelJsonConverter}: в строке они лежат
 * навесом.
 *
 * <p>Идентификатора у рядов нет — ключ естественный (инструмент, метка
 * времени площадки), и обе его половины уже лежат на модели.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OrderBookLevelJsonConverter.class)
public interface MarketSnapshotMapper {

    OrderBookSnapshotEntity domainToPersistence(MarketOrderBook orderBook);

    @Mapping(target = "id", ignore = true)
    MarketOrderBook persistenceToDomain(OrderBookSnapshotEntity entity);

    TickerSnapshotEntity domainToPersistence(MarketTicker ticker);

    @Mapping(target = "id", ignore = true)
    MarketTicker persistenceToDomain(TickerSnapshotEntity entity);
}
