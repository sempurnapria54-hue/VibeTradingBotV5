package com.example.tradingbot.mapping;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.trade.market_price_data.external_snapshot.MarketPriceDataExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.OkxTickerResponse;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг рыночной цены между слоями (docs/models/mapping/MarketPriceData.md):
 * нативный тикер OKX → граничный {@link MarketPriceDataExternalSnapshot}.
 * Сырые строки OKX → BigDecimal (цены) / Long (ts, ms). Сборка доменного
 * MarketPriceData (внутренний instrumentId, MID_PRICE) — шаг 5, здесь не
 * выполняется.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketPriceDataMapper {

    @Mapping(target = "externalInstrumentType", source = "instType")
    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalLastPrice", source = "last", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalAskPrice", source = "askPx", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalBidPrice", source = "bidPx", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalTimestamp", source = "ts", qualifiedByName = "toLong")
    MarketPriceDataExternalSnapshot integrationToSnapshot(OkxTickerResponse response);

    @Named("toBigDecimal")
    default BigDecimal toBigDecimal(String value) {
        return isBlank(value) ? null : new BigDecimal(value);
    }

    @Named("toLong")
    default Long toLong(String value) {
        return isBlank(value) ? null : Long.valueOf(value);
    }
}
