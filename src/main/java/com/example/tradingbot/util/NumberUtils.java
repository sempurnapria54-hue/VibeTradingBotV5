package com.example.tradingbot.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.experimental.UtilityClass;

import static org.apache.commons.lang3.StringUtils.isBlank;

@UtilityClass
public class NumberUtils {

    public static Long parseLongSafe(String source) {
        if (isBlank(source)) {
            return null;
        }
        return Long.parseLong(source);
    }

    public static OffsetDateTime parseOffsetDateTimeFromMillisSafe(String source) {
        Long millis = parseLongSafe(source);
        if (millis == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }
}
