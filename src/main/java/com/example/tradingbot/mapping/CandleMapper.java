package com.example.tradingbot.mapping;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.CandleResponse;
import com.example.tradingbot.persistence.model.candle.CandleEntity;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг свечи между слоями (docs/models/mapping/Candle.md):
 * integration DTO OKX (позиционный массив, уже разобранный в
 * {@link CandleResponse}) → граничный {@link CandleExternalSnapshot},
 * затем → доменная {@link Candle}. Сырые строки OKX → BigDecimal/Long;
 * confirm — фильтр закрытых свечей, в домен не пишется.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CandleMapper {

    @Mapping(target = "openTimestamp", source = "ts", qualifiedByName = "toLong")
    @Mapping(target = "open", source = "open", qualifiedByName = "toBigDecimal")
    @Mapping(target = "high", source = "high", qualifiedByName = "toBigDecimal")
    @Mapping(target = "low", source = "low", qualifiedByName = "toBigDecimal")
    @Mapping(target = "close", source = "close", qualifiedByName = "toBigDecimal")
    @Mapping(target = "volume", source = "volume", qualifiedByName = "toBigDecimal")
    @Mapping(target = "confirm", source = "confirm", qualifiedByName = "toConfirm")
    CandleExternalSnapshot integrationToSnapshot(CandleResponse response);

    Candle snapshotToDomain(CandleExternalSnapshot snapshot);

    CandleEntity domainToPersistence(Candle candle);

    @Named("toBigDecimal")
    default BigDecimal toBigDecimal(String value) {
        return isBlank(value) ? null : new BigDecimal(value);
    }

    @Named("toLong")
    default Long toLong(String value) {
        return isBlank(value) ? null : Long.valueOf(value);
    }

    @Named("toConfirm")
    default Boolean toConfirm(String value) {
        return Objects.equals(Constants.Okx.CONFIRM_CLOSED, value);
    }
}
