package com.example.connector.okx.util;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.experimental.UtilityClass;

/**
 * Парсинг сырых OKX-строк (общий для мапперов и {@code OkxResponseConverter}):
 * empty→null, numeric→BigDecimal, epoch-ms→время UTC.
 */
@UtilityClass
public class OkxParse {

    /** OKX numeric строка → BigDecimal; empty→null. */
    public static BigDecimal decimal(String value) {
        return isBlank(value) ? null : new BigDecimal(value);
    }

    /** OKX epoch-ms строка → OffsetDateTime (UTC); empty→null. */
    public static OffsetDateTime offsetTime(String millis) {
        return isBlank(millis)
                ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(millis)), ZoneOffset.UTC);
    }

    /** OKX numeric строка → Long; empty→null. */
    public static Long epochMillis(String millis) {
        return isBlank(millis) ? null : Long.parseLong(millis);
    }

    /** OKX numeric строка → Integer; empty→null. */
    public static Integer integer(String value) {
        return isBlank(value) ? null : Integer.parseInt(value);
    }

    /** OKX epoch-ms строка → Instant; empty→null. */
    public static Instant instant(String millis) {
        return isBlank(millis) ? null : Instant.ofEpochMilli(Long.parseLong(millis));
    }
}
