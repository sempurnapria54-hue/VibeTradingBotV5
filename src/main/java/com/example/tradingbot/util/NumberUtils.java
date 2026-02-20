package com.example.tradingbot.util;

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
}
