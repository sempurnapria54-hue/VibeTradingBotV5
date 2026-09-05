package com.example.connector.okx.mapping;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.connector.okx.snapshot.MarketPriceDataExternalSnapshot;
import com.example.connector.okx.integration.model.okx.response.TickerOkxResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг рыночной цены между слоями (docs/models/mapping/MarketPriceData.md):
 * integration DTO OKX ticker → граничный
 * {@link MarketPriceDataExternalSnapshot} (нормализованные цены/время),
 * затем → доменный {@link MarketPriceData} с добавлением внутреннего
 * instrumentId. Сырые decimal-строки → BigDecimal; epoch-millis-строка ts
 * → OffsetDateTime (UTC).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketPriceDataMapper {

    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalInstrumentType", source = "instType")
    @Mapping(target = "externalLastPrice", source = "last", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalAskPrice", source = "askPx", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalBidPrice", source = "bidPx", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalTimestamp", source = "ts", qualifiedByName = "toTimestamp")
    MarketPriceDataExternalSnapshot integrationToSnapshot(TickerOkxResponse response);

    MarketPriceData snapshotToDomain(MarketPriceDataExternalSnapshot snapshot, Long instrumentId);

    /**
     * Снапшот → доменная модель БЕЗ числового ключа связанной
     * сущности.
     *
     * <p><b>Коннектор его и не может заполнить:</b> числовые ключи баз
     * границу сервиса не пересекают
     * ({@code docs/architecture/data-ownership.md} §Идентификаторы), а
     * у коннектора базы нет вовсе. Связь проставляет владелец
     * сущности, получив модель. Соседний метод с ключом остаётся для
     * внутреннего употребления донора, пока тот жив.
     */
    MarketPriceData snapshotToDomain(MarketPriceDataExternalSnapshot snapshot);

    @Named("toBigDecimal")
    default BigDecimal toBigDecimal(String value) {
        return isBlank(value) ? null : new BigDecimal(value.trim());
    }

    @Named("toTimestamp")
    default OffsetDateTime toTimestamp(String epochMillis) {
        if (isBlank(epochMillis)) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(epochMillis.trim())), ZoneOffset.UTC);
    }
}
