package com.example.tradingbot.util;

import lombok.experimental.UtilityClass;

import java.util.Set;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

@UtilityClass
public class CollectionUtils {

    public static void checkContains(String value, String fieldName, Set<String> allowedValues) {
        if (isBlank(value) || isFalse(allowedValues.contains(value))) {
            throw new IllegalArgumentException(
                    fieldName + " has unsupported value: " + value + ". Allowed values: " + allowedValues
            );
        }
    }

}
