package com.example.tradingbot.mapping;

import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public interface CommonMapper {

    @Named("stringToBigDecimal")
    default BigDecimal stringToBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return new BigDecimal(value);
    }

    @Named("stringToInteger")
    default Integer stringToInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return new BigDecimal(value).intValue();
    }

    @Named("toOffsetDateTimeUtc")
    default OffsetDateTime toOffsetDateTimeUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        long epochMillis = Long.parseLong(value);
        Instant instant = Instant.ofEpochMilli(epochMillis);
        return instant.atOffset(ZoneOffset.UTC);
    }

}
