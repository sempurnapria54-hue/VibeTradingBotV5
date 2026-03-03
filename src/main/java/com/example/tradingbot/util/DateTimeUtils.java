package com.example.tradingbot.util;

import jakarta.persistence.Transient;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@UtilityClass
public class DateTimeUtils {

    @Transient
    public OffsetDateTime toOffsetDateTime(Long utcMilliseconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(utcMilliseconds), ZoneOffset.UTC);
    }
}
