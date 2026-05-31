package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.persistence.model.candle.CandleEntity;
import java.math.BigDecimal;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Маппинг свечи между слоями (docs/models/mapping/Candle.md):
 * client DTO OKX (позиционный массив, уже разобранный в
 * {@link CandleResponse}) → граничный {@link CandleExternalSnapshot},
 * затем → доменная {@link Candle}. Сырые строки OKX → BigDecimal/Long;
 * confirm — фильтр закрытых свечей, в домен не пишется.
 */
@Mapper(componentModel = "spring")
public interface CandleMapper {

    String OKX_CONFIRM_CLOSED = "1";

    @Mapping(target = "openTimestamp", source = "ts", qualifiedByName = "toLong")
    @Mapping(target = "open", source = "open", qualifiedByName = "toBigDecimal")
    @Mapping(target = "high", source = "high", qualifiedByName = "toBigDecimal")
    @Mapping(target = "low", source = "low", qualifiedByName = "toBigDecimal")
    @Mapping(target = "close", source = "close", qualifiedByName = "toBigDecimal")
    @Mapping(target = "volume", source = "volume", qualifiedByName = "toBigDecimal")
    @Mapping(target = "confirm", source = "confirm", qualifiedByName = "toConfirm")
    CandleExternalSnapshot clientToSnapshot(CandleResponse response);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "openTimestamp", source = "openTimestamp")
    @Mapping(target = "open", source = "open")
    @Mapping(target = "high", source = "high")
    @Mapping(target = "low", source = "low")
    @Mapping(target = "close", source = "close")
    @Mapping(target = "volume", source = "volume")
    Candle snapshotToDomain(CandleExternalSnapshot snapshot);

    CandleEntity domainToEntity(Candle candle);

    Candle entityToDomain(CandleEntity entity);

    @Named("toBigDecimal")
    default BigDecimal toBigDecimal(String value) {
        return StringUtils.isBlank(value) ? null : new BigDecimal(value);
    }

    @Named("toLong")
    default Long toLong(String value) {
        return StringUtils.isBlank(value) ? null : Long.valueOf(value);
    }

    @Named("toConfirm")
    default boolean toConfirm(String value) {
        return Objects.equals(OKX_CONFIRM_CLOSED, value);
    }
}
